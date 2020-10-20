// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;

public interface ComputeCache<T> extends Supplier<T> {
	T compute();
	@Override
	default T get() {
		return ComputeCaches.query(this);
	}
}
