// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import java.util.function.*;

public interface LazyCache<T>extends Supplier<T> {
	T compute();
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)CachedData.lazy.computeIfAbsent(this, LazyCache::compute);
	}
}
