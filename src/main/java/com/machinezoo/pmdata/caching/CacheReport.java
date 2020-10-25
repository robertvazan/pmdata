// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.stream.*;
import org.apache.commons.collections4.comparators.*;
import org.apache.commons.lang3.exception.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.time.*;
import com.machinezoo.hookless.util.*;
import com.machinezoo.pmdata.formatters.*;
import com.machinezoo.pmdata.widgets.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pmsite.utils.*;
import com.machinezoo.pushmode.dom.*;
import one.util.streamex.*;

public class CacheReport {
	private CacheInput input = new CacheInput();
	public CacheReport input(CacheInput input) {
		this.input = input;
		return this;
	}
	private Throwable exception;
	public CacheReport exception(Throwable exception) {
		this.exception = exception;
		return this;
	}
	/*
	 * It is assumed that production version is accessible to random visitors
	 * who shouldn't be able to manipulate or to even view cache state.
	 */
	private boolean expandable = SiteRunMode.get() != SiteRunMode.PRODUCTION;
	public CacheReport expandable(boolean expandable) {
		this.expandable = expandable;
		return this;
	}
	private static enum CacheStatus {
		READY(Tone.OK, "Ready", "Content depends on persistent cache."),
		EXPIRED(Tone.WARNING, "Expired", "Content depends on expired cache."),
		STALE(Tone.WARNING, "Stale", "Content depends on stale cache."),
		EMPTY(Tone.WARNING, "Empty", "Content depends on uninitialized cache."),
		FAILED(Tone.FAILURE, "Failed", "Exception was thrown while generating content."),
		LINKING(Tone.PROGRESS, "Linking", "Cache graph is being constructed."),
		QUEUED(Tone.PROGRESS, "Queued", "Cache refresh is pending."),
		RUNNING(Tone.PROGRESS, "Refreshing", "Cache refresh is in progress."),
		CANCELLED(Tone.WARNING, "Cancelled", "Cache refresh has been cancelled.");
		final Tone tone;
		final String label;
		final String message;
		CacheStatus(Tone tone, String label, String message) {
			this.tone = tone;
			this.label = label;
			this.message = message;
		}
		@Override
		public String toString() {
			return label;
		}
	}
	private static class CacheInfo {
		PersistentCache<?> cache;
		CacheWorker<?> worker;
		/*
		 * Stringified cache ID.
		 */
		String name;
		ReactiveValue<CacheInput> input;
		CacheSnapshot<?> snapshot;
		List<CacheInfo> children;
		/*
		 * Maximum depth.
		 */
		int depth;
		CacheStatus status;
		Progress.Goal progress;
	}
	private static class CacheCollection {
		List<CacheInfo> sorted = new ArrayList<>();
		Map<PersistentCache<?>, CacheInfo> hashed = new HashMap<>();
		/*
		 * Depth-first search. Sorted cache list will always have dependencies sorted before dependent cache (child-first order).
		 */
		void collect(CacheInput input) {
			for (var cache : input.snapshots().keySet()) {
				if (!hashed.containsKey(cache)) {
					var info = new CacheInfo();
					info.cache = cache;
					var owner = CacheOwner.of(cache);
					info.worker = owner.worker;
					/*
					 * Make sure the input we expand is the one recorded in CacheInfo.
					 * 
					 * Do not propagate blocking from CacheInput. It could be blocking for a long time or forever.
					 */
					try (var nonblocking = ReactiveScope.nonblocking()) {
						/*
						 * Do not propagate exception from CacheInput. It could be failing permanently.
						 * It's better to capture the exception and report it later.
						 */
						info.input = ReactiveValue.capture(() -> owner.input.get());
					}
					if (info.input.result() != null)
						collect(info.input.result());
					/*
					 * Guard against self-referencing caches.
					 */
					if (!hashed.containsKey(cache)) {
						info.name = cache.toString();
						info.snapshot = input.snapshot(cache);
						if (info.input.result() != null) {
							info.children = StreamEx.of(info.input.result().snapshots().keySet())
								.map(c -> hashed.get(c))
								.sortedBy(e -> e.name)
								.toList();
						} else
							info.children = Collections.emptyList();
						hashed.put(cache, info);
						sorted.add(info);
					}
				}
			}
		}
	}
	public void show() {
		if (input.snapshots().isEmpty())
			return;
		var caches = new CacheCollection();
		caches.collect(input);
		/*
		 * Sort dependent caches before their dependencies (parent-first order).
		 */
		Collections.reverse(caches.sorted);
		/*
		 * Now that we have partial ordering by dependency relations, we can calculate maximum depth for every cache.
		 */
		for (var parent : caches.sorted)
			for (var child : parent.children)
				child.depth = Math.max(child.depth, parent.depth + 1);
		/*
		 * Sort first by increasing depth, then by name.
		 */
		caches.sorted.sort(new ComparatorChain<>(List.of(Comparator.comparingInt(c -> c.depth), Comparator.comparing(c -> c.name))));
		/*
		 * Child-first order while examining subtree properties.
		 */
		Collections.reverse(caches.sorted);
		var status = CacheStatus.READY;
		var empty = exception != null && ExceptionUtils.getThrowableList(exception).stream().anyMatch(x -> x instanceof EmptyCacheException);
		if (exception != null)
			status = empty ? CacheStatus.EMPTY : CacheStatus.FAILED;
		for (var entry : caches.sorted) {
			entry.progress = entry.worker.progress();
			if (entry.progress != null) {
				if (entry.worker.started() == null)
					entry.status = CacheStatus.QUEUED;
				else
					entry.status = CacheStatus.RUNNING;
			} else if (entry.input.blocking())
				entry.status = CacheStatus.LINKING;
			else if (entry.snapshot == null)
				entry.status = CacheStatus.EMPTY;
			else if (entry.snapshot.cancelled() && ReactiveDuration.between(entry.snapshot.refreshed(), ReactiveInstant.now()).compareTo(Duration.ofSeconds(3)) < 0)
				entry.status = CacheStatus.CANCELLED;
			else if (entry.input.exception() != null || entry.snapshot.exception() != null)
				entry.status = CacheStatus.FAILED;
			else if (entry.snapshot.hash() == null)
				entry.status = CacheStatus.EMPTY;
			else if (!entry.input.result().hash().equals(entry.snapshot.input()))
				entry.status = CacheStatus.STALE;
			else if (entry.cache.policy().period() != null && ReactiveInstant.now().isAfter(entry.snapshot.refreshed().plus(entry.cache.policy().period())))
				entry.status = CacheStatus.EXPIRED;
			else
				entry.status = CacheStatus.READY;
			if (entry.status.ordinal() > status.ordinal())
				status = entry.status;
		}
		if (!expandable) {
			/*
			 * OK status only needs to be shown when there's expansion button in it.
			 */
			if (status != CacheStatus.READY)
				Notice.show(status.tone, status.message);
			return;
		}
		/*
		 * Parent-first order for display.
		 */
		Collections.reverse(caches.sorted);
		var fragment = SiteFragment.get().nest("caches");
		try (var scope = fragment.open()) {
			var prefs = fragment.preferences();
			boolean expanded = prefs.getBoolean("show-caches", false);
			new Notice()
				.key(fragment.elementId("status"))
				.tone(status.tone)
				.content(new DomFragment()
					.add(status.message)
					.add(" ")
					.add(new LinkButton(expanded ? "Hide details." : "Show details.")
						.handle(() -> prefs.putBoolean("show-caches", !expanded))
						.html()))
				.render();
			if (expanded) {
				try (var view = new CasePicker("Cache view")) {
					if (view.is("Summary")) {
						try (var table = new PlainTable("Cache summary")) {
							var groups = StreamEx.of(caches.sorted).groupingBy(c -> c.status);
							for (var key : StreamEx.of(groups.keySet()).sorted()) {
								table.add("Status", key).tone(key.tone);
								var group = groups.get(key);
								boolean cancellable = key == CacheStatus.QUEUED || key == CacheStatus.RUNNING;
								var action = cancellable ? "Cancel" : status == CacheStatus.EMPTY ? "Populate" : "Refresh";
								table.add("Action", fragment.nest("summary", key.toString())
									.run(() -> new LinkButton(action)
										.handle(() -> group.stream().map(g -> g.worker).forEach(cancellable ? CacheWorker::cancel : CacheWorker::schedule))
										.render())
									.content());
								table.add("Count", group.size());
								var snapshots = StreamEx.of(group).filter(g -> g.snapshot != null).map(g -> g.snapshot).toList();
								table.add("Size", Pretty.bytes().format(snapshots.stream().mapToLong(s -> s.size()).sum()));
								table.add("Cost", snapshots.stream().map(s -> s.cost()).reduce((a, b) -> a.plus(b)));
								table.add("Updated", snapshots.stream().map(s -> s.updated()).max(Comparator.naturalOrder()));
								table.add("Refreshed", snapshots.stream().map(s -> s.refreshed()).max(Comparator.naturalOrder()));
							}
						}
					}
					if (view.is("Progress")) {
						var running = StreamEx.of(caches.sorted).filter(e -> e.status == CacheStatus.RUNNING).toList();
						if (running.isEmpty())
							Notice.info("No refresh in progress.");
						for (var entry : running) {
							/*
							 * Wake up every second to refresh the progress.
							 */
							ReactiveInstant.now().plus(Duration.of(ConsistentRandom.of(fragment.elementId()).nextInt(1000), ChronoUnit.MILLIS)).truncatedTo(ChronoUnit.SECONDS);
							/*
							 * Give every Cancel button unique ID. Create one extra "progress" subfragment to differentiate it from Cancel buttons in the table.
							 */
							fragment.nest("progress", entry.name)
								.run(() -> new Notice()
									/*
									 * Optimization. Avoid large HTML diffs when progress boxes appear/disappear.
									 */
									.key(SiteFragment.get().elementId())
									.tone(Tone.PROGRESS)
									.content(new DomFragment()
										.add(String.format("Refreshing %s... ", entry.name))
										.add(new LinkButton("Cancel")
											.handle(entry.worker::cancel)
											.html())
										.add(Html.br())
										.add(entry.progress.format()))
									.render())
								.render();
						}
						var queued = caches.sorted.stream().filter(e -> e.status == CacheStatus.QUEUED).count();
						if (queued > 0) {
							new Notice()
								.key(fragment.elementId("queued"))
								.tone(Tone.PROGRESS)
								.format("Refresh queue contains %d cache(s).", queued)
								.render();
						}
					}
					if (view.is("Caches")) {
						try (var table = new PlainTable("Caches")) {
							for (var entry : caches.sorted) {
								table.add("Status", entry.status).tone(entry.status.tone);
								boolean cancellable = entry.status == CacheStatus.QUEUED || entry.status == CacheStatus.RUNNING;
								var action = cancellable ? "Cancel" : status == CacheStatus.EMPTY ? "Populate" : "Refresh";
								table.add("Action", fragment.nest("list", entry.name)
									.run(() -> new LinkButton(action)
										.handle(cancellable ? entry.worker::cancel : entry.worker::schedule)
										.render())
									.content());
								if (entry.snapshot != null) {
									table.add("Size", Pretty.bytes().format(entry.snapshot.size()));
									table.add("Cost", entry.snapshot.cost());
									table.add("Updated", entry.snapshot.updated());
									table.add("Refreshed", entry.snapshot.refreshed());
								} else {
									table.add("Size", "");
									table.add("Cost", "");
									table.add("Updated", "");
									table.add("Refreshed", "");
								}
								table.add("Cache", entry.name).left();
							}
						}
					}
					if (view.is("Details")) {
						var detailsPref = prefs.get("show-details", null);
						var details = caches.sorted.stream().filter(c -> c.name.equals(detailsPref)).findFirst().orElse(null);
						try (var dview = new CasePicker("Cache details")) {
							if (dview.is("Pick")) {
								try (var table = new PlainTable("Caches")) {
									for (var entry : caches.sorted) {
										table.add("Status", entry.status).tone(entry.status.tone);
										if (entry != details) {
											table.add("Details", fragment.nest("details", entry.name)
												.run(() -> new LinkButton("Pick")
													.handle(() -> prefs.put("show-details", entry.name))
													.render())
												.content());
										} else
											table.add("Details", "Selected");
										table.add("Cache", entry.name).left();
									}
								}
							}
							if (details != null) {
								StaticContent.show("Selected cache", details.name);
								if (details.input.blocking())
									Notice.warn("Cache linker is blocking. Some information might not be available.");
								if (dview.is("Cache")) {
									new StaticContent("Status")
										.add(details.status.toString())
										.tone(details.status.tone)
										.render();
									StaticContent.show("Length of the longest dependent chain", details.depth);
									StaticContent.show("Input hash", details.input.result() != null ? details.input.result().hash() : "(unavailable)");
									var started = details.worker.started();
									if (started != null)
										StaticContent.show("Refresh started", started);
									if (details.progress != null)
										StaticContent.show("Progress", details.progress);
								}
								if (dview.is("Contents")) {
									if (details.snapshot != null) {
										var snapshot = details.snapshot;
										StaticContent.show("Input hash", snapshot.input());
										StaticContent.show("Path", snapshot.path());
										StaticContent.show("Content hash", snapshot.hash());
										StaticContent.show("Size", new ByteFormatter().format(snapshot.size()));
										StaticContent.show("Cost", snapshot.cost());
										StaticContent.show("Updated", snapshot.updated());
										StaticContent.show("Refreshed", snapshot.refreshed());
										StaticContent.show("Failed", snapshot.exception() != null ? "Yes" : "No");
										StaticContent.show("Cancelled", snapshot.cancelled() ? "Yes" : "No");
									} else
										Notice.info("Cache is empty.");
								}
								if (dview.is("Parameters")) {
									if (details.input.result() != null) {
										var parameters = details.input.result().parameters();
										if (!parameters.isEmpty()) {
											try (var table = new PlainTable("Parameters")) {
												for (var name : StreamEx.of(parameters.keySet()).sorted()) {
													table.add("Name", name).left();
													table.add("Value", parameters.get(name)).left();
												}
											}
										} else
											Notice.info("Cache has no parameters.");
									} else
										Notice.info("Cache parameters have not been determined yet.");
								}
								if (dview.is("Dependencies")) {
									if (!details.children.isEmpty()) {
										try (var table = new PlainTable("Dependencies")) {
											for (var child : details.children) {
												table.add("Status", child.status.label).tone(child.status.tone);
												table.add("Cache", child.name).left();
											}
										}
									} else
										Notice.info("Cache has no dependencies.");
								}
								if (dview.is("Exception")) {
									if (details.input.exception() != null)
										SiteFragment.get().add(Html.pre().clazz("site-error").add(new CachedException(details.input.exception()).getFormattedCause()));
									else if (details.snapshot != null && details.snapshot.exception() != null)
										SiteFragment.get().add(Html.pre().clazz("site-error").add(details.snapshot.exception()));
									else
										Notice.info("No exception was reported for this cache.");
								}
							}
						}
					}
					if (view.is("Exception")) {
						var error = exception != null && !empty
							? new CachedException(exception).getFormattedCause()
							: Stream
								.concat(
									caches.sorted.stream()
										.map(e -> e.input.exception())
										.filter(x -> x != null)
										.map(x -> new CachedException(x).getFormattedCause()),
									caches.sorted.stream()
										.filter(e -> e.snapshot != null)
										.map(e -> e.snapshot.exception())
										.filter(x -> x != null))
								.findFirst().orElse(null);
						if (error != null)
							SiteFragment.get().add(Html.pre().clazz("site-error").add(error));
						else
							Notice.info("No exception was reported.");
					}
				}
			}
		}
		fragment.render();
	}
}
