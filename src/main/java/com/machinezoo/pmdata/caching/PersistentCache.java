// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.machinezoo.hookless.*;
import com.machinezoo.stagean.*;

@DraftApi("update() in addition to supply() that takes old CacheData as its parameter (may return the same)")
public abstract class PersistentCache<T extends CacheFile> {
	public abstract CacheFormat<T> format();
	public abstract void link();
	public abstract T supply();
	public CachePolicy policy() {
		return new CachePolicy();
	}
	/*
	 * This has side effects like get() but without returning cached value or throwing.
	 * It is intended to be used in link(). It might be faster than file().
	 */
	public void touch() {
		CacheInput.get().snapshot(this);
	}
	public T file() {
		var snapshot = CacheInput.get().snapshot(this);
		if (snapshot == null) {
			if (policy().blocking())
				CurrentReactiveScope.block();
			throw new EmptyCacheException();
		}
		return snapshot.get();
	}
}
