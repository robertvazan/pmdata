// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.machinezoo.hookless.*;
import com.machinezoo.hookless.time.*;
import one.util.streamex.*;

/*
 * Recursive cache stability indicator.
 */
class CacheStability {
	static boolean evaluate(CacheOwner owner) {
		var snapshot = owner.snapshot.get();
		/*
		 * Empty, failing, or cancelled cache. Mark the cache as unstable even if there's an older value available.
		 */
		if (snapshot == null || snapshot.hash() == null || snapshot.exception() != null || snapshot.cancelled())
			return false;
		var policy = owner.cache.caching();
		/*
		 * Expired cache.
		 */
		if (snapshot != null && policy.period() != null && ReactiveInstant.now().isAfter(snapshot.refreshed().plus(policy.period())))
			return false;
		var worker = owner.worker;
		/*
		 * Currently refreshing cache.
		 */
		if (worker.progress() != null)
			return false;
		ReactiveValue<CacheInput> input;
		/*
		 * Do not propagate blocking or exceptions from CacheInput
		 * Just fall back to unstable status when input is not available.
		 */
		try (var nonblocking = ReactiveScope.nonblocking()) {
			input = ReactiveValue.capture(() -> owner.input.get());
		}
		/*
		 * Failing or blocking linker.
		 */
		if (input.result() == null || input.blocking())
			return false;
		/*
		 * Stale cache.
		 */
		if (snapshot != null && input.result() != null && !input.result().hash().equals(snapshot.input()))
			return false;
		/*
		 * Unstable children.
		 */
		if (input.result() != null && StreamEx.of(input.result().snapshots().keySet()).anyMatch(c -> !CacheOwner.of(c).stability.get()))
			return false;
		return true;
	}
}
