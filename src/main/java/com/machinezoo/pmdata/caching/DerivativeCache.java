// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public interface DerivativeCache<T> extends PersistentSource<T> {
	T compute();
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)CachedData.derivative.getUnchecked(this).get().unpack();
	}
}
