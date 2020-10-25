// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.concurrent.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.time.*;
import one.util.streamex.*;

/*
 * Automatic refresh scheduling. There's one trigger per cache.
 * This class shouldn't be ever created/started for caches with manual refresh.
 */
class CacheTrigger extends ReactiveThread {
	private final PersistentCache<?> cache;
	private CacheTrigger(PersistentCache<?> cache) {
		this.cache = cache;
	}
	private static final ConcurrentMap<PersistentCache<?>, CacheTrigger> all = new ConcurrentHashMap<>();
	/*
	 * The trigger has no way to start itself automatically.
	 * It needs to be hinted that some cache is in use.
	 * This method is safe to call multiple times.
	 */
	static void start(PersistentCache<?> cache) {
		/*
		 * Do not even create triggers for caches with manual refresh.
		 */
		if (cache.policy().mode() != CacheRefreshMode.MANUAL)
			all.computeIfAbsent(cache, CacheTrigger::new).start();
	}
	@Override
	protected void run() {
		var thread = CacheThread.of(cache);
		/*
		 * Refresh already in progress.
		 */
		if (thread.progress() != null)
			return;
		/*
		 * This may throw. That's okay. We don't want to refresh the cache if its linker is failing.
		 */
		var input = CacheInput.of(cache);
		/*
		 * Blocking linker.
		 */
		if (CurrentReactiveScope.blocked())
			return;
		var snapshot = CacheSnapshot.of(cache);
		/*
		 * Last refresh failed.
		 */
		if (snapshot != null && snapshot.exception() != null)
			return;
		/*
		 * Last refresh was cancelled.
		 */
		if (snapshot != null && snapshot.cancelled())
			return;
		/*
		 * Unstable dependency.
		 */
		if (StreamEx.of(input.snapshots().keySet()).anyMatch(c -> !CacheStability.of(c)))
			return;
		boolean dirty = false;
		/*
		 * Empty cache. No need to check for null snapshot hash as that would imply either exception or cancellation.
		 */
		if (snapshot == null)
			dirty = true;
		var policy = cache.policy();
		if (policy.mode() == CacheRefreshMode.AUTOMATIC && snapshot != null) {
			/*
			 * Stale cache.
			 * 
			 * This could theoretically start looping refreshes
			 * if the new snapshot for some reason does not have the updated input hash.
			 * That can however only happen in case of exception or cancellation, which are both handled above.
			 * Cache supplier cannot modify linker-generated input description,
			 * so successful refresh will always write the latest input hash into the snapshot.
			 */
			if (!input.hash().equals(snapshot.input()))
				dirty = true;
			/*
			 * Expired cache.
			 */
			if (policy.period() != null && ReactiveInstant.now().isAfter(snapshot.refreshed().plus(policy.period())))
				dirty = true;
		}
		/*
		 * No reason to refresh.
		 */
		if (!dirty)
			return;
		/*
		 * All conditions have been met. Start refresh.
		 */
		thread.schedule();
	}
}
