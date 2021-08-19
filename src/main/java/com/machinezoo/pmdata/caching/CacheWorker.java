// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import org.apache.commons.lang3.exception.*;
import org.slf4j.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.util.*;

public class CacheWorker<T extends CacheFile> {
	private static final Logger logger = LoggerFactory.getLogger(CacheWorker.class);
	private final CacheOwner<T> owner;
	CacheWorker(CacheOwner<T> owner) {
		this.owner = owner;
		OwnerTrace.of(this).parent(owner);
	}
	public static <T extends CacheFile> CacheWorker<T> of(PersistentCache<T> cache) {
		return CacheOwner.of(cache).worker;
	}
	public PersistentCache<T> cache() {
		return owner.cache;
	}
	private final ReactiveVariable<Progress> progress = OwnerTrace
		.of(new ReactiveVariable<Progress>())
		.parent(this)
		.tag("role", "progress")
		.target();
	public Progress progress() {
		return progress.get();
	}
	private final ReactiveVariable<Instant> started = OwnerTrace
		.of(new ReactiveVariable<Instant>())
		.parent(this)
		.tag("role", "started")
		.target();
	public Instant started() {
		return started.get();
	}
	public synchronized void cancel() {
		try (var nonreactive = ReactiveScope.ignore()) {
			var goal = progress.get();
			if (goal != null)
				goal.cancel();
		}
	}
	private static final ReadWriteLock exclusivity = new ReentrantReadWriteLock();
	private void refresh(Progress progress) {
		try {
			CacheInput input = null;
			try {
				/*
				 * Remove the "Scheduling" child added in schedule().
				 */
				progress.remove(progress.children().get(0));
				var lock = owner.policy.exclusive() ? exclusivity.writeLock() : exclusivity.readLock();
				progress.run("Exclusivity", () -> lock.lock());
				try {
					logger.info("Refreshing {}.", this);
					synchronized (this) {
						/*
						 * This must be synchronized, so that it does not run concurrently with related code in schedule()
						 * that also accesses goal's cancelled property and start timestamp at the same time.
						 * That way we can be sure that either the progress tracker throws CancellationException
						 * or there is no concurrently running refresh and we can safely start one.
						 */
						progress.tick();
						started.set(Instant.now());
					}
					T data;
					try (
							var goalScope = progress.track();
							/*
							 * Create reactive scope, so that we can check for reactive blocking and so that reactive freezing works.
							 */
							var reactiveScope = new ReactiveScope().enter();
							var outputScope = CacheFiles.redirect(CacheFiles.directory(owner.cache))) {
						/*
						 * We are delaying linker query as much as possible,
						 * so that it incorporates results of cache refreshes scheduled before this one.
						 * Parallelism and async nature of ReactiveWorker however makes this unreliable.
						 * Automated scheduling is however smart enough to avoid scheduling the refresh too early.
						 * 
						 * We will not block this thread (via ReactiveFuture.supplyReactive()) until linker output is non-blocking,
						 * because that could cause very long blocking or even deadlocks.
						 */
						input = owner.input.get();
						/*
						 * If the linker throws, just fail the whole refresh.
						 * If it reactively blocks, consider this refresh to have started prematurely and fail it immediately too.
						 */
						if (CurrentReactiveScope.blocked())
							throw new ReactiveBlockingException();
						/*
						 * In order to enforce consistency between linker and supplier, we will run supplier in frozen CacheInput context created by linker.
						 * If supplier uses something not declared by linker, exception will be thrown.
						 * Unfortunately, this does not allow us to detect superfluous dependencies declared by linker.
						 * It is nevertheless reasonable for linker-declared dependencies to be a superset of actually used dependencies.
						 */
						try (var inputScope = input.record()) {
							data = owner.cache.computeCache();
						}
						/*
						 * Any reactive blocking means the data is not up to date even if input hash matches.
						 */
						if (CurrentReactiveScope.blocked())
							throw new ReactiveBlockingException();
					}
					CacheSnapshot.update(owner, data, input, started.get());
					logger.info("Refreshed {}.", this);
				} finally {
					lock.unlock();
				}
			} catch (Throwable ex) {
				/*
				 * There's a lot of code in this exception handler, which creates risk of secondary exceptions.
				 * There's however no code here that could throw under normal circumstances.
				 * 
				 * We want to know about unexpected failures. That naturally excludes cancellations, which are triggered by the user.
				 */
				if (!ExceptionUtils.getThrowableList(ex).stream().anyMatch(x -> x instanceof CancellationException))
					logger.error("Failed to refresh persistent cache {}.", this, ex);
				else
					logger.info("Cancelled refresh of {}.", this);
				synchronized (this) {
					/*
					 * This code might run when there's concurrent refresh underway after refresh-cancel-refresh sequence.
					 * In such a situation, we cannot change snapshot, because the change might overwrite results of the newer refresh.
					 * In order to detect this situation, we will check whether our goal is the current goal.
					 * This code must be synchronized, so that no concurrent refresh is created in between the condition and actual snapshot write.
					 */
					if (this.progress.get() == progress)
						CacheSnapshot.update(owner, ex, input, started.get());
				}
			}
		} finally {
			synchronized (this) {
				/*
				 * If we encountered refresh-cancel-refresh sequence of calls,
				 * our goal might no longer be the one stored in the reactive variable.
				 * In that case we should do nothing but quietly terminate.
				 */
				if (this.progress.get() == progress) {
					this.progress.set(null);
					started.set(null);
				}
			}
		}
	}
	public synchronized void schedule() {
		boolean permitted;
		try (var nonreactive = ReactiveScope.ignore()) {
			var progress = this.progress.get();
			/*
			 * We can ignore cancelled refresh if it is just queued but not started yet.
			 * Its goal will throw CancellationException when it is about to start.
			 */
			permitted = progress == null || progress.cancelled() && started.get() == null;
		}
		if (permitted) {
			var progress = new Progress();
			progress.add(new Progress("Scheduling"));
			this.progress.set(progress);
			logger.info("Scheduling {}.", this);
			var refresh = new CacheRefresh(() -> refresh(progress));
			refresh.exclusive = owner.policy.exclusive();
			var snapshot = owner.snapshot.get();
			if (snapshot != null)
				refresh.cost = snapshot.cost();
			CacheRefresh.executor.submit(refresh);
		}
	}
	@Override
	public synchronized String toString() {
		return owner.toString();
	}
}
