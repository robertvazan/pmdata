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
		if (this instanceof CanonicalPersistentSource) {
			var canonical = ((CanonicalPersistentSource<?>)this).canonicalize();
			if (!canonical.equals(this)) {
				canonical.touch();
				return;
			}
		}
		PersistentCache.super.touch();
	}
	@Override
	default Map<K, V> get() {
		if (this instanceof CanonicalPersistentSource) {
			@SuppressWarnings("unchecked") var canonical = ((CanonicalPersistentSource<Map<K, V>>)this).canonicalize();
			if (!canonical.equals(this))
				return canonical.get();
		}
		return getCache().map();
	}
}
