// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.cache;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.machinezoo.noexception.*;

/*
 * This is general progress reporting service. There's no built-in UI for it.
 * DataWidget can nevertheless show progress of any PersistentCache.
 * 
 * This class is intentionally non-reactive, because progress reporting should be timer-based instead.
 * It is undesirable for progress UI to flicker at high speed in case progress is updated quickly.
 * Non-reactive implementation is also much faster, which allows more fine-grained progress reporting.
 */
public class Progress {
	/*
	 * Methods are synchronized to support multi-threaded batches.
	 */
	public static class Goal {
		private static final ThreadLocal<Goal> current = new ThreadLocal<>();
		/*
		 * Most code should use get(), which returns fallback if there's no current goal.
		 */
		public static Optional<Goal> current() {
			return Optional.ofNullable(current.get());
		}
		public static Goal get() {
			return current().orElseGet(Goal::new);
		}
		/*
		 * This establishes the goal as the current innermost goal via thread-local variable.
		 */
		public CloseableScope track() {
			var outer = current.get();
			current.set(this);
			return () -> current.set(outer);
		}
		/*
		 * Goal name is an arbitrary object. UI performs serialization. Immutable name is thus required.
		 * The object can have internal structure (array, list). UI is expected to unpack it.
		 * Many methods take ellipsis parameter, so that multiple levels can be specified together.
		 * We allow null/empty goal name. UI should be able to deal with that by providing fallback name.
		 */
		private final Object name;
		public Object name() {
			return name;
		}
		public Goal(Object... name) {
			this.name = name;
		}
		/*
		 * Cancellation is propagated to all current children.
		 * Attempt to add a new child will cause exception.
		 */
		private boolean cancelled;
		public synchronized boolean cancelled() {
			return cancelled;
		}
		public synchronized void cancel() {
			if (!cancelled) {
				cancelled = true;
				for (var subgoal : subgoals)
					subgoal.cancel();
			}
		}
		private final List<Goal> subgoals = new ArrayList<>();
		public synchronized List<Goal> subgoals() {
			return new ArrayList<>(subgoals);
		}
		public synchronized void add(Goal subgoal) {
			if (cancelled)
				throw new CancellationException();
			subgoals.add(subgoal);
		}
		public synchronized void remove(Goal subgoal) {
			subgoals.remove(subgoal);
		}
		private Object stage;
		public synchronized Object stage() {
			return stage;
		}
		public synchronized void stage(Object... stage) {
			if (cancelled)
				throw new CancellationException();
			this.stage = stage;
		}
		public void stageOff() {
			stage((Object[])null);;
		}
	}
	/*
	 * This indicates incremental progress to the goal without creating new subgoal.
	 * It instead changes stage for the current goal, which is appended to goal chain in UI.
	 * Empty parameter list or null parameter will clear the current stage.
	 */
	public static void log(Object... stage) {
		Goal.get().stage(stage);
	}
	/*
	 * This creates nested goal and establishes it as the current goal.
	 * Current goal's stage is cleared. Message array must be non-empty.
	 * This technically provides supports for multiple thread, although it's not very convenient.
	 */
	public static CloseableScope start(Object... name) {
		var parent = Goal.get();
		parent.stageOff();
		var subgoal = new Goal(name);
		parent.add(subgoal);
		return subgoal.track().andThen(() -> parent.remove(subgoal));
	}
	/*
	 * Convenience wrappers for the above.
	 */
	public static void run(Object name, Runnable runnable) {
		try (var scope = start(name)) {
			runnable.run();
		}
	}
	public static <T> T get(Object name, Supplier<T> supplier) {
		try (var scope = start(name)) {
			return supplier.get();
		}
	}
	/*
	 * Equivalent to opening a subgoal for every item.
	 * This has poor interactions with exceptions/returns unless there's an enveloping goal.
	 */
	public static <T> Iterable<T> loop(Object prefix, Iterable<T> iterable) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				var parent = Goal.get();
				var inner = iterable.iterator();
				return new Iterator<T>() {
					CloseableScope scope;
					private void close() {
						if (scope != null) {
							scope.close();
							scope = null;
						}
					}
					@Override
					public boolean hasNext() {
						close();
						return inner.hasNext();
					}
					@Override
					public T next() {
						close();
						var next = inner.next();
						var subgoal = new Goal(prefix, next);
						parent.add(subgoal);
						scope = subgoal.track().andThen(() -> parent.remove(subgoal));
						return next;
					}
				};
			}
		};
	}
	public static <T> Iterable<T> loop(Iterable<T> iterable) {
		return loop(null, iterable);
	}
}
