// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.machinezoo.hookless.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;
import one.util.streamex.*;

@DraftApi
public class DataDialog {
	private final SiteSlot slot;
	private final Runnable runnable;
	public DataDialog(SiteSlot slot, Runnable runnable) {
		this.slot = slot;
		this.runnable = runnable;
	}
	private static class CachedContent {
		private final DomContent content;
		private final List<BlobCache.Dependency> dependencies;
		CachedContent(DomContent content, List<BlobCache.Dependency> dependencies) {
			this.content = content;
			this.dependencies = dependencies;
		}
	}
	private CachedContent evaluate() {
		try (SiteDialog dialog = new SiteDialog(slot)) {
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
					SiteDialog.out().add(slot.page().handle(ex));
				}
				/*
				 * Freezing speeds up repeated rendering. Cached content should be always frozen anyway.
				 */
				return new CachedContent(dialog.content().freeze(), tracker.dependencies());
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
		boolean shown = slot.preferences().getBoolean("show-dependencies", false);
		Dialog.notice(new DomFragment()
			.add("Content above relies on BLOB cache (")
			.add(Html.button()
				.id(slot.nested("show-dependencies").id())
				.clazz("link-button")
				.add(shown ? "hide" : "show")
				.onclick(() -> slot.preferences().putBoolean("show-dependencies", !shown)))
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
			SiteDialog.out().add(slot.page().handle(exception.get()));
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
			.id(slot.nested(cache.id.toString()).nested(title).id())
			.clazz("link-button")
			.add(title)
			.onclick(runnable);
	}
	public DomContent html() {
		/*
		 * Size-limited cache would be better for highly dynamic pages,
		 * but this class is currently used for pages with fixed set of cache dialogs,
		 * so we are going to be lazy and just memoize everything here.
		 * 
		 * Caching requires a repeatable key. Unfortunately, this cache depends on Runnable.
		 * We have no way to compare/hash Runnable instances.
		 * We instead require the slot to change whenever Runnable changes.
		 * In practice, the Runnable cannot really depend on anything. It must be a method reference.
		 * 
		 * We will use two cache layers, one for the content itself and one for everything including cache table.
		 * This arrangement will avoid content refresh when we just want to update cache refresh progress.
		 */
		Supplier<CachedContent> inner = slot.local(DataDialog.class.getSimpleName() + "-inner", () -> new ReactiveLazy<>(this::evaluate));
		Supplier<DomContent> assembler = () -> {
			try (SiteDialog dialog = new SiteDialog(slot)) {
				/*
				 * Here we chain the two caches together. This might look like incorrect capture of local variable,
				 * but SiteSlot.local() always returns the same value, because it effectively acts as a global variable,
				 * which is safe to reference from supplier of the outer cache.
				 */
				CachedContent cached = inner.get();
				SiteDialog.out().add(cached.content);
				table(cached.dependencies);
				/*
				 * Freezing speeds up repeated rendering. Cached content should be always frozen anyway.
				 */
				return dialog.content().freeze();
			}
		};
		Supplier<DomContent> outer = slot.local(DataDialog.class.getSimpleName() + "-outer", () -> new ReactiveLazy<>(assembler));
		return outer.get();
	}
	/*
	 * Binding is actually the typical usage.
	 */
	public static SiteBinding binding(String name, Runnable runnable) {
		return SiteBinding.block(name, context -> new DataDialog(context.page().slot(name), runnable).html());
	}
}
