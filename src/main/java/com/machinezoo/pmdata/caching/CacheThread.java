// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.machinezoo.hookless.*;
import com.machinezoo.hookless.time.*;
import com.machinezoo.hookless.util.*;

/*
 * Automatic refresh scheduling. There's one thread per cache except for manual caches.
 */
class CacheThread extends ReactiveThread {
	private final CacheOwner owner;
	CacheThread(CacheOwner owner) {
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
		 * Unstable or failing dependency.
		 */
		if (input.stability() != CacheStability.READY)
			return;
		boolean dirty = false;
		/*
		 * Empty cache. No need to check for null snapshot hash as that would imply either exception or cancellation.
		 */
		if (snapshot == null)
			dirty = true;
		var policy = owner.cache.caching();
		if (policy.mode() == CacheRefreshMode.AUTOMATIC && snapshot != null) {
			/*
			 * Stale cache.
			 * 
			 * This will not cause looping of refreshes even if the cache is failed or cancelled,
			 * because stored input hash reflects last refresh, successful or not,
			 * instead of the input used to generate last valid cache content.
			 * If there's an exception, it is stored along with an up-to-date hash, preventing further refreshes.
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
