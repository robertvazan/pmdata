// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import static java.util.stream.Collectors.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.util.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.stagean.*;

/*
 * This should really be a thin wrapper around MapDB, which is faster and handles more data.
 * The only downside of MapDB is that it requires dedicated UI to explore the database.
 * 
 * Research pages sometimes need to edit some data, usually annotations.
 * Edits of this data are frequent and small, so remote storage is inefficient.
 * It would also cause blocking that interferes with proper function of BLOB cache.
 * We will therefore implement simple local storage in a JSON file.
 * Map serves as a very general and yet very usable abstraction here.
 * 
 * This annotation storage is temporary on local disk, likely not backed up.
 * Once annotation process is complete, annotations have to be exported to long-term storage.
 * Such export likely involves packing and sorting the data, optimizing it for efficient reading.
 */
@DraftApi
@DraftCode("kryo serialization")
public class AnnotationMap<K, V> implements Map<K, V> {
	/*
	 * The simplest and the most performant solution is to hold the map in RAM all the time.
	 * Since this class is used for temporary annotations-in-progress, the amount of consumed RAM is limited.
	 */
	private final Map<K, V> memory = new HashMap<>();
	/*
	 * Deserialization requires Class references.
	 */
	private final Class<K> keyType;
	private final Class<V> valueType;
	/*
	 * Every annotation file must have a filename as there is no way to automate naming.
	 * Filename is usually derived from page's class name or URL path component.
	 */
	private final String filename;
	private AnnotationMap(Class<K> keyType, Class<V> valueType, String filename) {
		this.keyType = keyType;
		this.valueType = valueType;
		this.filename = filename;
	}
	/*
	 * Reference to outer Collections.synchronizedMap(). See below.
	 */
	private Object mutex;
	/*
	 * It is easy to create clashes between filenames, so we namespace everything under owner's class name.
	 * We still need local name (within the class), but that one is easier to make unique.
	 * We aren't using fully qualified class name, because simple class name is unique enough.
	 */
	public static <K, V> Map<K, V> create(Class<?> owner, String name, Class<K> keyType, Class<V> valueType) {
		var persisted = new AnnotationMap<>(keyType, valueType, owner.getSimpleName() + "-" + name);
		/*
		 * Loading the map upon instantiation is the simplest solution.
		 * Since annotations are small and temporary, this will be fast enough.
		 */
		persisted.load();
		/*
		 * We make the map reactive, so that UI code reacts immediately to changes in the data.
		 */
		var reactive = ReactiveCollections.map(persisted);
		/*
		 * We will make it synchronized, because annotation map instances are always global.
		 * Collections.synchronizedMap() is used for synchronization instead of synchronized methods on this class,
		 * because Collections.synchronizedMap() also synchronizes keySet() and other methods returning collections.
		 * It it important to add synchronization as the outer level, so that calling code can synchronized explicitly.
		 * This will also ensure that save() is never called concurrently.
		 */
		var locked = Collections.synchronizedMap(reactive);
		/*
		 * Async saving code will have to do synchronization in separate thread.
		 * The only way to do it while still using Collections.synchronizedMap()
		 * is to tell the annotation map object about the enveloping synchronized map object.
		 */
		persisted.mutex = locked;
		return locked;
	}
	/*
	 * Use data directory instead of cache directory to avoid having annotations carelessly deleted.
	 */
	private static final Path directory = SiteFiles.dataOf(AnnotationMap.class.getSimpleName());
	private Path path() {
		return directory.resolve(filename + ".json");
	}
	/*
	 * We will save the map in JSON format as an array of entry objects containing key and value.
	 */
	@SuppressWarnings("unused")
	private static class JsonEntry<K, V> {
		K key;
		V value;
		JsonEntry(K key, V value) {
			this.key = key;
			this.value = value;
		}
	}
	private void load() {
		/*
		 * Missing file means empty map.
		 */
		try {
			if (Files.exists(path())) {
				String json = Files.readString(path());
				/*
				 * Deserialization is tricky due to generic type parameters.
				 * Gson provides the TypeToken trick, but it only works outside of this class
				 * when concrete types for type parameters are still known.
				 * Gson would take Type instead of Class and we could supply ParameterizedType,
				 * but Java unfortunately doesn't offer any way to construct arbitrary ParameterizedType.
				 * We will therefore do part of the parsing dynamically and part with types.
				 */
				var gson = new Gson();
				var array = JsonParser.parseString(json).getAsJsonArray();
				for (var item : array) {
					var entry = item.getAsJsonObject();
					K key = gson.fromJson(entry.get("key"), keyType);
					V value = gson.fromJson(entry.get("value"), valueType);
					memory.put(key, value);
				}
			}
		} catch (Throwable ex) {
			/*
			 * Tolerate reading errors. We don't want to crash everything
			 * just because either the key or the value class changed and deserialization fails now.
			 */
			Exceptions.log().handle(ex);
			memory.clear();
		}
	}
	private void save() {
		/*
		 * Don't break calling code when saving fails. Just report it in logs.
		 */
		Exceptions.log().run(Exceptions.sneak().runnable(() -> {
			/*
			 * This is called from public write methods. Don't capture unnecessary dependency.
			 */
			try (var ignored = ReactiveScope.ignore()) {
				/*
				 * Deleting the file when the map is empty ensures that the data directory will be reasonably clean.
				 * We will be still leaving behind unused legacy files, which will be discarded only during computer upgrades.
				 */
				if (memory.isEmpty())
					Files.deleteIfExists(path());
				else {
					var list = memory.entrySet().stream()
						.map(e -> new JsonEntry<>(e.getKey(), e.getValue()))
						.collect(toList());
					String json = new GsonBuilder()
						.setPrettyPrinting()
						.disableHtmlEscaping()
						.create()
						.toJson(list);
					Files.writeString(path(), json);
				}
			}
		}));
	}
	/*
	 * We have to rate-limit file saving, because some edit operations could be coming at 60fps.
	 */
	private boolean scheduled;
	private void schedule() {
		if (!scheduled) {
			scheduled = true;
			SiteThread.timer().schedule(() -> {
				/*
				 * Timer thread can only handle lightweight tasks.
				 * Our serialization might take time, so forward it to the low-priority bulk thread pool.
				 */
				SiteThread.bulk().submit(() -> {
					/*
					 * Synchronize on the same object as write methods.
					 */
					synchronized (mutex) {
						scheduled = false;
						save();
					}
				});
				/*
				 * 100ms is short enough to prevent data loss when the app unexpectedly exits.
				 * It is long enough to limit performance impact of persistency.
				 */
			}, 100, TimeUnit.MILLISECONDS);
		}
	}
	@Override
	public void clear() {
		memory.clear();
		schedule();
	}
	@Override
	public boolean containsKey(Object key) {
		return memory.containsKey(key);
	}
	@Override
	public boolean containsValue(Object value) {
		return memory.containsValue(value);
	}
	@Override
	public Set<Entry<K, V>> entrySet() {
		return memory.entrySet();
	}
	@Override
	public boolean equals(Object obj) {
		return memory.equals(obj);
	}
	@Override
	public V get(Object key) {
		return memory.get(key);
	}
	@Override
	public int hashCode() {
		return memory.hashCode();
	}
	@Override
	public boolean isEmpty() {
		return memory.isEmpty();
	}
	@Override
	public Set<K> keySet() {
		return memory.keySet();
	}
	@Override
	public V put(K key, V value) {
		V previous = memory.put(key, value);
		schedule();
		return previous;
	}
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		memory.putAll(m);
		schedule();
	}
	@Override
	public V remove(Object key) {
		V removed = remove(key);
		schedule();
		return removed;
	}
	@Override
	public int size() {
		return memory.size();
	}
	@Override
	public Collection<V> values() {
		return memory.values();
	}
}
