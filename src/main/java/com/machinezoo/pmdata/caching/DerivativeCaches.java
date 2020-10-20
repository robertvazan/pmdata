// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.google.common.cache.*;
import com.machinezoo.hookless.*;

public class DerivativeCaches {
	private static <T> ReactiveLazy<CacheDerivative<Object>> materialize(DerivativeCache<T> cache) {
		return new ReactiveLazy<>(() -> CacheDerivative.capture(cache::compute));
	}
	/*
	 * Like in ComputeCaches, just specialized for DerivativeCache.
	 */
	private static final LoadingCache<DerivativeCache<?>, ReactiveLazy<CacheDerivative<Object>>> all = CacheBuilder.newBuilder()
		.softValues()
		.build(CacheLoader.from(k -> materialize(k)));
	@SuppressWarnings("unchecked")
	public static <T> T query(DerivativeCache<T> cache) {
		return (T)all.getUnchecked(cache).get().unpack();
	}
}
