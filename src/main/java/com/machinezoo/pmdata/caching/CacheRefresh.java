// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.time.*;
import java.util.concurrent.*;
import org.apache.commons.lang3.builder.*;
import com.machinezoo.pmsite.utils.*;

/*
 * Prioritized execution of cache refreshes.
 */
class CacheRefresh implements Runnable, Comparable<CacheRefresh> {
	private final Instant scheduled = Instant.now();
	private final Runnable next;
	CacheRefresh(Runnable next) {
		this.next = next;
	}
	boolean exclusive;
	Duration cost = Duration.ZERO;
	@Override
	public void run() {
		next.run();
	}
	@Override
	public int compareTo(CacheRefresh other) {
		return new CompareToBuilder()
			/*
			 * Refreshes that need exclusive CPU access are scheduled last,
			 * so that they don't interfere with parallelization of non-exclusive tasks.
			 */
			.append(exclusive, other.exclusive)
			/*
			 * Refresh cheaper caches first, so that heavy caches don't hog the CPU.
			 * This will automatically sort empty caches to the front. Cache initialization has priority over refresh.
			 */
			.append(cost, other.cost)
			/*
			 * Caches with the same cost are usually the empty caches.
			 * Sort them in reverse order of scheduling, so that recently requested caches are prioritized.
			 */
			.append(-scheduled.toEpochMilli(), -other.scheduled.toEpochMilli())
			.toComparison();
	}
	/*
	 * Create separate executor, so that we don't hog SiteThread.bulk(), which is intended for somewhat lighter tasks.
	 * Cache code can still internally parallelize and run small pieces of work on SiteThread.bulk() or elsewhere.
	 * Maybe we should expose special heavy thread pool for cache parallelization in the future.
	 * 
	 * We also need refresh prioritization, which requires separate executor.
	 * 
	 * ThreadPoolExecutor wraps submitted Runnable instances in FutureTask, which will break sorting.
	 * We have to inherit from ThreadPoolExecutor and return our own sortable task from newTaskFor().
	 */
	private static class CacheRefreshTask<T> extends FutureTask<T> implements Comparable<Object> {
		private final CacheRefresh refresh;
		CacheRefreshTask(CacheRefresh refresh, T value) {
			super(refresh, value);
			this.refresh = refresh;
		}
		@Override
		public int compareTo(Object o) {
			/*
			 * Sort unrecognized tasks to the front.
			 */
			if (!(o instanceof CacheRefreshTask))
				return 1;
			return refresh.compareTo(((CacheRefreshTask<?>)o).refresh);
		}
	}
	/*
	 * This will not really work for arbitrary tasks as those will fail to cast to Comparable in PriorityBlockingQueue.
	 * If we want to allow tasks other than CacheRefreshTask in the future, we will have to use Comparator instead of Comparable.
	 */
	private static class CacheRefreshExecutor extends ThreadPoolExecutor {
		CacheRefreshExecutor(int parallelism, ThreadFactory factory) {
			super(parallelism, parallelism, 0, TimeUnit.SECONDS, new PriorityBlockingQueue<>(), factory);
		}
		@Override
		protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
			if (runnable instanceof CacheRefresh)
				return new CacheRefreshTask<>((CacheRefresh)runnable, value);
			else
				return super.newTaskFor(runnable, value);
		}
	}
	static final ExecutorService executor;
	static {
		int parallelism = Runtime.getRuntime().availableProcessors();
		var factory = new SiteThread()
			.owner(CacheRefresh.class)
			.lowestPriority()
			.factory();
		executor = new CacheRefreshExecutor(parallelism, factory);
	}
}
