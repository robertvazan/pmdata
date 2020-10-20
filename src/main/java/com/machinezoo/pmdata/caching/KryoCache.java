// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;

public interface KryoCache<T> extends PersistentCache<KryoFile<T>>, Supplier<T> {
	T compute();
	@Override
	default CacheFormat<KryoFile<T>> format() {
		return KryoFile.format();
	}
	@Override
	default KryoFile<T> supply() {
		return KryoFile.of(compute());
	}
	@Override
	default T get() {
		return snapshot().read();
	}
}
