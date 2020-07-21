// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import com.machinezoo.hookless.*;
import com.machinezoo.stagean.*;

/*
 * This feels like it could be an experimental hookless feature, perhaps integrated with hookless caches/memoizers,
 * but so far I cannot see application outside data science, so let's keep it here as a utility class for now.
 * It might have to stay this way, because the inlining-optimized API just isn't suitable for hookless.
 */
@DraftApi("should be replaced with LRU cache")
public class FreezeCache {
	/*
	 * Some long-running reactive computations like batches could freeze gigabytes of objects.
	 * We will therefore discard cached objects if we reach capacity.
	 */
	private final int capacity;
	public FreezeCache(int capacity) {
		this.capacity = capacity;
	}
	public FreezeCache() {
		/*
		 * By default, cache only 2 items per reactive computation.
		 * This is sufficient for iteration (that uses only one object at a time)
		 * as well as for most forms of chaining (that uses last two objects)
		 * and comparison (that uses two selected objects).
		 */
		this(2);
	}
	/*
	 * Fast hashCode() trick taken from hookless. This object is frequently looked up in reactive freezes.
	 */
	private final int hashCode = ThreadLocalRandom.current().nextInt();
	@Override
	public int hashCode() {
		return hashCode;
	}
	/*
	 * Since several entries may exist per freeze cache (in every reactive computation where it is used),
	 * the entries must be distinguished by key. We want the most generic key, so we allow any list of objects as a key.
	 * We could use ArrayList to get equality and hashing, but specialized key class will have lower overhead.
	 */
	private static class MultiKey {
		final Object[] keys;
		MultiKey(Object[] keys) {
			this.keys = keys;
		}
		@Override
		public boolean equals(Object obj) {
			return obj instanceof MultiKey && Arrays.equals(keys, ((MultiKey)obj).keys);
		}
		@Override
		public int hashCode() {
			return Objects.hash(keys);
		}
	}
	/*
	 * Exposing the entry via method allows us to use neat ellipsis parameters.
	 */
	public Entry entry(Object... keys) {
		/*
		 * There is one cache for every combination of FreezeCache instance and reactive computation.
		 * Essentially, every instance of FreezeCache serves as an additional key.
		 * Callers can instantiate this class as a static field to easily create isolated method-specific cache.
		 */
		Storage storage = CurrentReactiveScope.freeze(this, Storage::new);
		/*
		 * Create new ArrayList, because Arrays.asList doesn't implement equals and hashCode.
		 * We can then use the list as a key in the entry map.
		 */
		return storage.get(new MultiKey(keys));
	}
	/*
	 * Nothing in this class is synchronized, because reactive computations are single-threaded.
	 */
	private class Storage {
		/*
		 * We allow caching parameterized functions by supporting several entries per cache.
		 * We could also allow wrapping Function or BiFunction,
		 * but that would make the API more verbose than what we consider acceptable in data science code.
		 */
		final Map<MultiKey, Entry> entries = new HashMap<>();
		Entry get(MultiKey key) {
			Entry entry = entries.get(key);
			if (entry == null) {
				if (entries.size() >= capacity) {
					/*
					 * When the cache is full, we simply discard the entry with lowest access timestamp.
					 * 
					 * This algorithm is O(n), which will work fine for small cache capacity.
					 * If larger capacities are used, we will have to change this to remove entries in bulk.
					 */
					Entry discarded = entries.values().stream()
						.min(Comparator.comparingLong(e -> e.access))
						.orElseThrow();
					entries.remove(discarded.key);
				}
				entry = new Entry(key);
				entries.put(key, entry);
			}
			return entry;
		}
	}
	/*
	 * We will use atomic counter of reads to create timestamps for size-limiting purposes.
	 */
	private static final AtomicLong counter = new AtomicLong();
	public class Entry {
		private final MultiKey key;
		private Entry(MultiKey key) {
			this.key = key;
		}
		private long access;
		private boolean initialized;
		private Object value;
		@SuppressWarnings("unchecked")
		public <T> T get(Supplier<T> supplier) {
			access = counter.incrementAndGet();
			if (!initialized) {
				value = supplier.get();
				initialized = true;
			}
			/*
			 * There's no way to get around unchecked cast with the current neat API.
			 * Cast exceptions at runtime are however unlikely due to the way the API is typically used.
			 */
			return (T)value;
		}
	}
	/*
	 * Concise API for a few parameters that doesn't require chaining so many method calls.
	 */
	public <T> T get(Supplier<T> supplier) {
		return entry().get(supplier);
	}
	/*
	 * We could use typed key and Function, but it turns out this would just complicate calling code,
	 * because function's lambda parameter tends to have the same name as the key variable
	 * and even if renamed, the two would get mixed up in the lambda.
	 * It is therefore better to force callers to specialize the supplier for the key
	 * by referencing the key in the supplier lambda.
	 */
	public <T> T get(Object key, Supplier<T> supplier) {
		return entry(key).get(supplier);
	}
	public <T> T get(Object key1, Object key2, Supplier<T> supplier) {
		return entry(key1, key2).get(supplier);
	}
	private static final Map<CacheId, FreezeCache> named = new ConcurrentHashMap<>();
	public static FreezeCache of(CacheId id) {
		return named.computeIfAbsent(id, x -> new FreezeCache());
	}
	/*
	 * We will provide static methods for convenient lookup via cache path without having to add class variable.
	 * The only downside is a more expensive hash lookup in addition to the cheap lookup in reactive freezes.
	 */
	public static <T> T of(CacheId id, Supplier<T> supplier) {
		return of(id).get(supplier);
	}
	public static <T> T of(CacheId id, Object key, Supplier<T> supplier) {
		return of(id).get(key, supplier);
	}
	public static <T> T of(CacheId id, Object key1, Object key2, Supplier<T> supplier) {
		return of(id).get(key1, key2, supplier);
	}
}
