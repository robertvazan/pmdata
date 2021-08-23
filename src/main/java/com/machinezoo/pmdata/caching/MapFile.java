// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.mapdb.*;
import com.machinezoo.noexception.*;

public class MapFile<K, V> implements CacheFile {
	private final MapDbFile file;
	private final long capacity;
	private MapFile(MapDbFile file, long capacity) {
		this.file = file;
		this.capacity = capacity;
	}
	public MapFile(Path path, long capacity) {
		this(new MapDbFile(path), capacity);
	}
	public MapFile(long capacity) {
		this(new MapDbFile(), capacity);
	}
	public static <K, V> CacheFormat<MapFile<K, V>> format() {
		return new CacheFormat<>() {
			@Override
			public MapFile<K, V> load(Path path) {
				return new MapFile<>(MapDbFile.format().load(path), -1);
			}
		};
	}
	@Override
	public Path path() {
		return file.path();
	}
	@Override
	public boolean readonly() {
		return file.readonly();
	}
	private HTreeMap<K, V> map;
	private boolean cached;
	private void initialize() {
		if (map == null) {
			if (file.readonly()) {
				int keySize = file.db().atomicInteger("key-size").open().get();
				int valueSize = file.db().atomicInteger("value-size").open().get();
				/*
				 * Sum of key and value size should be larger than overhead in the compute cache,
				 * which is roughly estimated here to be several objects or 100 bytes.
				 */
				cached = keySize + valueSize > 100;
			}
			map = file.map("map", capacity);
		}
	}
	public void put(K key, V value) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(value);
		initialize();
		map.put(key, value);
	}
	private static <T> ToIntFunction<T> measure(Serializer<T> serializer) {
		return o -> {
			var output = new DataOutput2();
			Exceptions.wrap().run(() -> serializer.serialize(output, o));
			return output.pos;
		};
	}
	@Override
	public synchronized void commit() {
		if (!file.readonly()) {
			initialize();
			var keyMetric = measure(map.getKeySerializer());
			var valueMetric = measure(map.getValueSerializer());
			var keyStats = new IntSummaryStatistics();
			var valueStats = new IntSummaryStatistics();
			map.forEach((k, v) -> {
				keyStats.accept(keyMetric.applyAsInt(k));
				valueStats.accept(valueMetric.applyAsInt(v));
			});
			file.db().atomicInteger("key-size").create().set((int)keyStats.getAverage());
			file.db().atomicInteger("value-size").create().set((int)valueStats.getAverage());
			map = null;
			file.commit();
		}
	}
	private static class ParsedEntry<V> implements ComputeCache<V> {
		final MapFile<?, V> file;
		final Object key;
		ParsedEntry(MapFile<?, V> file, Object key) {
			this.file = file;
			this.key = key;
		}
		@Override
		public V compute() {
			return file.map.get(key);
		}
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ParsedEntry))
				return false;
			var other = (ParsedEntry<?>)obj;
			return file == other.file && key.equals(other.key);
		}
		@Override
		public int hashCode() {
			return file.hashCode() + key.hashCode();
		}
	}
	/*
	 * Optimization. Default hashCode() is slow.
	 */
	private final int hashCode = ThreadLocalRandom.current().nextInt();
	@Override
	public int hashCode() {
		return hashCode;
	}
	public synchronized V get(Object key) {
		Objects.requireNonNull(key);
		initialize();
		if (cached)
			return new ParsedEntry<>(this, key).get();
		else
			return map.get(key);
	}
	public Map<K, V> map() {
		initialize();
		return new Map<>() {
			@Override
			public void clear() {
				map.clear();
			}
			@Override
			public boolean containsKey(Object key) {
				return map.containsKey(key);
			}
			@Override
			public boolean containsValue(Object value) {
				throw new UnsupportedOperationException();
			}
			@Override
			public Set<Entry<K, V>> entrySet() {
				return map.entrySet();
			}
			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				if (!(obj instanceof Map))
					return false;
				Map<?, ?> other = (Map<?, ?>)obj;
				if (size() != other.size())
					return false;
				for (var entry : entrySet())
					if (!other.containsKey(entry.getKey()) || !Objects.equals(entry.getValue(), other.get(entry.getKey())))
						return false;
				for (var key : other.keySet())
					if (!containsKey(key))
						return false;
				return true;
			}
			@Override
			public V get(Object key) {
				return MapFile.this.get(key);
			}
			@Override
			public int hashCode() {
				throw new UnsupportedOperationException();
			}
			@Override
			public boolean isEmpty() {
				return map.isEmpty();
			}
			@Override
			public Set<K> keySet() {
				return map.keySet();
			}
			@Override
			public V put(K key, V value) {
				return map.put(key, value);
			}
			@Override
			public void putAll(Map<? extends K, ? extends V> m) {
				map.putAll(m);
			}
			@Override
			public V remove(Object key) {
				return map.remove(key);
			}
			@Override
			public int size() {
				return map.size();
			}
			@Override
			public Collection<V> values() {
				return map.values();
			}
		};
	}
	public static <K, V> MapFile<K, V> of(Map<K, V> map) {
		var file = new MapFile<K, V>(map.size());
		map.forEach(file::put);
		return file;
	}
	public static <K, V> MapFile<K, V> of(Collection<K> keys, Function<K, V> function) {
		var file = new MapFile<K, V>(keys.size());
		for (var key : Progress.get().loop(keys))
			file.put(key, function.apply(key));
		return file;
	}
	public static <K, V> MapFile<K, V> of(K[] keys, Function<K, V> function) {
		var file = new MapFile<K, V>(keys.length);
		for (var key : Progress.get().loop(keys))
			file.put(key, function.apply(key));
		return file;
	}
}
