// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;
import java.util.concurrent.*;
import com.google.common.cache.*;
import com.machinezoo.hookless.*;

class CachedData {
	static final ConcurrentMap<LazyCache<?>, Object> lazy = new ConcurrentHashMap<>();
	/*
	 * Soft-valued cache may cause extremely inefficient GC behavior:
	 * https://bugs.openjdk.java.net/browse/JDK-6912889
	 * 
	 * It is however very simple and it will use all RAM that is allocated to Java process,
	 * which is usually some fraction of physical RAM.
	 * This cache can be tuned indirectly with -Xmx and -XX:SoftRefLRUPolicyMSPerMB.
	 * 
	 * Cached value is wrapped in Optional, because Guava cache does not tolerate null values.
	 */
	static final LoadingCache<ComputeCache<?>, Optional<?>> compute = CacheBuilder.newBuilder()
		.softValues()
		.build(CacheLoader.from(k -> Optional.ofNullable(k.compute())));
	/*
	 * Like in ComputeCaches, just specialized for DerivativeCache.
	 */
	static final LoadingCache<DerivativeCache<?>, ReactiveLazy<CacheDerivative<Object>>> derivative = CacheBuilder.newBuilder()
		.softValues()
		.build(CacheLoader.from(k -> materialize(k)));
	private static <T> ReactiveLazy<CacheDerivative<Object>> materialize(DerivativeCache<T> cache) {
		return new ReactiveLazy<>(() -> CacheDerivative.capture(() -> {
			/*
			 * Touch the cache just in case there are extra dependencies there.
			 */
			cache.touch();
			int version = cache.version();
			if (version != 0) {
				/*
				 * This is inlined in dependencies of other caches. We therefore cannot use constant parameter name.
				 * We will use cache's toString() to ensure every derivative cache has its own version dependency.
				 */
				CacheInput.get().parameter(cache.toString(), version);
			}
			return cache.compute();
		}));
	}
}
