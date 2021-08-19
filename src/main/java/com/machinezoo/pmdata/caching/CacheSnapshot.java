// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import static java.util.stream.Collectors.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.commons.lang3.exception.*;
import org.slf4j.*;
import com.google.gson.*;

public class CacheSnapshot<T extends CacheFile> {
	private static final Logger logger = LoggerFactory.getLogger(CacheSnapshot.class);
	private final CacheOwner<T> owner;
	public PersistentCache<T> cache() {
		return owner.cache;
	}
	private T data;
	public T data() {
		return data;
	}
	public T get() {
		if (data == null) {
			if (exception != null)
				throw new CachedException(exception);
			if (cancelled)
				throw new CancellationException();
		}
		return data;
	}
	public Path path() {
		return data != null ? data.path() : null;
	}
	/*
	 * This may be present even if get() succeeds, because failing refresh does not erase last good value.
	 */
	private String exception;
	public String exception() {
		return exception;
	}
	/*
	 * This may be present in addition to valid data and/or exception. It is used to persist cancellation,
	 * so that cancelled refreshes are not automatically retried every time the program is restarted.
	 */
	private boolean cancelled;
	public boolean cancelled() {
		return cancelled;
	}
	/*
	 * Hash of the input as observed by the cache during the last refresh attempt whether successful or not.
	 * Sometimes refresh attempt fails before input hash is known. In that case this field will be set to some fallback value.
	 */
	private String input;
	public String input() {
		return input;
	}
	/*
	 * Hash is null if this snapshot contains only exception and no data.
	 */
	private String hash;
	public String hash() {
		return hash;
	}
	private long size;
	public long size() {
		return size;
	}
	private Instant updated;
	public Instant updated() {
		return updated;
	}
	private Instant refreshed;
	public Instant refreshed() {
		return refreshed;
	}
	/*
	 * How long did it take to refresh the cache last time.
	 */
	private Duration cost;
	public Duration cost() {
		return cost;
	}
	private CacheSnapshot(CacheOwner<T> owner) {
		this.owner = owner;
	}
	private static class Saved {
		/*
		 * The ID is just informative. We want it in the JSON file, but we don't use it.
		 */
		@SuppressWarnings("unused")
		String id;
		String path;
		String exception;
		boolean cancelled;
		String input;
		String hash;
		long size;
		long updated;
		long refreshed;
		long cost;
	}
	static <T extends CacheFile> CacheSnapshot<T> load(CacheOwner<T> owner) {
		var directory = CacheFiles.directory(owner.cache);
		var json = directory.resolve("cache.json");
		if (!Files.isRegularFile(json))
			return null;
		try {
			var saved = new Gson().fromJson(Files.readString(json), Saved.class);
			var snapshot = new CacheSnapshot<T>(owner);
			snapshot.exception = saved.exception;
			snapshot.cancelled = saved.cancelled;
			Objects.requireNonNull(saved.input);
			snapshot.input = saved.input;
			if ((saved.hash != null) != (saved.path != null))
				throw new IllegalStateException();
			snapshot.hash = saved.hash;
			if (saved.size < 0)
				throw new IllegalStateException();
			snapshot.size = saved.size;
			snapshot.updated = Instant.ofEpochMilli(saved.updated);
			snapshot.refreshed = Instant.ofEpochMilli(saved.refreshed);
			snapshot.cost = Duration.ofMillis(saved.cost);
			if (saved.path != null)
				snapshot.data = owner.cache.cacheFormat().load(directory.resolve(Paths.get(saved.path)));
			try (var listing = Files.list(directory)) {
				for (var junk : listing.collect(toList())) {
					if (!junk.equals(json) && (snapshot.data == null || !junk.equals(snapshot.data.path()))) {
						try {
							CacheFiles.remove(junk);
						} catch (Throwable ex) {
							logger.warn("Unable to remove stale cache data in {}.", junk, ex);
						}
					}
				}
			}
			return snapshot;
		} catch (Throwable ex) {
			logger.warn("Ignoring invalid cache metadata in {}.", json, ex);
			return null;
		}
	}
	public static <T extends CacheFile> CacheSnapshot<T> of(PersistentCache<T> cache) {
		return CacheOwner.of(cache).snapshot.get();
	}
	private void save() {
		var directory = CacheFiles.directory(owner.cache);
		var path = directory.resolve("cache.json");
		try {
			var saved = new Saved();
			saved.id = owner.cache.toString();
			saved.path = data != null ? data.path().toString() : null;
			saved.exception = exception;
			saved.cancelled = cancelled;
			saved.input = input;
			saved.hash = hash;
			saved.size = size;
			saved.updated = updated.toEpochMilli();
			saved.refreshed = refreshed.toEpochMilli();
			saved.cost = cost.toMillis();
			Files.createDirectories(directory);
			Files.writeString(path, new GsonBuilder()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.create()
				.toJson(saved));
		} catch (Throwable ex) {
			logger.error("Unable to save cache metadata in {}.", path, ex);
		}
	}
	static <T extends CacheFile> void update(CacheOwner<T> owner, T data, CacheInput input, Instant started) {
		Objects.requireNonNull(data);
		var variable = owner.snapshot;
		var previous = variable.get();
		data.commit();
		var next = new CacheSnapshot<T>(owner);
		next.data = data;
		next.input = input.hash();
		next.hash = CacheFiles.hash(data.path());
		next.size = CacheFiles.size(data.path());
		next.refreshed = Instant.now();
		next.cost = Duration.between(started, next.refreshed);
		next.updated = previous != null && Objects.equals(previous.hash, next.hash) ? previous.updated : next.refreshed;
		variable.set(next);
		/*
		 * Even if the write fails, in-memory state is already updated.
		 * This is important to prevent automated cache refresh from being re-triggered.
		 */
		next.save();
	}
	static <T extends CacheFile> void update(CacheOwner<T> owner, Throwable exception, CacheInput input, Instant started) {
		var cancelled = ExceptionUtils.getThrowableList(exception).stream().anyMatch(x -> x instanceof CancellationException);
		var variable = owner.snapshot;
		var previous = variable.get();
		var next = new CacheSnapshot<T>(owner);
		if (!cancelled)
			next.exception = new CachedException(exception).getFormattedCause();
		else if (previous != null)
			next.exception = previous.exception;
		next.cancelled = cancelled;
		next.refreshed = Instant.now();
		/*
		 * CacheInput comes from linker, which checks that calling hash() is exception-free.
		 * We never reuse input from previous snapshot, because then we wouldn't know
		 * when to refresh failing or cancelled caches.
		 */
		next.input = input != null ? input.hash() : new CacheInput().hash();
		if (previous != null) {
			next.data = previous.data;
			next.hash = previous.hash;
			next.size = previous.size;
			next.updated = previous.updated;
			next.cost = previous.cost;
		} else {
			if (started != null)
				next.cost = Duration.between(started, next.refreshed);
			else
				next.cost = Duration.ZERO;
			next.updated = next.refreshed;
		}
		variable.set(next);
		next.save();
	}
}
