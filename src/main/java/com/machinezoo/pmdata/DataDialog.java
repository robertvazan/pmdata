// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.common.cache.*;
import com.machinezoo.hookless.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;
import one.util.streamex.*;

@DraftApi
public class DataDialog {
	private final SiteFragment.Key key;
	private final Runnable runnable;
	private DataDialog(Runnable runnable) {
		this.key = SiteFragment.get().key();
		this.runnable = runnable;
	}
	private static class CachedContent {
		private final SiteFragment fragment;
		private final List<BlobCache.Dependency> dependencies;
		CachedContent(SiteFragment fragment, List<BlobCache.Dependency> dependencies) {
			this.fragment = fragment;
			this.dependencies = dependencies;
		}
	}
	private CachedContent evaluate() {
		var fragment = SiteFragment.forKey(key);
		try (var scope = fragment.open()) {
			try (var tracker = new BlobCache.Tracker()) {
				try {
					/*
					 * This would be a great place to call SiteReload.watch().
					 * That would however cause all cached dialogs to refresh
					 * whenever XML template text changes, defeating the purpose of caching.
					 * We will therefore require refresh in the browser
					 * to force cached dialog refresh when code changes.
					 */
					runnable.run();
				} catch (BlobCache.EmptyCacheException ex) {
					Dialog.fail("Some content depends on uninitialized BLOB cache.");
				} catch (Throwable ex) {
					/*
					 * We want to catch the exception here, so that cache UI is not affected by content exceptions.
					 * UI for persistent cache, for example, must remain visible in case of exception,
					 * because cache refresh may be needed to resolve the exception.
					 */
					Dialog.fail("Exception was thrown while generating content.");
					fragment.add(fragment.page().handle(ex));
				}
				return new CachedContent(fragment, tracker.dependencies());
			}
		}
	}
	private void sort(List<BlobCache> sorted, BlobCache next) {
		if (!sorted.contains(next)) {
			for (var dependency : StreamEx.of(next.dependencies()).append(next.blockers()))
				sort(sorted, dependency.cache());
			sorted.add(next);
		}
	}
	private void table(List<BlobCache.Dependency> dependencies) {
		if (dependencies.isEmpty())
			return;
		var prefs = SiteFragment.get().preferences();
		boolean shown = prefs.getBoolean("show-dependencies", false);
		Dialog.notice(new DomFragment()
			.add("Content above relies on BLOB cache (")
			.add(Html.button()
				.id(SiteFragment.get().elementId("show-dependencies"))
				.clazz("link-button")
				.add(shown ? "hide" : "show")
				.onclick(() -> prefs.putBoolean("show-dependencies", !shown)))
			.add(")."));
		var caches = new ArrayList<BlobCache>();
		for (var dependency : dependencies)
			sort(caches, dependency.cache());
		if (caches.stream().flatMap(c -> c.dependencies().stream()).anyMatch(d -> !d.fresh()))
			Dialog.warn("Some BLOB caches are stale.");
		if (shown) {
			try (var table = new Dialog.Table("Cache dependencies")) {
				for (var cache : caches)
					row(table, cache);
			}
		}
		var exception = StreamEx.of(caches)
			.map(c -> c.exception())
			.filter(ex -> !(ex instanceof CancellationException) && !(ex instanceof ReactiveBlockingException))
			.nonNull()
			.findFirst();
		if (exception.isPresent()) {
			Dialog.fail("At least one cache has failed to refresh.");
			SiteFragment.get().add(SiteFragment.get().page().handle(exception.get()));
		}
	}
	private void row(Dialog.Table table, BlobCache cache) {
		table.add("Cache", cache.id).left();
		table.add("Updated", cache.updated());
		table.add("Refreshed", cache.refreshed());
		var path = cache.path();
		var size = path == null ? 0 : Exceptions.sneak().get(() -> Files.size(path));
		table.add("Size", Pretty.bytes(size));
		var hash = cache.hash();
		switch (cache.state()) {
		case IDLE:
			if (cache.exception() != null) {
				if (cache.exception() instanceof CancellationException)
					table.add("Status", Html.span().clazz("maybe").add("Cancelled"));
				else if (cache.exception() instanceof ReactiveBlockingException)
					table.add("Status", Html.span().clazz("no").add("Blocked"));
				else
					table.add("Status", Html.span().clazz("no").add("Failed"));
			} else if (hash == null)
				table.add("Status", Html.span().clazz("maybe").add("Empty"));
			else {
				if (cache.dependencies().stream().allMatch(d -> d.fresh()))
					table.add("Status", "Ready");
				else
					table.add("Status", Html.span().clazz("maybe").add("Stale"));
			}
			break;
		case SCHEDULED:
			table.add("Status", "Starting...");
			break;
		case RUNNING:
			var progress = cache.progress();
			table.add("Status", progress != null ? progress : "Refreshing...");
			break;
		case CANCELLING:
			table.add("Status", "Cancelling...");
			break;
		}
		switch (cache.state()) {
		case IDLE:
			if (cache.defined())
				table.add("Control", button(cache, hash == null ? "Populate" : "Refresh", cache::refresh));
			break;
		case SCHEDULED:
		case RUNNING:
			table.add("Control", button(cache, "Cancel", cache::cancel));
			break;
		default:
			break;
		}
	}
	private DomContent button(BlobCache cache, String title, Runnable runnable) {
		return Html.button()
			.id(SiteFragment.get().elementId(cache.id.toString(), title))
			.clazz("link-button")
			.add(title)
			.onclick(runnable);
	}
	/*
	 * Caching requires a repeatable key. Unfortunately, this cache depends on Runnable.
	 * We have no way to compare/hash Runnable instances.
	 * We instead require fragment key to change whenever Runnable changes.
	 * In practice, the Runnable cannot really depend on anything. It must be a method reference.
	 * 
	 * We will use two cache layers, one for the content itself and one for everything including cache table.
	 * This arrangement will avoid content refresh when we just want to update cache refresh progress.
	 */
	private static final Cache<SiteFragment.Key, ReactiveLazy<CachedContent>> innerCache = CacheBuilder.newBuilder()
		/*
		 * Ideally, we would like to keep entries referenced by outer cache,
		 * but since that's not possible, we will just duplicate outer cache configuration.
		 */
		.expireAfterAccess(1, TimeUnit.MINUTES)
		.maximumSize(100)
		.build();
	private SiteFragment assemble() {
		var fragment = SiteFragment.forKey(key);
		try (var scope = fragment.open()) {
			CachedContent inner = Exceptions.sneak().get(() -> innerCache.get(key, () -> new ReactiveLazy<>(this::evaluate))).get();
			inner.fragment.render();
			table(inner.dependencies);
		}
		return fragment;
	}
	private static final Cache<SiteFragment.Key, ReactiveLazy<SiteFragment>> outerCache = CacheBuilder.newBuilder()
		/*
		 * This is a compute cache. Assuming the computations may be expensive, consuming up to a second of compute time,
		 * it is reasonable to cache output for a minute. We might want to make this configurable via properties.
		 */
		.expireAfterAccess(1, TimeUnit.MINUTES)
		/*
		 * Cached data may be large, perhaps containing embedded images. We should assume it might be up to 1MB large.
		 * In that case, it is reasonable to default to a fairly low size limit, for example 100.
		 * Reasonable size limit varies a lot with traffic and system configuration.
		 */
		.maximumSize(100)
		.build();
	private void render() {
		Exceptions.sneak().get(() -> outerCache.get(key, () -> new ReactiveLazy<>(this::assemble))).get().render();
	}
	/*
	 * Binding is actually the typical usage.
	 */
	public static SiteBinding binding(String name, Runnable runnable) {
		return SiteFragment.binding(name, () -> new DataDialog(runnable).render());
	}
}
