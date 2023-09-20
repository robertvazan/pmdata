// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import com.machinezoo.hookless.*;
import com.machinezoo.hookless.time.*;

/*
 * Recursive cache stability indicator. Indicates either stable (whether ready or failing) or unstable (likely to change) cache.
 * This has nothing to do with ability of the cache to serve data. Cache can always hold on to older data.
 * Cache stability is intended to guide dependent computations, especially refreshes of dependent caches.
 */
public enum CacheStability {
	READY,
	/*
	 * Includes instability in any of the dependencies.
	 */
	UNSTABLE,
	/*
	 * Includes failure in any of the dependencies.
	 */
	FAILING;
	static CacheStability evaluate(CacheOwner owner) {
		ReactiveValue<CacheInput> input;
		/*
		 * Do not propagate blocking or exceptions from CacheInput
		 * Just fall back to unstable status when input is not available.
		 */
		try (var nonblocking = ReactiveScope.nonblocking()) {
			input = ReactiveValue.capture(() -> owner.input.get());
		}
		/*
		 * Blocking linker.
		 */
		if (input.blocking())
			return UNSTABLE;
		/*
		 * Failing linker.
		 */
		if (input.result() == null)
			return FAILING;
		/*
		 * Unstable or failing children.
		 */
		var children = input.result().stability();
		if (children != READY)
			return children;
		var snapshot = owner.snapshot.get();
		/*
		 * Empty cache.
		 */
		if (snapshot == null)
			return UNSTABLE;
		/*
		 * Stale cache, regardless of whether the cache holds a value or an exception.
		 */
		if (!input.result().hash().equals(snapshot.input()))
			return UNSTABLE;
		/*
		 * Expired cache. When the application launches after a long break,
		 * periodically refreshed caches may be extremely outdated. It is important to wait for their refresh.
		 */
		var policy = owner.cache.caching();
		if (policy.period() != null && ReactiveInstant.now().isAfter(snapshot.refreshed().plus(policy.period())))
			return UNSTABLE;
		/*
		 * Currently refreshing cache. Even up-to-date cache can be manually forced to refresh.
		 * We assume the cache is being forced to refresh because it is suspected to be out of date.
		 */
		var worker = owner.worker;
		if (worker.progress() != null)
			return UNSTABLE;
		/*
		 * Failing or cancelled cache, regardless of whether there is an older value available.
		 */
		if (snapshot.hash() == null || snapshot.exception() != null || snapshot.cancelled())
			return FAILING;
		return READY;
	}
}
