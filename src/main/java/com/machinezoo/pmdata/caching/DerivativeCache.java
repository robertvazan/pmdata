// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;

public interface DerivativeCache<T> extends Supplier<T> {
	void touch();
	T compute();
	@Override
	default T get() {
		return DerivativeCaches.query(this);
	}
}
