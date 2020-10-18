// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public interface PersistentCacheQuery<T extends CacheData> {
	CacheFormat<T> format();
	void link();
	T supply();
	default CachePolicy policy() {
		return new CachePolicy();
	}
	default PersistentCache<T> cache() {
		return CachePool.persist(this);
	}
	default void touch() {
		cache().touch();
	}
	default T snapshot() {
		return cache().get();
	}
}
