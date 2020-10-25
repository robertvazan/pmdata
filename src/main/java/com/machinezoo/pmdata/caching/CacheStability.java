// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.concurrent.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.time.*;
import com.machinezoo.hookless.util.*;
import one.util.streamex.*;

/*
 * Recursive cache stability indicator.
 */
class CacheStability {
	private static boolean evaluate(PersistentCache<?> cache) {
		var snapshot = CacheSnapshot.of(cache);
		/*
		 * Empty, failing, or cancelled cache. Mark the cache as unstable even if there's an older value available.
		 */
		if (snapshot == null || snapshot.hash() == null || snapshot.exception() != null || snapshot.cancelled())
			return false;
		var policy = cache.policy();
		/*
		 * Expired cache.
		 */
		if (snapshot != null && policy.period() != null && ReactiveInstant.now().isAfter(snapshot.refreshed().plus(policy.period())))
			return false;
		var thread = CacheThread.of(cache);
		/*
		 * Currently refreshing cache.
		 */
		if (thread.progress() != null)
			return false;
		ReactiveValue<CacheInput> input;
		/*
		 * Do not propagate blocking or exceptions from CacheInput
		 * Just fall back to unstable status when input is not available.
		 */
		try (var nonblocking = ReactiveScope.nonblocking()) {
			input = ReactiveValue.capture(() -> CacheInput.of(cache));
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
		if (input.result() != null && StreamEx.of(input.result().snapshots().keySet()).anyMatch(c -> !CacheStability.of(c)))
			return false;
		return true;
	}
	private static final ConcurrentMap<PersistentCache<?>, ReactiveWorker<Boolean>> all = new ConcurrentHashMap<>();
	static boolean of(PersistentCache<?> cache) {
		var worker = all.computeIfAbsent(cache, key -> OwnerTrace
			.of(new ReactiveWorker<Boolean>()
				.supplier(() -> evaluate(cache))
				/*
				 * Do not ever block, not even initially. Simply report the cache as unstable.
				 */
				.initial(new ReactiveValue<>(false, false)))
			.parent(CacheStability.class)
			.tag("cache", cache)
			.target());
		return worker.get();
	}
}
