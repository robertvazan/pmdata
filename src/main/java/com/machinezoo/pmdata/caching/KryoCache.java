// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.machinezoo.stagean.*;

@ApiIssue("Replace with record-based JSON-compatible serialization that does not store type information.")
public interface KryoCache<T> extends PersistentSource<T> {
	void link();
	T compute();
	default int version() {
		return 0;
	}
	default CachingOptions caching() {
		return CachingOptions.DEFAULT;
	}
	@Override
	default void touch() {
		new KryoCacheFile(this).touch();
	}
	@Override
	@SuppressWarnings("unchecked")
	default T get() {
		return (T)new KryoCacheValue(new KryoCacheFile(this).path()).get();
	}
}
