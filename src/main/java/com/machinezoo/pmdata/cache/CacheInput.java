// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.cache;

import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.function.*;
import com.machinezoo.noexception.*;
import one.util.streamex.*;

public class CacheInput {
	public static CacheInput NONE = new CacheInput();
	static {
		NONE.freeze();
	}
	/*
	 * There's one thread-local CacheInput, but multiple threads can be supported
	 * by obtaining CacheInput instance from get() and then calling record() in worker threads.
	 */
	private static final ThreadLocal<CacheInput> current = new ThreadLocal<>();
	public CloseableScope record() {
		var outer = current.get();
		current.set(this);
		return () -> current.set(outer);
	}
	/*
	 * Difference between these two is that current() returns empty Optional while get() returns temporary instance.
	 */
	public static Optional<CacheInput> current() {
		return Optional.ofNullable(current.get());
	}
	public static CacheInput get() {
		return current().orElseGet(CacheInput::new);
	}
	private boolean frozen;
	private Map<PersistentCache<?>, CacheSnapshot<?>> snapshots = new HashMap<>();
	public synchronized Map<PersistentCache<?>, CacheSnapshot<?>> snapshots() {
		return frozen ? snapshots : new HashMap<>(snapshots);
	}
	private void modify() {
		if (frozen)
			throw new IllegalStateException("Cache input cannot be modified anymore.");
	}
	@SuppressWarnings("unchecked")
	public synchronized <T extends CacheData> CacheSnapshot<T> snapshot(PersistentCache<T> cache) {
		var snapshot = snapshots.get(cache);
		if (snapshot == null) {
			/*
			 * HashMap permits null values. We want to cache null (absent) snapshots.
			 */
			if (!snapshots.containsKey(cache)) {
				modify();
				snapshots.put(cache, snapshot = cache.snapshot());
			}
		}
		return (CacheSnapshot<T>)snapshot;
	}
	/*
	 * We will store parameters that area arbitrary objects. They can even be null.
	 * These objects should generally be immutable or guaranteed not to be modified.
	 * They will have to be stringified when presented in UI, so sensible toString() must be implemented.
	 * The toString() implementation is also used to compute input hash.
	 */
	private Map<String, Object> parameters = new HashMap<>();
	public synchronized Map<String, Object> parameters() {
		return frozen ? parameters : new HashMap<>(parameters);
	}
	@SuppressWarnings("unchecked")
	public synchronized <T> T parameter(String key, Supplier<T> supplier) {
		var stored = parameters.get(key);
		if (stored == null) {
			/*
			 * HashMap permits null values. We want to cache null.
			 */
			if (!parameters.containsKey(key)) {
				modify();
				parameters.put(key, stored = supplier.get());
			}
		}
		return (T)stored;
	}
	public synchronized void freeze() {
		if (!frozen) {
			snapshots = Collections.unmodifiableMap(snapshots);
			parameters = Collections.unmodifiableMap(parameters);
			frozen = true;
		}
	}
	@Override
	public synchronized String toString() {
		return StreamEx.of(parameters.keySet()).sorted().map(k -> k + "=" + Objects.toString(parameters.get(k)) + "\n").joining()
			+ StreamEx.of(snapshots.keySet()).sorted().map(id -> id + "=" + Optional.ofNullable(snapshots.get(id)).map(s -> s.hash()).orElse("empty") + "\n").joining();
	}
	public String hash() {
		return Base64.getUrlEncoder().encodeToString(Exceptions.sneak().get(() -> MessageDigest.getInstance("SHA-256")).digest(toString().getBytes(StandardCharsets.UTF_8)));
	}
};
