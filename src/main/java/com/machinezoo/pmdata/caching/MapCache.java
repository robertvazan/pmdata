// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;
import java.util.function.*;

public interface MapCache<K, V> extends PersistentCache<MapFile<K, V>>, Supplier<Map<K, V>>, PersistentMapSource<K, V> {
	MapFile<K, V> compute();
	@Override
	default CacheFormat<MapFile<K, V>> cacheFormat() {
		return MapFile.format();
	}
	@Override
	default MapFile<K, V> computeCache() {
		return compute();
	}
	@Override
	default void touch() {
		PersistentCache.super.touch();
	}
	@Override
	default Map<K, V> get() {
		return getCache().map();
	}
}
