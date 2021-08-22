// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;

public interface KryoCache<T> extends PersistentCache<KryoFile<T>>, Supplier<T>, PersistentSource<T> {
	T compute();
	@Override
	default CacheFormat<KryoFile<T>> cacheFormat() {
		return KryoFile.format();
	}
	@Override
	default KryoFile<T> computeCache() {
		return KryoFile.of(compute());
	}
	@Override
	default void touch() {
		PersistentCache.super.touch();
	}
	@Override
	default T get() {
		return getCache().read();
	}
}
