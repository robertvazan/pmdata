// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.machinezoo.hookless.*;
import com.machinezoo.stagean.*;

@DraftApi("update() in addition to supply() that takes old CacheData as its parameter (may return the same)")
public interface PersistentCache<T extends CacheFile> {
	CacheFormat<T> format();
	void link();
	T supply();
	default CachePolicy policy() {
		return new CachePolicy();
	}
	/*
	 * This has side effects like get() but without returning cached value or throwing.
	 * It is intended to be used in link(). It might be faster than file().
	 */
	default void touch() {
		CacheInput.get().snapshot(this);
	}
	default T file() {
		var snapshot = CacheInput.get().snapshot(this);
		if (snapshot == null) {
			if (policy().blocking())
				CurrentReactiveScope.block();
			throw new EmptyCacheException();
		}
		return snapshot.get();
	}
}
