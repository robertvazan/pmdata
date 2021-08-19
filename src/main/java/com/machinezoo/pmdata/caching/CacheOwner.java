// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.concurrent.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.util.*;

/*
 * Central hub for all the cache-specific objects.
 */
class CacheOwner<T extends CacheFile> {
	final PersistentCache<T> cache;
	/*
	 * Policy copy to prevent accidental changes by application code.
	 */
	final CachePolicy policy;
	final ReactiveVariable<CacheSnapshot<T>> snapshot;
	final CacheWorker<T> worker;
	/*
	 * CacheInput as captured by this worker has several flaws:
	 * - It can be out of date.
	 * - It could reactively block.
	 * - Exception might be thrown instead of returning input description.
	 * 
	 * These are all legitimate results. Callers have to deal with them.
	 */
	final ReactiveWorker<CacheInput> input;
	final ReactiveWorker<Boolean> stability;
	CacheOwner(PersistentCache<T> cache) {
		this.cache = cache;
		OwnerTrace.of(this).tag("cache", cache);
		policy = cache.cachePolicy().clone();
		snapshot = OwnerTrace
			.of(new ReactiveVariable<>(CacheSnapshot.load(this)))
			.parent(this)
			.tag("role", "snapshot")
			.target();
		worker = new CacheWorker<>(this);
		input = OwnerTrace
			.of(new ReactiveWorker<CacheInput>(() -> CacheInput.link(this)))
			.parent(this)
			.tag("role", "input")
			.target();
		stability = OwnerTrace
			.of(new ReactiveWorker<Boolean>()
				.supplier(() -> CacheStability.evaluate(this))
				/*
				 * Do not ever block, not even initially. Simply report the cache as unstable.
				 */
				.initial(new ReactiveValue<>(false, false)))
			.parent(this)
			.tag("role", "stability")
			.target();
		/*
		 * Do not even create thread for caches with manual refresh.
		 */
		if (cache.cachePolicy().mode() != CacheRefreshMode.MANUAL)
			new CacheThread(this).start();
	}
	private static final ConcurrentMap<PersistentCache<?>, CacheOwner<?>> all = new ConcurrentHashMap<>();
	@SuppressWarnings("unchecked")
	static <T extends CacheFile> CacheOwner<T> of(PersistentCache<T> cache) {
		return (CacheOwner<T>)all.computeIfAbsent(cache, key -> new CacheOwner<>(cache));
	}
	@Override
	public synchronized String toString() {
		return cache.toString();
	}
}
