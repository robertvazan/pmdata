// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;

public abstract class KryoCache<T> extends PersistentCache<KryoFile<T>> implements Supplier<T> {
	public abstract T compute();
	@Override
	public CacheFormat<KryoFile<T>> format() {
		return KryoFile.format();
	}
	@Override
	public KryoFile<T> supply() {
		return KryoFile.of(compute());
	}
	@Override
	public T get() {
		return file().read();
	}
}
