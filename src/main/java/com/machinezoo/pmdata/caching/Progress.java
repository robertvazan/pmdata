// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.machinezoo.closeablescope.*;
import one.util.streamex.*;

/*
 * This is general progress reporting service. There's no built-in UI for it.
 * DataWidget can nevertheless show progress of any PersistentCache.
 * 
 * This class is intentionally non-reactive, because progress reporting should be timer-based instead.
 * It is undesirable for progress UI to flicker at high speed in case progress is updated quickly.
 * Non-reactive implementation is also much faster, which allows for more fine-grained progress reporting.
 * 
 * Methods are synchronized to support multi-threaded batches.
 */
public class Progress {
	private static final ThreadLocal<Progress> current = new ThreadLocal<>();
	/*
	 * Most code should use get(), which returns fallback if there's no current goal.
	 */
	public static Optional<Progress> current() {
		return Optional.ofNullable(current.get());
	}
	public static Progress get() {
		return current().orElseGet(Progress::new);
	}
	/*
	 * This establishes the Progress instance as the current innermost progress frame via thread-local variable.
	 */
	public CloseableScope track() {
		var outer = current.get();
		current.set(this);
		return () -> current.set(outer);
	}
	/*
	 * Progress name is an arbitrary object. UI performs stringification. Immutable name is thus required.
	 * The object can have internal structure (array, list). UI is expected to unpack it.
	 * Many methods take ellipsis parameter, so that multiple levels can be specified together.
	 * We allow null/empty progress name. UI should be able to deal with that (with fallback or by skipping the name).
	 */
	private final Object name;
	public Object name() {
		return name;
	}
	public Progress(Object... name) {
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
			for (var child : children)
				child.cancel();
		}
	}
	/*
	 * Merely an opportunity to receive CancellationException.
	 * It could be used in the future for updating progress bars more accurately than with timers.
	 */
	public synchronized void tick() {
		if (cancelled)
			throw new CancellationException();
	}
	/*
	 * Milestones are intended for simple numbers (e.g., downloaded bytes so far),
	 * but they can be also used to display arbitrary variable information appended to progress tracker's name.
	 */
	private Object milestone;
	public synchronized Object milestone() {
		return milestone;
	}
	public synchronized void milestone(Object... milestone) {
		if (cancelled)
			throw new CancellationException();
		this.milestone = milestone;
	}
	private final List<Progress> children = new ArrayList<>();
	public synchronized List<Progress> children() {
		return new ArrayList<>(children);
	}
	public synchronized void add(Progress child) {
		if (cancelled)
			throw new CancellationException();
		children.add(child);
	}
	public synchronized void remove(Progress child) {
		/*
		 * Removals do not throw, so that we can clean up safely,
		 * especially since track() is supposed to be run in try-with-resources.
		 */
		children.remove(child);
	}
	/*
	 * Simple one-line stringification is implemented below, but UI can customize it arbitrarily.
	 */
	private static String join(List<String> list) {
		list = StreamEx.of(list).filter(o -> o != null).toList();
		if (list.isEmpty())
			return null;
		return StreamEx.of(list).joining(" / ");
	}
	private static String join(String... parts) {
		/*
		 * List.of() will throw on null argument.
		 */
		return join(Arrays.asList(parts));
	}
	private static String stringify(Object object) {
		if (object == null)
			return null;
		Collection<?> collection = null;
		if (object instanceof Collection)
			collection = (Collection<?>)object;
		if (object.getClass().isArray())
			collection = IntStreamEx.range(Array.getLength(object)).mapToObj(n -> Array.get(object, n)).toList();
		if (collection != null)
			return join(StreamEx.of(collection).map(o -> stringify(o)).toList());
		return object.toString();
	}
	/*
	 * This is separate from toString(), so that we can return null to indicate unnamed progress tracker.
	 */
	public synchronized String format() {
		var name = stringify(this.name);
		var milestone = stringify(this.milestone);
		if (milestone != null)
			name = name != null ? name + " (" + milestone + ")" : milestone;
		var children = StreamEx.of(this.children).map(g -> g.format()).filter(g -> g != null).toList();
		if (children.size() > 1)
			return join(name, "[" + String.join(", ", children) + "]");
		else if (!children.isEmpty())
			return join(name, children.get(0));
		else
			return name;
	}
	@Override
	public String toString() {
		return "Progress: " + Optional.of(format()).orElse("(unnamed)");
	}
	/*
	 * This creates nested progress and establishes it as the current progress tracker.
	 * This technically provides supports for multiple thread, although it's not very convenient.
	 */
	public CloseableScope start(Object... name) {
		var child = new Progress(name);
		add(child);
		return child.track().andThen(() -> remove(child));
	}
	/*
	 * Convenience wrappers for the above.
	 */
	public void run(Object name, Runnable runnable) {
		try (var scope = start(name)) {
			runnable.run();
		}
	}
	public <T> T get(Object name, Supplier<T> supplier) {
		try (var scope = start(name)) {
			return supplier.get();
		}
	}
	/*
	 * Equivalent to opening a subgoal for every item.
	 * This has poor interactions with exceptions/returns unless there's an enveloping goal.
	 */
	public <T> Iterable<T> loop(Object prefix, Iterable<T> iterable) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
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
						var child = new Progress(prefix, next);
						Progress.this.add(child);
						scope = child.track().andThen(() -> Progress.this.remove(child));
						return next;
					}
				};
			}
		};
	}
	public <T> Iterable<T> loop(Iterable<T> iterable) {
		return loop(null, iterable);
	}
	public <T> Iterable<T> loop(Object prefix, T[] array) {
		return loop(prefix, List.of(array));
	}
	public <T> Iterable<T> loop(T[] array) {
		return loop(null, array);
	}
}
