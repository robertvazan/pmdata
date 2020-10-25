// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;
import com.google.common.cache.*;
import com.machinezoo.hookless.*;

public abstract class DerivativeCache<T> implements Supplier<T> {
	public abstract void touch();
	public abstract T compute();
	private static <T> ReactiveLazy<CacheDerivative<Object>> materialize(DerivativeCache<T> cache) {
		return new ReactiveLazy<>(() -> CacheDerivative.capture(cache::compute));
	}
	/*
	 * Like in ComputeCaches, just specialized for DerivativeCache.
	 */
	private static final LoadingCache<DerivativeCache<?>, ReactiveLazy<CacheDerivative<Object>>> all = CacheBuilder.newBuilder()
		.softValues()
		.build(CacheLoader.from(k -> materialize(k)));
	@Override
	@SuppressWarnings("unchecked")
	public T get() {
		return (T)all.getUnchecked(this).get().unpack();
	}
}
