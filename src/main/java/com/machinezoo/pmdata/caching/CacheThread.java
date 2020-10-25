// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.machinezoo.hookless.*;
import com.machinezoo.hookless.time.*;
import com.machinezoo.hookless.util.*;
import one.util.streamex.*;

/*
 * Automatic refresh scheduling. There's one thread per cache except for manual caches.
 */
class CacheThread extends ReactiveThread {
	private final CacheOwner<?> owner;
	CacheThread(CacheOwner<?> owner) {
		this.owner = owner;
		OwnerTrace.of(this).parent(owner);
	}
	@Override
	protected void run() {
		var worker = owner.worker;
		/*
		 * Refresh already in progress.
		 */
		if (worker.progress() != null)
			return;
		/*
		 * This may throw. That's okay. We don't want to refresh the cache if its linker is failing.
		 */
		var input = owner.input.get();
		/*
		 * Blocking linker.
		 */
		if (CurrentReactiveScope.blocked())
			return;
		var snapshot = owner.snapshot.get();
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
		if (StreamEx.of(input.snapshots().keySet()).anyMatch(c -> !CacheOwner.of(c).stability.get()))
			return;
		boolean dirty = false;
		/*
		 * Empty cache. No need to check for null snapshot hash as that would imply either exception or cancellation.
		 */
		if (snapshot == null)
			dirty = true;
		var policy = owner.policy;
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
		worker.schedule();
	}
}
