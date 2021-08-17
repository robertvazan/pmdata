// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;

public interface LazyCache<T>extends Supplier<T> {
	T compute();
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)CachedData.lazy.computeIfAbsent(this, LazyCache::compute);
	}
}
