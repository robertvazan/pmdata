// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import static java.util.stream.Collectors.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.regex.*;
import org.apache.commons.lang3.exception.*;
import org.slf4j.*;
import com.google.common.hash.*;
import com.google.gson.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.util.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.utils.*;
import com.machinezoo.stagean.*;

@DraftApi("update() in addition to supply() that takes old CacheData as its parameter (may return the same)")
public class CacheState<T extends CacheFile> {
	private static final Logger logger = LoggerFactory.getLogger(CacheState.class);
	private final CacheFormat<T> format;
	public CacheState(CacheFormat<T> format) {
		this.format = format;
	}
	/*
	 * Optimization to allow fast collection of dependencies.
	 */
	private final int hashCode = ThreadLocalRandom.current().nextInt();
	@Override
	public int hashCode() {
		return hashCode;
	}
	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}
	private boolean defined;
	private void configure() {
		if (defined)
			throw new IllegalStateException("Cache definition cannot be changed anymore.");
	}
	private Object id;
	public Object id() {
		return id;
	}
	public synchronized CacheState<T> id(Object id) {
		Objects.requireNonNull(id);
		configure();
		this.id = id;
		return this;
	}
	private CachePolicy policy = new CachePolicy();
	public synchronized CachePolicy policy() {
		return policy.clone();
	}
	public synchronized CacheState<T> policy(CachePolicy policy) {
		Objects.requireNonNull(policy);
		if (policy.period() != null && policy.mode() != CacheRefreshMode.AUTOMATIC)
			throw new IllegalArgumentException("Period makes sense only with automatic refresh.");
		configure();
		this.policy = policy.clone();
		return this;
	}
	private Runnable linker = () -> {
	};
	public synchronized CacheState<T> link(Runnable linker) {
		Objects.requireNonNull(linker);
		configure();
		this.linker = linker;
		return this;
	}
	private Supplier<T> supplier;
	public synchronized CacheState<T> supply(Supplier<T> supplier) {
		Objects.requireNonNull(supplier);
		configure();
		this.supplier = supplier;
		return this;
	}
	private static final Pattern filenameRe = Pattern.compile("^[a-zA-Z0-9._-]+");
	private static String base64(byte[] data) {
		return Base64.getUrlEncoder().encodeToString(data).replace("=", "");
	}
	static String hashId(String text) {
		return base64(Hashing.sha256().hashString(text, StandardCharsets.UTF_8).asBytes());
	}
	private Path directory() {
		var path = SiteFiles.cacheOf(CacheState.class.getSimpleName());
		var name = id.toString();
		var matcher = filenameRe.matcher(name);
		if (matcher.find())
			path = path.resolve(matcher.group());
		return path.resolve(hashId(name));
	}
	private static class Metadata {
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
	@DraftCode("support data directories besides data files")
	private CacheSnapshot<T> load() {
		var path = directory().resolve("cache.json");
		if (!Files.isRegularFile(path))
			return null;
		try {
			var metadata = new Gson().fromJson(Files.readString(path), Metadata.class);
			var snapshot = new CacheSnapshot<T>();
			snapshot.exception = metadata.exception;
			snapshot.cancelled = metadata.cancelled;
			Objects.requireNonNull(metadata.input);
			snapshot.input = metadata.input;
			if ((metadata.hash != null) != (metadata.path != null))
				throw new IllegalStateException();
			snapshot.hash = metadata.hash;
			if (metadata.size < 0)
				throw new IllegalStateException();
			snapshot.size = metadata.size;
			snapshot.updated = Instant.ofEpochMilli(metadata.updated);
			snapshot.refreshed = Instant.ofEpochMilli(metadata.refreshed);
			snapshot.cost = Duration.ofMillis(metadata.cost);
			if (metadata.path != null)
				snapshot.data = format.load(directory().resolve(Paths.get(metadata.path)));
			try (var listing = Files.list(directory())) {
				for (var junk : listing.collect(toList())) {
					if (!junk.equals(path) && (snapshot.data == null || !junk.equals(snapshot.data.path()))) {
						if (Files.isRegularFile(junk)) {
							try {
								Files.delete(junk);
							} catch (Throwable ex) {
								logger.warn("Unable to remove stale cache data in {}.", path, ex);
							}
						}
					}
				}
			}
			return snapshot;
		} catch (Throwable ex) {
			logger.warn("Ignoring invalid cache metadata in {}.", path, ex);
			return null;
		}
	}
	private void save(CacheSnapshot<T> snapshot) {
		var path = directory().resolve("cache.json");
		try {
			var metadata = new Metadata();
			metadata.id = id.toString();
			metadata.path = snapshot.data != null ? snapshot.data.path().toString() : null;
			metadata.exception = snapshot.exception;
			metadata.cancelled = snapshot.cancelled;
			metadata.input = snapshot.input;
			metadata.hash = snapshot.hash;
			metadata.size = snapshot.size;
			metadata.updated = snapshot.updated.toEpochMilli();
			metadata.refreshed = snapshot.refreshed.toEpochMilli();
			metadata.cost = snapshot.cost.toMillis();
			Files.createDirectories(directory());
			Files.writeString(path, new GsonBuilder()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.create()
				.toJson(metadata));
		} catch (Throwable ex) {
			logger.error("Unable to save cache metadata in {}.", path, ex);
		}
	}
	private final ReactiveVariable<CacheSnapshot<T>> snapshot = OwnerTrace
		.of(new ReactiveVariable<CacheSnapshot<T>>())
		.parent(this)
		.tag("role", "snapshot")
		.target();
	/*
	 * Duplicate cache IDs are going to be common due to copy-pasting of code fragments. We have to detect it.
	 */
	private static Set<Object> allIds = new HashSet<>();
	/*
	 * Check and freeze the defining properties declared above.
	 * This is called automatically when the cache is first used.
	 * It can be called explicitly if the app wishes to check cache definitions early.
	 */
	public synchronized CacheState<T> define() {
		if (!defined) {
			Objects.requireNonNull(id, "Cache ID must be configured.");
			Objects.requireNonNull(supplier, "Supplier must be configured.");
			synchronized (allIds) {
				if (allIds.contains(id))
					throw new IllegalStateException("Duplicate cache ID.");
				allIds.add(id);
			}
			snapshot.set(load());
			defined = true;
			OwnerTrace.of(this).tag("id", id);
		}
		return this;
	}
	private CacheInput link() {
		var input = new CacheInput();
		try (var recording = input.record()) {
			try {
				linker.run();
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
		define();
		return input.get();
	}
	@DraftCode("support data directories besides data files")
	private CacheSnapshot<T> evaluate(CacheInput input, CacheSnapshot<T> previous) {
		var start = Instant.now();
		try (
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
				var outputScope = CacheOutput.advertise(directory())) {
			try {
				var next = new CacheSnapshot<T>();
				next.data = supplier.get();
				Objects.requireNonNull(next.data);
				/*
				 * Any reactive blocking means the data is not up to date even if input hash matches.
				 */
				if (CurrentReactiveScope.blocked())
					throw new ReactiveBlockingException();
				next.input = input.hash();
				var path = next.data.path();
				if (Files.isRegularFile(path)) {
					var hasher = Exceptions.sneak().get(() -> MessageDigest.getInstance("SHA-256"));
					Exceptions.sneak().run(() -> {
						try (var stream = Files.newInputStream(path)) {
							byte[] buffer = new byte[4096];
							while (true) {
								int amount = stream.read(buffer);
								if (amount <= 0)
									break;
								next.size += amount;
								hasher.digest(buffer, 0, amount);
							}
						}
					});
					next.hash = base64(hasher.digest());
				} else
					throw new IllegalStateException("Cannot find persistent data file.");
				next.refreshed = Instant.now();
				next.cost = Duration.between(start, next.refreshed);
				next.updated = previous != null && Objects.equals(previous.hash, next.hash) ? previous.updated : next.refreshed;
				return next;
			} catch (Throwable ex) {
				var next = new CacheSnapshot<T>();
				next.exception = new CachedException(ex).getFormattedCause();
				next.refreshed = Instant.now();
				if (previous != null) {
					next.data = previous.data;
					next.input = previous.input;
					next.hash = previous.hash;
					next.size = previous.size;
					next.updated = previous.updated;
					next.cost = previous.cost;
				} else {
					next.input = input.hash();
					next.cost = Duration.between(start, next.refreshed);
					next.updated = next.refreshed;
				}
				return next;
			}
		}
	}
	/*
	 * Result of the last supplier run. Null if there is none. EmptyCacheException is only thrown by get() below.
	 */
	public CacheSnapshot<T> snapshot() {
		define();
		return snapshot.get();
	}
	/*
	 * Convenient access to commonly used snapshot methods, mediated through CacheInput.
	 */
	public T get() {
		var snapshot = CacheInput.get().snapshot(this);
		if (snapshot == null) {
			if (policy.blocking())
				CurrentReactiveScope.block();
			throw new EmptyCacheException();
		}
		return snapshot.get();
	}
	/*
	 * This has side effects like get() but without returning cached value or throwing.
	 * It is intended to be used in linkers. It might be faster than get().
	 */
	public void touch() {
		CacheInput.get().snapshot(this);
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
		define();
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
		.owner(CacheState.class)
		.hardwareParallelism()
		.lowestPriority()
		.executor();
	private static final ReadWriteLock exclusivity = new ReentrantReadWriteLock();
	@DraftCode("support data directories besides data files")
	private static void hash(Path path, CacheSnapshot<?> into) {
		if (Files.isRegularFile(path)) {
			var hasher = Exceptions.sneak().get(() -> MessageDigest.getInstance("SHA-256"));
			Exceptions.sneak().run(() -> {
				try (var stream = Files.newInputStream(path)) {
					byte[] buffer = new byte[4096];
					while (true) {
						int amount = stream.read(buffer);
						if (amount <= 0)
							break;
						into.size += amount;
						hasher.update(buffer, 0, amount);
					}
				}
			});
			into.hash = Base64.getMimeEncoder().encodeToString(hasher.digest());
		} else
			throw new IllegalStateException("Cannot find persistent data file.");
	}
	public synchronized void refresh() {
		define();
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
			executor.submit(() -> {
				try {
					var previous = snapshot.get();
					CacheInput input = null;
					try {
						goal.stage("Exclusivity");
						var lock = policy.exclusive() ? exclusivity.writeLock() : exclusivity.readLock();
						lock.lock();
						try {
							var next = new CacheSnapshot<T>();
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
									var outputScope = CacheOutput.advertise(directory())) {
								next.data = supplier.get();
								/*
								 * Any reactive blocking means the data is not up to date even if input hash matches.
								 */
								if (CurrentReactiveScope.blocked())
									throw new ReactiveBlockingException();
							}
							Objects.requireNonNull(next.data);
							next.data.commit();
							next.input = input.hash();
							hash(next.data.path(), next);
							next.refreshed = Instant.now();
							next.cost = Duration.between(started.get(), next.refreshed);
							next.updated = previous != null && Objects.equals(previous.hash, next.hash) ? previous.updated : next.refreshed;
							snapshot.set(next);
							/*
							 * Even if the write fails, in-memory state is already updated.
							 * This is important to prevent automated cache refresh from being re-triggered.
							 */
							save(next);
							logger.info("Refreshed {}.", this);
						} finally {
							lock.unlock();
						}
					} catch (Throwable ex) {
						/*
						 * There's a lot of code in this exception handler, which creates risk of secondary exceptions.
						 * There's however no code here that could throw under normal circumstances.
						 */
						var cancelled = ExceptionUtils.getThrowableList(ex).stream().anyMatch(x -> x instanceof CancellationException);
						/*
						 * We want to know about unexpected failures. That naturally excludes cancellations, which are triggered by the user.
						 */
						if (!cancelled)
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
							if (progress.get() == goal) {
								var next = new CacheSnapshot<T>();
								if (!cancelled)
									next.exception = new CachedException(ex).getFormattedCause();
								else if (previous != null)
									next.exception = previous.exception;
								next.cancelled = cancelled;
								next.refreshed = Instant.now();
								if (previous != null) {
									next.data = previous.data;
									next.input = previous.input;
									next.hash = previous.hash;
									next.size = previous.size;
									next.updated = previous.updated;
									next.cost = previous.cost;
								} else {
									/*
									 * CacheInput comes from linker, which checks that calling hash() is exception-free.
									 */
									next.input = input != null ? input.hash() : new CacheInput().hash();
									if (started.get() != null)
										next.cost = Duration.between(started.get(), next.refreshed);
									else
										next.cost = Duration.ZERO;
									next.updated = next.refreshed;
								}
								snapshot.set(next);
								/*
								 * Safe to call, because exceptions are handled inside the method.
								 */
								save(next);
							}
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
			});
		}
	}
	@Override
	public synchronized String toString() {
		return Optional.ofNullable(id()).orElse(CacheState.class.getSimpleName()).toString();
	}
}
