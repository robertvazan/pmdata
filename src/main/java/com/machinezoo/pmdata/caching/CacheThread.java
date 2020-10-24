// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import org.apache.commons.lang3.exception.*;
import org.slf4j.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.util.*;
import com.machinezoo.pmsite.utils.*;

public class CacheThread<T extends CacheFile> {
	private static final Logger logger = LoggerFactory.getLogger(CacheThread.class);
	private final PersistentCache<T> cache;
	public PersistentCache<T> cache() {
		return cache;
	}
	private CacheThread(PersistentCache<T> cache) {
		this.cache = cache;
		OwnerTrace.of(this).tag("cache", cache);
	}
	private static final ConcurrentMap<PersistentCache<?>, CacheThread<?>> all = new ConcurrentHashMap<>();
	@SuppressWarnings("unchecked")
	public static <T extends CacheFile> CacheThread<T> of(PersistentCache<T> cache) {
		return (CacheThread<T>)all.computeIfAbsent(cache, key -> new CacheThread<>(cache));
	}
	private CacheInput link() {
		var input = new CacheInput();
		try (var recording = input.record()) {
			try {
				cache.link();
				input.freeze();
				/*
				 * Verify that input hash can be calculated. This will call toString() on all the parameters.
				 */
				input.hash();
			} catch (Throwable ex) {
				/*
				 * Don't always log the exception. Stay silent if:
				 * - there's reactive blocking
				 * - the exception was caused by empty cache
				 * - there's any empty cache recorded in input (should also cover cases when EmptyCacheException is thrown)
				 * - there's any failing or cancelled cache recorded in input
				 * 
				 * Cancellation flags are ignored. Cancellation alone is not an error.
				 * Cancellation flag does not hide persisted exception either. It is therefore safe to ignore.
				 */
				if (!CurrentReactiveScope.blocked() && !input.snapshots().values().stream().anyMatch(s -> s == null || s.exception() != null))
					logger.warn("Persistent cache linker threw an exception.", ex);
				throw ex;
			}
		}
		return input;
	}
	private final ReactiveWorker<CacheInput> input = OwnerTrace
		.of(new ReactiveWorker<>(this::link))
		.parent(this)
		.tag("role", "links")
		.target();
	public CacheInput input() {
		return input.get();
	}
	private final ReactiveVariable<Progress.Goal> progress = OwnerTrace
		.of(new ReactiveVariable<Progress.Goal>())
		.parent(this)
		.tag("role", "progress")
		.target();
	public Progress.Goal progress() {
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
	/*
	 * Separate executor, so that we don't hog SiteThread.bulk(), which is intended for somewhat lighter tasks.
	 * Cache supplier can still internally parallelize and run small pieces of work on SiteThread.bulk() or elsewhere.
	 * Maybe we should in the future expose special heavy thread pool for cache parallelization.
	 */
	private static final ExecutorService executor = new SiteThread()
		.owner(CacheThread.class)
		.hardwareParallelism()
		.lowestPriority()
		.executor();
	private static final ReadWriteLock exclusivity = new ReentrantReadWriteLock();
	private void refresh(Progress.Goal goal) {
		try {
			CacheInput input = null;
			try {
				goal.stage("Exclusivity");
				var lock = cache.policy().exclusive() ? exclusivity.writeLock() : exclusivity.readLock();
				lock.lock();
				try {
					/*
					 * We are delaying linker query as much as possible,
					 * so that it incorporates results of cache refreshes scheduled before this one.
					 * Parallelism and async nature of ReactiveWorker however makes this unreliable.
					 * Automated scheduling is however smart enough to avoid scheduling the refresh too early.
					 * It is very unusual for linker to keep blocking for a long time.
					 */
					goal.stage("Linker");
					input = ReactiveFuture.supplyReactive(this::input).join();
					logger.info("Refreshing {}.", this);
					synchronized (this) {
						/*
						 * This must be synchronized, so that it does not run concurrently with related code above
						 * that also accesses goal's cancelled property and start timestamp at the same time.
						 * That way we can be sure that either the goal throws CancellationException
						 * or there is no concurrently running refresh and we can safely start one.
						 */
						goal.stageOff();
						started.set(Instant.now());
					}
					T data;
					try (
							var goalScope = goal.track();
							/*
							 * Create reactive scope, so that we can check for reactive blocking and so that reactive freezing works.
							 */
							var reactiveScope = new ReactiveScope().enter();
							/*
							 * In order to enforce consistency between linker and supplier, we will run supplier in frozen CacheInput context created by linker.
							 * If supplier uses something not declared by linker, exception will be thrown.
							 * Unfortunately, this does not allow us to detect superfluous dependencies declared by linker.
							 * It is nevertheless reasonable for linker-declared dependencies to be a superset of actually used dependencies.
							 */
							var inputScope = input.record();
							var outputScope = CacheFiles.redirect(CacheFiles.directory(cache))) {
						data = cache.supply();
						/*
						 * Any reactive blocking means the data is not up to date even if input hash matches.
						 */
						if (CurrentReactiveScope.blocked())
							throw new ReactiveBlockingException();
					}
					CacheSnapshot.update(cache, data, input, started.get());
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
					if (progress.get() == goal)
						CacheSnapshot.update(cache, ex, input, started.get());
				}
			}
		} finally {
			synchronized (this) {
				/*
				 * If we encountered refresh-cancel-refresh sequence of calls,
				 * our goal might no longer be the one stored in the reactive variable.
				 * In that case we should do nothing but quietly terminate.
				 */
				if (progress.get() == goal) {
					progress.set(null);
					started.set(null);
				}
			}
		}
	}
	public synchronized void schedule() {
		boolean permitted;
		try (var nonreactive = ReactiveScope.ignore()) {
			var goal = progress.get();
			/*
			 * We can ignore cancelled refresh if it is just queued but not started yet.
			 * Its goal will throw CancellationException when it is about to start.
			 */
			permitted = goal == null || goal.cancelled() && started.get() == null;
		}
		if (permitted) {
			var goal = new Progress.Goal();
			goal.stage("Scheduling");
			progress.set(goal);
			logger.info("Scheduling {}.", this);
			executor.submit(() -> refresh(goal));
		}
	}
	@Override
	public synchronized String toString() {
		return cache.toString();
	}
}
