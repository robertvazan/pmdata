// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public interface KryoCache<T> extends PersistentSource<T> {
	void link();
	T compute();
	default CachingOptions caching() {
		return CachingOptions.DEFAULT;
	}
	@Override
	default void touch() {
		new KryoCacheFile(this).touch();
	}
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)new KryoCacheValue(new KryoCacheFile(this).path()).get();
	}
}
