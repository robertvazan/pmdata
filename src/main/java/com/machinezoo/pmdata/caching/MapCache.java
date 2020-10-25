// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;
import java.util.function.*;

public abstract class MapCache<K, V> extends PersistentCache<MapFile<K, V>> implements Supplier<Map<K, V>> {
	public abstract MapFile<K, V> compute();
	@Override
	public CacheFormat<MapFile<K, V>> format() {
		return MapFile.format();
	}
	@Override
	public MapFile<K, V> supply() {
		return compute();
	}
	@Override
	public Map<K, V> get() {
		return file().map();
	}
	public V get(K key) {
		return file().get(key);
	}
}
