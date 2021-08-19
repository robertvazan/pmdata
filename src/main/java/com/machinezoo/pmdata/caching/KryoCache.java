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
	default T get() {
		if (this instanceof CanonicalPersistentSource) {
			@SuppressWarnings("unchecked") var canonical = ((CanonicalPersistentSource<T>)this).canonicalize();
			if (!canonical.equals(this))
				return canonical.get();
		}
		return getCache().read();
	}
}
