// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;
import java.util.function.*;

public interface MapCache<K, V> extends PersistentCache<MapFile<K, V>>, Supplier<Map<K, V>> {
	MapFile<K, V> compute();
	@Override
	default CacheFormat<MapFile<K, V>> format() {
		return MapFile.format();
	}
	@Override
	default MapFile<K, V> supply() {
		return compute();
	}
	@Override
	default Map<K, V> get() {
		return snapshot().map();
	}
	default V get(K key) {
		return snapshot().get(key);
	}
}
