// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public interface DerivativeCache<T> extends PersistentSource<T> {
	void link();
	T compute();
	default int version() {
		return 0;
	}
	default void touch() {
		link();
		int version = version();
		if (version != 0) {
			/*
			 * This is inlined in dependencies of other caches. We therefore cannot use constant parameter name.
			 * We will use cache's toString() to ensure every derivative cache has its own version dependency.
			 */
			CacheInput.get().parameter(toString(), version);
		}
	}
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)CachedData.derivative.getUnchecked(this).get().unpack();
	}
}
