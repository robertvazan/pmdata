// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.charset.*;
import java.util.*;
import java.util.function.*;
import com.google.common.hash.*;
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
	/*
	 * Input is allowed to be inconsistent with multiple values per key. This applies to both parameters and dependencies.
	 * Only the first value is recorded. All following values just flag the input as inconsistent.
	 * Cache supplier should be preferably written in such a way that it just uses the first value cached by this class.
	 */
	private boolean inconsistent;
	public synchronized boolean inconsistent() {
		return inconsistent;
	}
	public synchronized void taint() {
		if (!inconsistent) {
			modify();
			inconsistent = true;
		}
	}
	private Map<PersistentCache<?>, CacheSnapshot<?>> snapshots = new HashMap<>();
	public synchronized Map<PersistentCache<?>, CacheSnapshot<?>> snapshots() {
		return frozen ? snapshots : new HashMap<>(snapshots);
	}
	private void modify() {
		if (frozen)
			throw new IllegalStateException("Cache input cannot be modified anymore.");
	}
	public synchronized <T extends CacheFile> void snapshot(PersistentCache<T> cache, CacheSnapshot<T> snapshot) {
		if (!snapshots.containsKey(cache)) {
			modify();
			snapshots.put(cache, snapshot);
		} else if (snapshot != snapshots.get(cache) && !inconsistent) {
			modify();
			inconsistent = true;
		}
	}
	@SuppressWarnings("unchecked")
	public synchronized <T extends CacheFile> CacheSnapshot<T> snapshot(PersistentCache<T> cache) {
		var snapshot = snapshots.get(cache);
		if (snapshot == null) {
			/*
			 * HashMap permits null values. We want to cache null (absent) snapshots.
			 */
			if (!snapshots.containsKey(cache)) {
				modify();
				snapshots.put(cache, snapshot = CacheSnapshot.of(cache));
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
	public synchronized <T> T parameter(String key) {
		if (!parameters.containsKey(key))
			throw new IllegalArgumentException("No such parameter: " + key);
		return (T)parameters.get(key);
	}
	public synchronized void parameter(String key, Object value) {
		if (!parameters.containsKey(key)) {
			modify();
			parameters.put(key, value);
		} else if (!Objects.toString(value).equals(Objects.toString(parameters.get(key))) && !inconsistent) {
			modify();
			inconsistent = true;
		}
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
			hash = hash();
			frozen = true;
		}
	}
	@SuppressWarnings("unchecked")
	public synchronized void unpack() {
		var into = get();
		for (var entry : snapshots.entrySet())
			into.snapshot((PersistentCache<CacheFile>)entry.getKey(), (CacheSnapshot<CacheFile>)entry.getValue());
		for (var entry : parameters.entrySet())
			into.parameter(entry.getKey(), entry.getValue());
		if (inconsistent)
			into.taint();
	}
	@Override
	public synchronized String toString() {
		return (inconsistent ? "[inconsistent]\n" : "")
			+ StreamEx.of(parameters.keySet()).sorted().map(k -> k + " = " + Objects.toString(parameters.get(k)) + "\n").joining()
			+ StreamEx.of(snapshots.keySet()).map(c -> c + " = " + Optional.ofNullable(snapshots.get(c)).map(s -> s.hash()).orElse("empty") + "\n").sorted().joining();
	}
	private static String hashId(String text) {
		var hash = Hashing.sha256().hashString(text, StandardCharsets.UTF_8).asBytes();
		return Base64.getUrlEncoder().encodeToString(hash).replace("=", "");
	}
	private String hash;
	public synchronized String hash() {
		return frozen ? hash : hashId(toString());
	}
};
