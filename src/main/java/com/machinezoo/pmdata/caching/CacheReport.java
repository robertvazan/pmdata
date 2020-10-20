// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.stream.*;
import org.apache.commons.collections4.comparators.*;
import org.apache.commons.lang3.exception.*;
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
	}
	private static class CacheInfo {
		CacheState<?> cache;
		/*
		 * Stringified cache ID.
		 */
		String name;
		CacheSnapshot<?> snapshot;
		List<CacheInfo> children;
		/*
		 * Maximum depth.
		 */
		int depth;
		CacheStatus status;
		/*
		 * Max of snapshot refresh timestamp of this cache and all dependencies recursively.
		 * Null if this cache or any dependency isn't ready.
		 */
		Instant refreshedMax;
		Progress.Goal progress;
	}
	private static class CacheCollection {
		List<CacheInfo> sorted = new ArrayList<>();
		Map<Object, CacheInfo> byId = new HashMap<>();
		/*
		 * Depth-first search. Sorted cache list will always have dependencies sorted before dependent cache (child-first order).
		 */
		void collect(CacheInput input) {
			for (var cache : input.snapshots().keySet()) {
				if (!byId.containsKey(cache.id())) {
					/*
					 * Make sure the input we expand is the one recorded in CacheInfo.
					 */
					var dependencies = cache.input();
					collect(dependencies);
					/*
					 * Guard against self-referencing caches.
					 */
					if (!byId.containsKey(cache.id())) {
						var info = new CacheInfo();
						info.cache = cache;
						info.name = cache.id().toString();
						info.snapshot = input.snapshot(cache);
						info.children = StreamEx.of(dependencies.snapshots().keySet())
							.map(c -> byId.get(c.id()))
							.sortedBy(e -> e.name)
							.toList();
						byId.put(cache.id(), info);
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
		int concurrency = Runtime.getRuntime().availableProcessors();
		for (var entry : caches.sorted) {
			entry.progress = entry.cache.progress();
			if (entry.progress != null) {
				if (entry.cache.started() == null)
					entry.status = CacheStatus.QUEUED;
				else
					entry.status = CacheStatus.RUNNING;
			} else if (entry.snapshot == null)
				entry.status = CacheStatus.EMPTY;
			else if (entry.snapshot.cancelled() && ReactiveDuration.between(entry.snapshot.refreshed(), ReactiveInstant.now()).compareTo(Duration.ofSeconds(3)) < 0)
				entry.status = CacheStatus.CANCELLED;
			else if (entry.snapshot.exception() != null)
				entry.status = CacheStatus.FAILED;
			else if (entry.snapshot.hash() == null)
				entry.status = CacheStatus.EMPTY;
			else if (!entry.cache.input().hash().equals(entry.snapshot.input()))
				entry.status = CacheStatus.STALE;
			else if (entry.cache.policy().period() != null && ReactiveInstant.now().isAfter(entry.snapshot.refreshed().plus(entry.cache.policy().period())))
				entry.status = CacheStatus.EXPIRED;
			else
				entry.status = CacheStatus.READY;
			if (entry.status.ordinal() > status.ordinal())
				status = entry.status;
			var unstableDeps = entry.children.stream().anyMatch(e -> e.refreshedMax == null);
			var dependencyTime = entry.children.stream().map(e -> e.refreshedMax).filter(t -> t != null).max(Comparator.naturalOrder()).orElse(null);
			if (entry.status == CacheStatus.READY) {
				if (entry.children.isEmpty())
					entry.refreshedMax = entry.snapshot.refreshed;
				else if (!unstableDeps)
					entry.refreshedMax = Stream.of(entry.snapshot.refreshed, dependencyTime).max(Comparator.naturalOrder()).get();
			}
			boolean refresh = false;
			if (entry.status == CacheStatus.EMPTY && entry.cache.policy().mode() != CacheRefreshMode.MANUAL)
				refresh = true;
			if (entry.status == CacheStatus.STALE && entry.cache.policy().mode() == CacheRefreshMode.AUTOMATIC)
				refresh = true;
			if (entry.status == CacheStatus.EXPIRED)
				refresh = true;
			if (unstableDeps)
				refresh = false;
			/*
			 * Avoid repeated refresh. This essentially protects against repeated refresh if there's inconsistency between linker and supplier.
			 */
			if (entry.status == CacheStatus.STALE && !entry.children.isEmpty() && !unstableDeps
				&& dependencyTime.isBefore(entry.snapshot.refreshed().minus(entry.snapshot.cost()))) {
				refresh = false;
			}
			if (concurrency <= 0)
				refresh = false;
			if (refresh) {
				entry.cache.refresh();
				--concurrency;
			} else if (entry.status == CacheStatus.RUNNING || entry.status == CacheStatus.QUEUED)
				--concurrency;
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
				for (var entry : StreamEx.of(caches.sorted).filter(e -> e.status == CacheStatus.RUNNING)) {
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
									.handle(entry.cache::cancel)
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
				var detailsPref = prefs.get("show-details", null);
				var details = caches.sorted.stream().filter(c -> c.name.equals(detailsPref)).findFirst().orElse(null);
				try (var table = new PlainTable("Caches")) {
					for (var entry : caches.sorted) {
						table.add("Status", entry.status.label).tone(entry.status.tone);
						boolean cancellable = entry.status == CacheStatus.QUEUED || entry.status == CacheStatus.RUNNING;
						var action = cancellable ? "Cancel" : status == CacheStatus.EMPTY ? "Populate" : "Refresh";
						table.add("Action", fragment.nest(entry.name)
							.run(() -> new LinkButton(action)
								.handle(cancellable ? entry.cache::cancel : entry.cache::refresh)
								.render())
							.content());
						if (entry.snapshot != null) {
							table.add("Size", Pretty.bytes().format(entry.snapshot.size()));
							table.add("Cost", entry.snapshot.cost);
							table.add("Updated", entry.snapshot.updated());
							table.add("Refreshed", entry.snapshot.refreshed());
						} else {
							table.add("Size", "");
							table.add("Cost", "");
							table.add("Updated", "");
							table.add("Refreshed", "");
						}
						table.add("Details", fragment.nest(entry.name)
							.run(() -> new LinkButton(details == entry ? "Hide" : "Show")
								.handle(() -> prefs.put("show-details", details == entry ? null : entry.name))
								.render())
							.content());
						table.add("Cache", entry.name).left();
					}
				}
				if (details != null) {
					var parameters = details.cache.input().parameters();
					if (!parameters.isEmpty()) {
						try (var table = new PlainTable("Parameters")) {
							for (var name : StreamEx.of(parameters.keySet()).sorted()) {
								table.add("Name", name).left();
								table.add("Value", parameters.get(name)).left();
							}
						}
					}
					if (!details.children.isEmpty()) {
						try (var table = new PlainTable("Dependencies")) {
							for (var child : details.children) {
								table.add("Status", child.status.label).tone(child.status.tone);
								table.add("Cache", child.name).left();
							}
						}
					}
				}
				var error = exception != null && !empty
					? new PersistedException(exception).getFormattedCause()
					: caches.sorted.stream()
						.filter(e -> e.snapshot != null)
						.map(e -> e.snapshot.exception())
						.filter(x -> x != null)
						.findFirst().orElse(null);
				if (error != null)
					SiteFragment.get().add(Html.pre().clazz("site-error").add(error));
			}
		}
		fragment.render();
	}
}
