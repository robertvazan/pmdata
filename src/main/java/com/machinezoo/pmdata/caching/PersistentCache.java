// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public interface PersistentCache<T extends CacheFile> {
	CacheFormat<T> format();
	void link();
	T supply();
	default CachePolicy policy() {
		return new CachePolicy();
	}
	default CacheState<T> cache() {
		return PersistentCaches.query(this);
	}
	default void touch() {
		cache().touch();
	}
	default T snapshot() {
		return cache().get();
	}
}
