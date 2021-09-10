// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public interface KryoCache<T> extends PersistentSource<T> {
	void link();
	T compute();
	default CachePolicy caching() {
		return new CachePolicy();
	}
	@Override
	default void touch() {
		new KryoCacheFile(this).touch();
	}
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)new KryoCacheValue(this).get();
	}
}
