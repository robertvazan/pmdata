// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;

public interface ComputeCache<T> extends Supplier<T> {
	T compute();
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)CachedData.compute.getUnchecked(this).orElse(null);
	}
}
