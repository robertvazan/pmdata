// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.concurrent.*;
import java.util.function.*;

public abstract class LazyCache<T> implements Supplier<T> {
	public abstract T compute();
	private static final ConcurrentMap<LazyCache<?>, Object> all = new ConcurrentHashMap<>();
	@Override
	@SuppressWarnings("unchecked")
	public T get() {
		return (T)all.computeIfAbsent(this, LazyCache::compute);
	}
}
