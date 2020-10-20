// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.concurrent.*;

public class LazyCaches {
	private static final ConcurrentMap<LazyCache<?>, Object> all = new ConcurrentHashMap<>();
	@SuppressWarnings("unchecked")
	public static <T> T query(LazyCache<T> cache) {
		return (T)all.computeIfAbsent(cache, LazyCache::compute);
	}
}
