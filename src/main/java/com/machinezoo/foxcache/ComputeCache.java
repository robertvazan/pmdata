// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import java.util.function.*;

public interface ComputeCache<T> extends Supplier<T> {
	T compute();
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)CachedData.compute.getUnchecked(this).orElse(null);
	}
}
