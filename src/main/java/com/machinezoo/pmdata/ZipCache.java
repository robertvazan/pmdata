// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import static java.util.stream.Collectors.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;
import org.apache.commons.io.*;
import org.apache.commons.lang3.tuple.*;
import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;
import com.machinezoo.stagean.*;

/*
 * BLOB cache is hard to use on its own. One highly reusable specialization is a ZIP file.
 * ZipFile lets us read on-disk ZIP files without loading them into memory.
 * We can expose ZIP files in many ways, but Map seems to be the easiest to use while remaining general.
 */
@DraftApi("replace ZIPs completely with MapDB")
public class ZipCache<K, V> implements MapCache<K, V> {
	private final BlobCache cache;
	/*
	 * Deserialization is somewhat computationally expensive. We will use freeze cache to speed it up.
	 * This will also limit the number of deserialized instances circulating around.
	 * Every ZIP file gets its own independent freeze cache, because it contains distinct data.
	 * We will however cache only the default (2) number of deserialized files to limit RAM consumption.
	 * Single ZIP could be used concurrently by several reactive computations,
	 * but freeze cache already handles this by maintaining separate cache for every computation.
	 */
	private final FreezeCache freezes;
	/*
	 * We are taking a supplier as a parameter just like BLOB cache,
	 * but this could be extended to Consumer<Map<K, V>> in the future
	 * in order to support large caches (using on-disk value buffer).
	 */
	private final Supplier<Map<K, V>> supplier;
	/*
	 * We will keep the constructor private to prevent stupid coding errors like failing to mark class field static.
	 * Method map/bimap() provides nicer and more concise API than the constructor anyway.
	 */
	private ZipCache(CacheId id, Supplier<Map<K, V>> supplier) {
		this.supplier = supplier;
		cache = BlobCache.get(id, this::refresh);
		freezes = FreezeCache.of(id.then(ZipCache.class));
	}
	private static final Map<CacheId, ZipCache<?, ?>> all = new HashMap<>();
	@SuppressWarnings("unchecked")
	public static synchronized <K, V> MapCache<K, V> map(CacheId id, Supplier<Map<K, V>> supplier) {
		ZipCache<?, ?> cache = all.get(id);
		if (cache == null)
			all.put(id, cache = new ZipCache<>(id, supplier));
		/*
		 * Unavoidable unchecked cast, but the way this class is typically used makes it safe.
		 */
		return (ZipCache<K, V>)cache;
	}
	/*
	 * Tuple-based overload for multiple keys.
	 */
	public static <K1, K2, V> BiMapCache<K1, K2, V> bimap(CacheId id, Supplier<Map<Pair<K1, K2>, V>> supplier) {
		var map = map(id, supplier);
		return (k1, k2) -> map.get(Pair.of(k1, k2));
	}
	/*
	 * Kryo serialization like in BlobCache.
	 */
	private static final ThreadLocal<Kryo> kryos = ThreadLocal.withInitial(() -> {
		Kryo kryo = new Kryo();
		kryo.setRegistrationRequired(false);
		kryo.addDefaultSerializer(Pair.class, CacheSerializers.PairSerializer.class);
		return kryo;
	});
	private static byte[] encode(Object object) {
		Output output = new Output(64, -1);
		kryos.get().writeClassAndObject(output, object);
		return output.toBytes();
	}
	private static Object decode(byte[] bytes) {
		Input input = new Input(bytes);
		return kryos.get().readClassAndObject(input);
	}
	/*
	 * Filenames are simply base64-encoded (URL-safe) serialized keys.
	 */
	private static String filename(Object key) {
		return Base64.getUrlEncoder().encodeToString(encode(key));
	}
	private byte[] refresh() {
		/*
		 * To make the ZIP file content reproducible, map entries must be sorted by key.
		 * Since the key is not guaranteed to be comparable, we will sort by filename instead.
		 */
		List<Pair<String, V>> sorted = supplier.get()
			.entrySet().stream()
			.map(e -> Pair.of(filename(e.getKey()), e.getValue()))
			.sorted(Comparator.comparing(Pair::getKey))
			.collect(toList());
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Exceptions.sneak().run(() -> {
			try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
				for (var pair : sorted) {
					ZipEntry entry = new ZipEntry(pair.getKey());
					/*
					 * Set some constant modification time to ensure the ZIP file is reproducible.
					 * If we don't set it, ZipOutputStream sets it to current time, which breaks reproducibility.
					 */
					entry.setLastModifiedTime(FileTime.fromMillis(0));
					zip.putNextEntry(entry);
					zip.write(encode(pair.getValue()));
				}
			}
		});
		return buffer.toByteArray();
	}
	/*
	 * We will cache open ZipFile forever, at least until cache content changes.
	 * This is essential for performance, because opening a ZipFile involves parsing the whole entry table.
	 */
	private Path lastPath;
	private ZipFile lastZip;
	/*
	 * ZipFile operations might or might not be thread-safe, so let's synchronize everything.
	 * This method additionally needs synchronization to access last* fields.
	 * It is not itself synchronized, because synchronization is done on all public methods of this class.
	 */
	private ZipFile zip() {
		/*
		 * Record cache access even if we return already opened ZIP file.
		 */
		cache.access();
		/*
		 * If the cache is not ready, access() method above will throw.
		 * If it doesn't, we will throw NullPointerException due to null path.
		 * 
		 * Always request new path in order to immediately reflect cache changes.
		 */
		Path path = cache.path();
		if (lastZip != null && path.equals(lastPath))
			return lastZip;
		if (lastZip != null) {
			/*
			 * Closing a file should never throw since we aren't writing anything into it.
			 */
			Exceptions.log().run(Exceptions.sneak().runnable(lastZip::close));
			/*
			 * In case ZipFile constructor below throws, we want to leave this object in consistent state.
			 */
			lastZip = null;
			lastPath = null;
		}
		/*
		 * BLOB cache pins all files returned via path(),
		 * but let's be cautious and fill last* fields only after we successfully open the ZIP file.
		 */
		ZipFile zip = Exceptions.sneak().get(() -> new ZipFile(path.toFile()));
		lastPath = path;
		lastZip = zip;
		return zip;
	}
	/*
	 * We can now define map lookup. This is the most frequently needed operation on the map.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public synchronized V get(K key) {
		/*
		 * Using a key with freeze cache here requires the key to implement equals() and hashCode(),
		 * but that requirement is already satisfied, because it is used as map key by supplier.
		 */
		return freezes.get(key, Exceptions.sneak().supplier(() -> {
			ZipFile zip = zip();
			ZipEntry entry = zip.getEntry(filename(key));
			if (entry == null)
				return null;
			/*
			 * Technically, this can result in an IOException, perhaps when network drive is disconnected,
			 * but cache is on local disk that never fails in practice.
			 * Zip files are also written in such a way that the file is very unlikely to be damaged.
			 */
			try (InputStream stream = zip.getInputStream(entry)) {
				/*
				 * Unavoidable unchecked cast, but the way this class is typically used makes it safe.
				 */
				return (V)decode(IOUtils.toByteArray(stream));
			}
		}));
	}
}
