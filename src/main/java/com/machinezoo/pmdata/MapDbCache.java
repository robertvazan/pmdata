// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.apache.commons.lang3.tuple.*;
import org.mapdb.*;
import org.mapdb.Serializer;
import org.mapdb.volume.*;
import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;
import com.machinezoo.stagean.*;

@DraftApi
public class MapDbCache<T> {
	private static class MemoryVolume extends Volume {
		@Override
		public boolean isReadOnly() {
			return false;
		}
		@Override
		public boolean isSliced() {
			return false;
		}
		@Override
		public int sliceSize() {
			return -1;
		}
		@Override
		public File getFile() {
			return null;
		}
		@Override
		public boolean getFileLocked() {
			return false;
		}
		@Override
		public void sync() {
		}
		@Override
		public void close() {
		}
		byte[] array = new byte[1];
		int length;
		@Override
		public long length() {
			return length;
		}
		private int narrow(long offset) {
			if (offset > 1_000_000_000)
				throw new IndexOutOfBoundsException();
			return (int)offset;
		}
		@Override
		public void ensureAvailable(long loffset) {
			int offset = narrow(loffset);
			if (offset > length) {
				while (offset > array.length)
					array = Arrays.copyOf(array, 2 * array.length);
				length = offset;
			}
		}
		@Override
		public void truncate(long lsize) {
			int size = narrow(lsize);
			while (size > array.length)
				array = Arrays.copyOf(array, 2 * array.length);
			length = size;
		}
		private ByteBuffer buffer(long offset) {
			return ByteBuffer.wrap(array, 0, length).position(narrow(offset));
		}
		@Override
		public byte getByte(long offset) {
			return buffer(offset).get();
		}
		@Override
		public void getData(long offset, byte[] bytes, int bytesPos, int size) {
			buffer(offset).get(bytes, bytesPos, size);
		}
		@Override
		public DataInput2 getDataInput(long offset, int size) {
			return new DataInput2.ByteBuffer(buffer(0), narrow(offset));
		}
		@Override
		public int getInt(long offset) {
			return buffer(offset).getInt();
		}
		@Override
		public long getLong(long offset) {
			return buffer(offset).getLong();
		}
		@Override
		public void clear(long startOffset, long endOffset) {
			int start = narrow(startOffset);
			int end = narrow(endOffset);
			for (int i = start; i < end; ++i)
				array[i] = 0;
		}
		@Override
		public void putByte(long offset, byte value) {
			buffer(offset).put(value);
		}
		@Override
		public void putData(long offset, byte[] src, int srcPos, int srcSize) {
			buffer(offset).put(src, srcPos, srcSize);
		}
		@Override
		public void putData(long offset, ByteBuffer buf) {
			buffer(offset).put(buf);
		}
		@Override
		public void putInt(long offset, int value) {
			buffer(offset).putInt(value);
		}
		@Override
		public void putLong(long offset, long value) {
			buffer(offset).putLong(value);
		}
	}
	private final BlobCache cache;
	private final Function<DB, T> specialization;
	private final Consumer<T> consumer;
	private MapDbCache(CacheId id, Function<DB, T> specialization, Consumer<T> consumer) {
		cache = BlobCache.get(id, this::refresh);
		this.specialization = specialization;
		this.consumer = consumer;
	}
	private byte[] refresh() {
		var volume = new MemoryVolume();
		var db = DBMaker.volumeDB(volume, false).make();
		consumer.accept(specialization.apply(db));
		db.close();
		return Arrays.copyOf(volume.array, volume.length);
	}
	private Path path;
	private DB db;
	private T latest;
	private T get() {
		cache.access();
		Path path = cache.path();
		if (latest != null && this.path.equals(path))
			return latest;
		if (db != null) {
			Exceptions.log().run(db::close);
			db = null;
		}
		db = DBMaker.fileDB(path.toFile()).readOnly().fileMmapEnable().make();
		latest = specialization.apply(db);
		this.path = path;
		return latest;
	}
	private static final Map<CacheId, MapDbCache<?>> all = new ConcurrentHashMap<>();
	@SuppressWarnings("unchecked")
	private static <T> T get(CacheId id, Function<DB, T> specialization, Consumer<T> consumer) {
		return (T)all.computeIfAbsent(id, k -> new MapDbCache<>(id, specialization, consumer)).get();
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
	private static class KryoSerializer<T> implements Serializer<T> {
		@Override
		public void serialize(DataOutput2 out, T value) throws IOException {
			byte[] bytes = encode(value);
			out.writeInt(bytes.length);
			out.write(bytes);
		}
		@SuppressWarnings("unchecked")
		@Override
		public T deserialize(DataInput2 input, int available) throws IOException {
			var bytes = new byte[input.readInt()];
			input.readFully(bytes);
			return (T)decode(bytes);
		}
	}
	public static <K, V> Map<K, V> map(CacheId id, Supplier<Map<K, V>> supplier) {
		return get(id, db -> db.hashMap("map", new KryoSerializer<K>(), new KryoSerializer<V>()).createOrOpen(), table -> supplier.get().forEach(table::put));
	}
}
