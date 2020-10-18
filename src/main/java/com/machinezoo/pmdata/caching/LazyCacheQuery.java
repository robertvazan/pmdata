// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;

public interface LazyCacheQuery<T> extends Supplier<T> {
	T compute();
	@Override
	default T get() {
		return CachePool.lazy(this);
	}
}
