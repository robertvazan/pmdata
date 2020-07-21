// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import static java.util.stream.Collectors.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.apache.commons.lang3.tuple.*;
import org.slf4j.*;
import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.*;
import com.google.gson.*;
import com.machinezoo.hookless.*;
import com.machinezoo.hookless.util.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.stagean.*;
import one.util.streamex.*;

/*
 * This cache is intended for expensive computations (taking seconds to minutes).
 * It can handle large amounts of output data by saving cached values as BLOBs in files.
 * 
 * Heavy computations cannot be fully reactive, because hookless assumes that computations are cheap.
 * Making this cache fully reactive would cause undesirable avalanches to cache refreshes.
 * Cached computation is nevertheless executed in reactive scope, so that reactive freezes work.
 * Captured reactive dependencies are discarded though. Reactive blocking is treated as an exception.
 * 
 * Due to the abundance of static methods and their interactions with instance methods,
 * synchronization is always global on the class level even in instance methods.
 */
@DraftApi("separate progress & cancellation, separate dependencies, MapDB-backed")
public class BlobCache {
	/*
	 * We need some sort of serializable cache identifier, so that we can find the cache after application restart.
	 */
	public final CacheId id;
	private BlobCache(CacheId id) {
		this.id = id;
		OwnerTrace.of(this)
			.tag("id", id);
		OwnerTrace.of(content)
			.parent(this)
			.tag("role", "content");
		OwnerTrace.of(state)
			.parent(this)
			.tag("role", "state");
		OwnerTrace.of(progress)
			.parent(this)
			.tag("role", "progress");
		OwnerTrace.of(exception)
			.parent(this)
			.tag("role", "exception");
		OwnerTrace.of(blockers)
			.parent(this)
			.tag("role", "blockers");
	}
	/*
	 * There is a global list of known BLOB caches. Once instantiated, cache object lives forever.
	 * This list is populated initially by loading state JSON.
	 * Additions are then made lazily when particular cache object is requested.
	 * Source code can therefore contain many more (potentially active) caches than this list.
	 */
	private static final Map<CacheId, BlobCache> all = new HashMap<>();
	public static synchronized BlobCache get(CacheId id) {
		BlobCache cache = all.get(id);
		if (cache == null)
			all.put(id, cache = new BlobCache(id));
		return cache;
	}
	/*
	 * Every fully initialized cache has a supplier that can regenerate cache content.
	 * Supplier may be null in caches loaded from JSON or referenced as dependencies from other caches.
	 * 
	 * Single supplier returning byte[] supports only batches with single file output.
	 * This is a bit limiting, but we want to encourage packing output in a single file,
	 * because it simplifies not only this cache but also communication of all datasets.
	 * 
	 * The supplier has to materialize all output in RAM. This limits output to a few GB.
	 * If the code is to run on a VPS, output is practically limited to about 100MB.
	 * In the future, we might want to also offer API based on Consumer<OutputStream>.
	 * 
	 * Since BLOB cache heavily uses hashes to detect data changes,
	 * supplier output should be reproducible for the cache to work well.
	 * 
	 * Supplier is executed in reactive context, so that reactive freezes work,
	 * but reactive dependencies are discarded. Reactive blocking is treated as exception.
	 * Exceptions are not cached. They only change transient state for UI integration.
	 * 
	 * Supplier reference is volatile, so that we don't have to bother with locking around it.
	 */
	private volatile Supplier<byte[]> supplier;
	private final ReactiveVariable<Boolean> defined = new ReactiveVariable<>(false);
	/*
	 * Code that controls the cache will usually use only the following method.
	 * Supplier should be a lambda. Call of this method should envelop the actual data generator.
	 */
	public static BlobCache get(CacheId id, Supplier<byte[]> supplier) {
		BlobCache cache = get(id);
		if (supplier != null && cache.supplier == null) {
			cache.supplier = supplier;
			cache.defined.set(true);
		}
		return cache;
	}
	/*
	 * We will offer a convenience API that uses BLOB cache to serialize single large object.
	 * Since this API allows only deserialization of all data at once, it should be limited to relatively smaller objects.
	 * 
	 * We will use kryo for serialization, because it doesn't require type information like JSON/CBOR
	 * and because it is fast, compact, compresses well, and it is maintained.
	 * We will not compress data at the moment, because it can actually slow everything down. Disk space is plentiful.
	 * 
	 * Kryo is not thread-safe. We will give every thread its own instance.
	 * We don't want to recreate the instance during every serialization, because kryo caches a lot of type information.
	 */
	private static final ThreadLocal<Kryo> kryos = ThreadLocal.withInitial(() -> {
		Kryo kryo = new Kryo();
		/*
		 * Kryo requires class registration by default for security reasons, which are irrelevant for local cache.
		 */
		kryo.setRegistrationRequired(false);
		/*
		 * Special-case pairs that are used as part of the bimap. Should probably allow configuration of custom serializers in the future.
		 */
		kryo.addDefaultSerializer(Pair.class, CacheSerializers.PairSerializer.class);
		return kryo;
	});
	@SuppressWarnings("unchecked")
	public static <T> T of(CacheId id, Supplier<T> supplier) {
		/*
		 * We want to freeze the parsed output to avoid the cost of repeated parsing.
		 */
		return FreezeCache.of(id.then(BlobCache.class), () -> {
			var cache = get(id, () -> {
				Output output = new Output(256, -1);
				kryos.get().writeClassAndObject(output, supplier.get());
				return output.toBytes();
			});
			cache.access();
			var bytes = Exceptions.sneak().get(() -> Files.readAllBytes(cache.path()));
			Input input = new Input(bytes);
			/*
			 * Unavoidable unchecked cast, but the way this class is typically used makes it safe.
			 */
			return (T)kryos.get().readClassAndObject(input);
		});
	}
	/*
	 * Map caches have the nice property of allowing indirection,
	 * so we can return standard interface that can be assigned to static variable
	 * while loading the cache into memory only when it is needed.
	 * 
	 * The main of() function would have to return Supplier to do the same trick,
	 * which complicates the API and the calling code too much.
	 */
	public static <K, V> MapCache<K, V> map(CacheId id, Supplier<Map<K, V>> supplier) {
		return MapCache.lazy(() -> MapCache.of(of(id, supplier)));
	}
	public static <K1, K2, V> BiMapCache<K1, K2, V> bimap(CacheId id, Supplier<Map<Pair<K1, K2>, V>> supplier) {
		return BiMapCache.lazy(() -> BiMapCache.of(of(id, supplier)));
	}
	/*
	 * The downside of defining caches this way is that caches loaded from JSON will not have the supplier.
	 * Such caches cannot be refreshed until they are accessed directly, usually by visiting their homepage.
	 * In order to build usable UI, the UI code must know whether the cache can be refreshed,
	 * i.e. whether it has a supplier.
	 */
	public boolean defined() {
		return defined.get();
	}
	/*
	 * Besides cache identity and content hash, we also keep some metadata that can be displayed in the UI.
	 */
	private static class Content {
		byte[] hash;
		/*
		 * Record both when the data (or its hash) last changed and when we performed last cache refresh.
		 * The latter lets us assess whether the cache could be stale.
		 * The former lets us see whether the last refresh resulted in a change.
		 */
		Instant updated;
		Instant refreshed;
		/*
		 * We are actually tracking full dependencies (including hash) rather than just caches.
		 * We even allow recording the same cache multiple times in case its hash changes
		 * while this cache's batch is running. Such cache is always considered to be out of date.
		 */
		List<Dependency> dependencies = Collections.emptyList();
	}
	/*
	 * Make the dependency reference equality-comparable. This helps with Tracker implementation below,
	 * but it may be also useful to calling code that is trying to process dependency lists.
	 */
	public static class Dependency {
		public final CacheId id;
		/*
		 * Keep hashes of dependencies, so that we can indicate outdated dependent caches in the UI.
		 */
		public final byte[] hash;
		public Dependency(CacheId id, byte[] hash) {
			this.id = id;
			this.hash = hash;
		}
		public BlobCache cache() {
			return get(id);
		}
		public boolean fresh() {
			return Arrays.equals(hash, cache().hash());
		}
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Dependency))
				return false;
			var other = (Dependency)obj;
			return Objects.equals(id, other.id) && Arrays.equals(hash, other.hash);
		}
		@Override
		public int hashCode() {
			return Arrays.deepHashCode(new Object[] { id, hash });
		}
	}
	/*
	 * Any change to this variable, even reassigning the same value, will invalidate dependent computations.
	 * We don't care, because changes are rare and only relatively cheap UI code should be dependent on this.
	 */
	private final ReactiveVariable<Content> content = new ReactiveVariable<>(new Content());
	public byte[] hash() {
		return content.get().hash;
	}
	public boolean available() {
		return hash() != null;
	}
	public Instant updated() {
		return content.get().updated;
	}
	public Instant refreshed() {
		return content.get().refreshed;
	}
	public List<Dependency> dependencies() {
		return Collections.unmodifiableList(content.get().dependencies);
	}
	/*
	 * Access timestamp is non-reactive, because we do not expose it via API.
	 * We only use it internally to expire and delete unused caches.
	 * 
	 * Initialize it to current time, which is correct for newly created caches.
	 * Caches loaded from JSON file will have their access time overwritten during loading.
	 */
	private Instant accessed = Instant.now();
	/*
	 * State of all BLOB caches is stored in one JSON file "index.json".
	 * JSON file is read before first cache is used and saved after every change.
	 * 
	 * Data files are in the same directory named "<hash>.dat".
	 * If two caches have the same output for some reason, they use the same data file.
	 * Data file is kept around only as long as it is referenced by at least one cache in the JSON file.
	 * 
	 * Use SiteFiles for cache location, so that location of all caches can be configured globally.
	 */
	private static final Path directory = SiteFiles.cacheOf(BlobCache.class.getSimpleName());
	private static final Path indexPath = directory.resolve("index.json");
	private static Path path(byte[] hash) {
		return directory.resolve(Base64.getUrlEncoder().encodeToString(hash).replace("_", "").replace("-", "").replace("=", ""));
	}
	private static void requireJson(boolean condition) {
		if (!condition)
			throw new IllegalStateException("Malformed BLOB cache index.");
	}
	private static class JsonDependency {
		String[] id;
		String hash;
		void validate() {
			requireJson(id != null && id.length > 0);
			requireJson(!hash.isBlank());
		}
	}
	private static class JsonCache {
		String[] id;
		String hash;
		long updated;
		long refreshed;
		long accessed;
		List<JsonDependency> dependencies;
		void validate() {
			requireJson(id != null && id.length > 0);
			requireJson(!hash.isBlank());
			requireJson(updated > 0);
			requireJson(refreshed > 0);
			requireJson(accessed > 0);
			for (var dependency : dependencies)
				dependency.validate();
		}
	}
	private static final Logger logger = LoggerFactory.getLogger(BlobCache.class);
	private static void load() {
		if (!Files.exists(indexPath))
			return;
		Exceptions.log(logger).run(Exceptions.sneak().runnable(() -> {
			for (var jcache : new Gson().fromJson(Files.readString(indexPath), JsonCache[].class)) {
				jcache.validate();
				byte[] hash = Base64.getUrlDecoder().decode(jcache.hash);
				if (!Files.exists(path(hash)))
					throw new IllegalStateException("BLOB cache index references missing data file.");
				BlobCache cache = get(new CacheId(jcache.id));
				Content content = new Content();
				content.hash = hash;
				content.updated = Instant.ofEpochMilli(jcache.updated);
				content.refreshed = Instant.ofEpochMilli(jcache.refreshed);
				content.dependencies = jcache.dependencies.stream()
					.map(jdep -> new Dependency(new CacheId(jdep.id), Base64.getUrlDecoder().decode(jdep.hash)))
					.collect(toList());
				cache.content.set(content);
				cache.accessed = Instant.ofEpochMilli(jcache.accessed);
			}
		}));
	}
	static {
		load();
	}
	/*
	 * This method must be called only from synchronized context, because it needs to capture consistent cache state.
	 */
	private static void save() {
		List<JsonCache> json = all.values().stream()
			.filter(c -> c.content.get().hash != null)
			.map(c -> {
				JsonCache jc = new JsonCache();
				jc.id = c.id.parts;
				Content content = c.content.get();
				jc.hash = Base64.getUrlEncoder().encodeToString(content.hash);
				jc.updated = content.updated.toEpochMilli();
				jc.refreshed = content.refreshed.toEpochMilli();
				jc.accessed = c.accessed.toEpochMilli();
				jc.dependencies = c.dependencies().stream()
					.map(d -> {
						JsonDependency jd = new JsonDependency();
						jd.id = d.id.parts;
						jd.hash = Base64.getUrlEncoder().encodeToString(d.hash);
						return jd;
					})
					.collect(toList());
				return jc;
			})
			.collect(toList());
		String serialized = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create()
			.toJson(json);
		Exceptions.sneak().run(() -> {
			Path temporary = Files.createTempFile(directory, "", ".tmp");
			Files.writeString(temporary, serialized);
			/*
			 * Use atomic move to minimize chances of data loss in case of sudden process crash.
			 */
			Files.move(temporary, indexPath, StandardCopyOption.ATOMIC_MOVE);
		});
	}
	/*
	 * We need separate thread pool, because long-running batches could clog SiteThread.bulk().
	 */
	private static final ExecutorService executor = new SiteThread()
		.owner(BlobCache.class)
		/*
		 * If this runs on the server, we want to avoid slowing down UI code.
		 */
		.lowestPriority()
		/*
		 * Batches are usually single-threaded, so parallelism serves to run several batches concurrently.
		 * This may be occasionally useful and it is safe as long as we have more cores on the CPU.
		 * We don't want more threads than cores, because batches are usually CPU-bound and memory-hungry.
		 * Batches can be still internally parallelized, for example using parallel streams
		 * (but see dependency tracking below).
		 * 
		 * Benchmarks (measuring algorithm speed) should be executed while the system is otherwise idle.
		 * It is quite possible we will need specialized system for benchmarking (perhaps based on JMH).
		 */
		.hardwareParallelism()
		.executor();
	/*
	 * We need state tracking for two reasons: preventing meaningless states (e.g. double reevaluation)
	 * and to inform the UI about run state of the cache.
	 */
	public static enum State {
		/*
		 * This indicates clean state of the cache unaffected by any recent events.
		 * It is reached after loading the cache from disk or after the cache is successfully refreshed.
		 * Cache content might not be available in this state though. That must be checked separately.
		 * This state is also reached in case of failure (exception, reactive blocking, cancellation).
		 * Exception must be checked to detect failure state.
		 */
		IDLE,
		/*
		 * When the cache is asked for refresh, it may first spend some time in the thread pool queue.
		 * This state indicates that the cache has an entry in the thread pool queue.
		 * Such entry cannot be removed (at least not easily), so this state cannot be changed in the UI.
		 * It is however possible to cancel the refresh in this case, so CANCELLING is also a a possible next state.
		 */
		SCHEDULED,
		/*
		 * We stay in this state while the cache is refreshing. Only cancellation can change the state.
		 */
		RUNNING,
		/*
		 * When cancel() is called, we cannot stop the batch immediately.
		 * We set this state and wait until it is observed, usually when progress is reported.
		 */
		CANCELLING;
	}
	private final ReactiveVariable<State> state = new ReactiveVariable<>(State.IDLE);
	public State state() {
		return state.get();
	}
	/*
	 * Running batches know which cache are they generating data for.
	 * This is mostly useful for progress reporting.
	 */
	private static final ThreadLocal<BlobCache> current = new ThreadLocal<>();
	public static BlobCache current() {
		return current.get();
	}
	/*
	 * Progress messages get their own reactive variable, because progress is changing quickly.
	 * Progress reporting would otherwise overwhelm UI with refreshes, especially heavy UI like visualizations,
	 * which have a reactive dependency on cache content.
	 */
	private ReactiveVariable<String> progress = new ReactiveVariable<>();
	public String progress() {
		return progress.get();
	}
	/*
	 * We should use reactive rate limiter from hookless, but it is not yet implemented.
	 * We will instead crudely silence progress reporting for some time after last progress update.
	 * This has the downside that stuck batch might not show the last progress message.
	 * If we were to fix it, we would be better off investing time in the hookless rate limiter.
	 */
	private long progressTime;
	/*
	 * Take arbitrary Object, so that callers don't have to toString() everything.
	 */
	public static void progress(Object message) {
		BlobCache cache = current();
		/*
		 * Tolerate execution of this method outside of running batch.
		 * This is useful for cases when batch code is also used in other contexts.
		 */
		if (cache != null) {
			/*
			 * If current cache is not null, it means we must be in state RUNNING or CANCELLING.
			 * Progress reporting is the ideal place to cancel the batch by throwing an exception.
			 * Query cache state non-reactively to avoid creating unnecessary reactive dependency
			 * that is unrelated to what the batch is actually doing.
			 */
			try (var ignored = ReactiveScope.ignore()) {
				if (cache.state.get() == State.CANCELLING)
					throw new CancellationException();
			}
			/*
			 * Call toString() outside the synchronized block just in case it somehow happens to reference another BlobCache.
			 */
			var formatted = message != null ? message.toString() : null;
			/*
			 * Synchronization is always global, because all caches are tied together by the shared directory.
			 */
			synchronized (BlobCache.class) {
				/*
				 * Here comes our crude rate limiter with deficiences described above.
				 * We will limit progress reporting to 300ms intervals,
				 * which is already fast enough to be hard to read.
				 */
				long now = System.currentTimeMillis();
				if (cache.progressTime == 0 || now > cache.progressTime + 300) {
					cache.progressTime = now;
					cache.progress.set(formatted);
				}
			}
		}
	}
	public static void progress(String format, Object... args) {
		progress(String.format(format, args));
	}
	public void cancel() {
		/*
		 * Synchronization is always global, because all caches are tied together by the shared directory.
		 */
		synchronized (BlobCache.class) {
			/*
			 * Silently tolerate calls in incorrect state, which happens easily in asynchronous UIs.
			 */
			if (state.get() == State.SCHEDULED || state.get() == State.RUNNING)
				state.set(State.CANCELLING);
		}
	}
	/*
	 * If the batch fails, cache content remains unchanged.
	 * We don't want to destroy good (if stale) cache content just because of some random error.
	 * We however have to report the exception in the UI, so we create separate API for it.
	 * This is also used to indicate cancellation and reactive blocking of the last refresh.
	 */
	private final ReactiveVariable<Throwable> exception = new ReactiveVariable<>();
	public Throwable exception() {
		return exception.get();
	}
	/*
	 * We need to track relationships (dependencies) between caches, but we cannot do it via current cache,
	 * because dependency tracking is also useful in higher level code, e.g. to display list of used caches in the UI.
	 * We will therefore expose an explicit mechanism for tracking dependencies on caches.
	 */
	private static final ThreadLocal<Tracker> trackerRef = new ThreadLocal<>();
	public static class Tracker implements AutoCloseable {
		/*
		 * Dependency tracker scopes may be nested. We don't want to prevent it, so we have to tolerate it.
		 */
		private final Tracker outer = trackerRef.get();
		/*
		 * Calling the constructor already sets the tracker as current.
		 * This is a bit aggressive, but it makes the API neat and concise.
		 */
		public Tracker() {
			trackerRef.set(this);
		}
		@Override
		public void close() {
			trackerRef.set(outer);
		}
		/*
		 * We want to report every dependency only once, so we need a set.
		 * But the UI is easier to use when dependencies are reported in order they are used,
		 * which usually corresponds to their logical order in the computation.
		 * Since set is an overkill for a small number of items, we will just check the list for duplicates.
		 */
		private final List<Dependency> dependencies = new ArrayList<>();
		public List<Dependency> dependencies() {
			return dependencies;
		}
		/*
		 * Dependency tracking method is static, so that we don't have to expose current tracker reference
		 * and so that the calling code can be simpler and safer.
		 */
		@SuppressWarnings("resource")
		public static void add(Dependency dependency) {
			/*
			 * If trackers are nested, the correct behavior is to record the dependency in each one of them.
			 * This also nicely covers the case when this method is called without any tracker in scope.
			 */
			for (Tracker tracker = trackerRef.get(); tracker != null; tracker = tracker.outer) {
				/*
				 * No synchronization is needed, because thread-local tracker can be only used from single thread.
				 * Multi-threaded code has to create one tracker for each thread
				 * and then explicitly copy captured dependencies into the main tracker.
				 * 
				 * Equality implementation in Dependency class ensures uniqueness.
				 * The same cache can be recorded with different hashes if the hash changes during computation.
				 */
				if (!tracker.dependencies.contains(dependency))
					tracker.dependencies.add(dependency);
			}
		}
	}
	/*
	 * If we don't have data available, we will throw an exception, because we have no reasonable fallback.
	 * Cache-aware UI is expected to catch this exception and display a nice message to the user.
	 * User can then explicitly refresh the cache if desirable.
	 */
	public class EmptyCacheException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public EmptyCacheException() {
			super("Requested BLOB cache is empty.");
		}
		/*
		 * Allow UI to show the name of the cache that needs to be populated.
		 */
		public BlobCache cache() {
			return BlobCache.this;
		}
	}
	/*
	 * Getting the path (or hash) is not sufficient to record access to the cache.
	 * In order to keep the cache alive, access() must be called explicitly.
	 * 
	 * Explicit access() method allows misuse of the API, but it also makes calling code easier to understand,
	 * because access intent is explicit in the code (as opposed to cache management intent in UI).
	 * UI and cache readers can thus share all methods of this class except this one.
	 * Callers must not forget to call access(), but that's easy to accomplish,
	 * because this class has only a few specialized wrappers.
	 */
	public void access() {
		/*
		 * Synchronization is always global, because all caches are tied together by the shared directory.
		 */
		synchronized (BlobCache.class) {
			/*
			 * Access implies dependency tracking.
			 * This must be done before exception is thrown as otherwise the cache won't show up in UI.
			 */
			Tracker.add(new Dependency(id, hash()));
			/*
			 * This method is called by code that actually needs the data. Throw if we don't have it.
			 * This ensures that path() will return non-null value if preceded by call to access().
			 * 
			 * Calling hash() right at the beginning also records reactive dependency,
			 * which will refresh dependent visualizations when cache content changes.
			 */
			if (hash() == null)
				throw new EmptyCacheException();
			/*
			 * Updating the access timestamp is the main purpose of this method, but it also has the nice side effect
			 * that purge() running between now and subsequent path() call will not discard content of this cache.
			 */
			Instant now = Instant.now();
			/*
			 * Rate-limit updates of the access timestamp to once per hour,
			 * because access timestamp change triggers writing of the JSON file.
			 */
			if (now.isAfter(accessed.plus(1, ChronoUnit.HOURS))) {
				accessed = now;
				Exceptions.log(logger).run(BlobCache::save);
			}
		}
	}
	/*
	 * Ordinary dependencies are only tracked for successful batch runs.
	 * In order to show in the UI what's causing reactive blocking or exception,
	 * we need an extra transient list of dependencies from the last batch run.
	 */
	private final ReactiveVariable<List<Dependency>> blockers = new ReactiveVariable<>(Collections.emptyList());
	public List<Dependency> blockers() {
		return Collections.unmodifiableList(blockers.get());
	}
	private void evaluate() {
		/*
		 * Synchronization is always global, because all caches are tied together by the shared directory.
		 */
		synchronized (BlobCache.class) {
			/*
			 * If we get here, we must be either in SCHEDULED or CANCELLING state.
			 * If the refresh was cancelled while still scheduled, don't even start it.
			 */
			if (state.get().equals(State.CANCELLING)) {
				state.set(State.IDLE);
				exception.set(new CancellationException());
				blockers.set(Collections.emptyList());
				return;
			}
			state.set(State.RUNNING);
			current.set(this);
		}
		ReactiveValue<byte[]> value;
		List<Dependency> dependencies;
		/*
		 * We will run the batch in reactive scope, so that reactive freezes work,
		 * but we will ignore captured dependencies, because batches are too heavy to be reactive.
		 */
		ReactiveScope scope = OwnerTrace
			.of(new ReactiveScope())
			.parent(this)
			.target();
		try (var computation = scope.enter()) {
			try (Tracker tracker = new Tracker()) {
				/*
				 * This will catch all exceptions that could be realistically thrown.
				 * We can let theoretical exceptions pass through and hit the logging handler.
				 */
				value = ReactiveValue.capture(supplier);
				dependencies = tracker.dependencies();
			}
		}
		/*
		 * Consider all cases other than successful non-blocking non-null byte[] result to be a failure.
		 */
		Throwable exception = null;
		if (value.blocking())
			exception = new ReactiveBlockingException();
		else if (value.exception() != null)
			exception = value.exception();
		else if (value.result() == null)
			exception = new NullPointerException();
		/*
		 * Perform expensive operations (hashing and writing files) outside of the synchronized block.
		 */
		byte[] hash = null;
		Path temporary = null;
		if (exception == null) {
			hash = Exceptions.sneak().get(() -> MessageDigest.getInstance("SHA-256").digest(value.result()));
			try {
				temporary = Files.createTempFile(directory, "", ".tmp");
				Files.write(temporary, value.result());
			} catch (Throwable ex) {
				exception = ex;
			}
		}
		synchronized (BlobCache.class) {
			current.set(null);
			progress.set(null);
			/*
			 * Atomic move has to be synchronized, because cache directory forms a single consistent unit.
			 * We have to get the file in place before we save JSON referencing the hash.
			 */
			if (exception == null && temporary != null) {
				/*
				 * Use of atomic move will ensure we either write all file content or none.
				 * This keeps the cache consistent even in case of application crash.
				 * We aren't syncing disks, so the risk of hardware failure (e.g. power outage) remains.
				 */
				try {
					Files.move(temporary, path(hash), StandardCopyOption.ATOMIC_MOVE);
				} catch (Throwable ex) {
					exception = ex;
				}
			}
			this.exception.set(exception);
			if (exception != null)
				blockers.set(dependencies);
			else {
				blockers.set(Collections.emptyList());
				Content fresh = new Content();
				fresh.hash = hash;
				fresh.updated = content.get().updated;
				fresh.refreshed = Instant.now();
				/*
				 * Only change the last update timestamp if the content has actually changed.
				 */
				if (fresh.updated == null || !Arrays.equals(fresh.hash, content.get().hash))
					fresh.updated = fresh.refreshed;
				fresh.dependencies = dependencies;
				content.set(fresh);
				/*
				 * Freshly generated cache must have been accessed or otherwise requested.
				 * Setting access timestamp here prevents concurrent purge() from deleting the cache immediately.
				 */
				accessed = Instant.now();
				Exceptions.log(logger).run(BlobCache::save);
			}
			state.set(State.IDLE);
		}
	}
	/*
	 * Refresh must be triggered explicitly, because we cannot afford full reactivity with heavy batches.
	 */
	public void refresh() {
		/*
		 * UI code is expected to call defined() first to see whether this cache knows how to refresh itself.
		 */
		if (supplier == null)
			throw new IllegalStateException();
		/*
		 * Synchronization is always global, because all caches are tied together by the shared directory.
		 */
		synchronized (BlobCache.class) {
			/*
			 * Silently tolerate calls in incorrect state, which may happen in asynchronous UI code.
			 */
			if (state.get() == State.IDLE) {
				state.set(State.SCHEDULED);
				executor.submit(Exceptions.log(logger).runnable(this::evaluate));
			}
		}
	}
	/*
	 * Returning file paths is tricky, because these files might be deleted by subsequent call to purge().
	 * To prevent that, we will make every file accessed via path() immune to purge().
	 */
	private static final Set<Path> pinned = new HashSet<>();
	public Path path() {
		byte[] hash = hash();
		if (hash == null)
			return null;
		Path path = path(hash);
		/*
		 * Synchronization is always global, because all caches are tied together by the shared directory.
		 */
		synchronized (BlobCache.class) {
			pinned.add(path);
		}
		return path;
	}
	/*
	 * We have no garbage collector to help us with cleaning up unused caches.
	 * Explicit removal is laborious. We will instead expire caches after a timeout.
	 * Since timeouts are usually long, it suffices to call this method once when the program starts.
	 */
	public static void purge(Duration lifetime) {
		/*
		 * Do not propagate exceptions to caller, because it cannot do anything about them, but do log the exceptions.
		 */
		Exceptions.log(logger).run(Exceptions.sneak().runnable(() -> {
			/*
			 * Synchronization is always global, because all caches are tied together by the shared directory.
			 */
			synchronized (BlobCache.class) {
				/*
				 * Don't remove caches that are used as dependencies of other caches even if they have the wrong hash.
				 * This makes it easier to construct usable UI that doesn't have to deal with dangling links.
				 */
				var used = StreamEx.of(all.values())
					.flatMap(c -> c.dependencies().stream())
					.map(d -> d.cache())
					.toSet();
				Instant now = Instant.now();
				for (BlobCache cache : all.values()) {
					/*
					 * Do not purge caches that are being updated, because such caches are certainly in use.
					 */
					if (now.isAfter(cache.accessed.plus(lifetime)) && !used.contains(cache) && cache.state.get() == State.IDLE) {
						cache.content.set(new Content());
					}
				}
				save();
				/*
				 * The above process as well as prior cache content changes leaves behind unused *.dat files.
				 * Here we delete every *.dat file that is not referenced from any cache
				 * and that wasn't pinned by previous call to path().
				 */
				Set<Path> referenced = new HashSet<>(pinned);
				for (BlobCache cache : all.values()) {
					Path path = cache.path();
					if (path != null)
						referenced.add(path);
				}
				for (Path path : Files.list(directory).collect(toList())) {
					if (path.getFileName().endsWith(".dat") && !referenced.contains(path))
						Files.delete(path);
				}
				/*
				 * There might be some leftover *.tmp files, but their presence is so unlikely
				 * we don't bother cleaning them up.
				 */
			}
		}));
	}
}
