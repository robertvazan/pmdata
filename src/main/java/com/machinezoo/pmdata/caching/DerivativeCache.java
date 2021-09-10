// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public interface DerivativeCache<T> extends PersistentSource<T> {
	T compute();
	default int version() {
		return 0;
	}
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)CachedData.derivative.getUnchecked(this).get().unpack();
	}
}
