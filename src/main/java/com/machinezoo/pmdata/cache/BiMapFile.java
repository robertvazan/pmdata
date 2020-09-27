// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.cache;

import java.nio.file.*;
import java.util.*;
import org.apache.commons.lang3.tuple.*;

/*
 * This is a thin wrapper around MapFile, which involves rather inefficient key serialization.
 * Custom pair serializer might improve performance in the future.
 */
public class BiMapFile<K1, K2, V> implements CacheData {
	private final MapFile<ImmutablePair<K1, K2>, V> file;
	private BiMapFile(MapFile<ImmutablePair<K1, K2>, V> file) {
		this.file = file;
	}
	public BiMapFile(Path path, Class<K1> keyType1, Class<K2> keyType2, Class<V> valueType, long capacity) {
		this(new MapFile<>(path, null, valueType, capacity));
	}
	public BiMapFile(Class<K1> keyType1, Class<K2> keyType2, Class<V> valueType, long capacity) {
		this(new MapFile<>(null, valueType, capacity));
	}
	public static <K1, K2, V> CacheFormat<BiMapFile<K1, K2, V>> format(Class<K1> keyType1, Class<K2> keyType2, Class<V> valueType) {
		return new CacheFormat<>() {
			@Override
			public BiMapFile<K1, K2, V> load(Path path) {
				return new BiMapFile<>(MapFile.<ImmutablePair<K1, K2>, V>format(null, valueType).load(path));
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
	@Override
	public void commit() {
		file.commit();
	}
	public void put(K1 key1, K2 key2, V value) {
		file.put(ImmutablePair.of(key1, key2), value);
	}
	public V get(K1 key1, K2 key2) {
		return file.get(ImmutablePair.of(key1, key2));
	}
	public Map<ImmutablePair<K1, K2>, V> map() {
		return file.map();
	}
}
