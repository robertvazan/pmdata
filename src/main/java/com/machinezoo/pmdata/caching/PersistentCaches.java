// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.concurrent.*;

public class PersistentCaches {
	private static final ConcurrentMap<PersistentCache<?>, CacheState<?>> all = new ConcurrentHashMap<>();
	@SuppressWarnings("unchecked")
	static <T extends CacheFile> CacheState<T> query(PersistentCache<T> cache) {
		return (CacheState<T>)all.computeIfAbsent(cache, key -> new CacheState<>(cache.format())
			.id(cache)
			.policy(cache.policy())
			.link(cache::link)
			.supply(cache::supply));
	}
}
