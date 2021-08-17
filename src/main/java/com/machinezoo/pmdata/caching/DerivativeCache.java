// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public interface DerivativeCache<T> extends PersistentSource<T> {
	T compute();
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		if (this instanceof CanonicalPersistentSource) {
			var canonical = ((CanonicalPersistentSource<T>)this).canonicalize();
			if (!canonical.equals(this))
				return canonical.get();
		}
		return (T)CachedData.derivative.getUnchecked(this).get().unpack();
	}
}
