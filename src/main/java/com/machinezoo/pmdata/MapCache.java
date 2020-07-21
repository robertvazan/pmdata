// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;
import com.machinezoo.stagean.*;
import one.util.streamex.*;

@DraftApi
@FunctionalInterface
public interface MapCache<K, V> extends Function<K, V> {
	V get(K key);
	@Override
	default V apply(K key) {
		return get(key);
	}
	static <K, V> MapCache<K, V> of(Function<K, V> function) {
		return function::apply;
	}
	static <K, V> MapCache<K, V> of(Map<K, V> map) {
		return map::get;
	}
	private static <K, V> Function<K, V> progress(Function<K, V> mapper) {
		return k -> {
			BlobCache.progress(k);
			return mapper.apply(k);
		};
	}
	static <K, V> Map<K, V> fill(Collection<K> keys, Function<K, V> mapper) {
		return StreamEx.of(keys).toMap(progress(mapper));
	}
	static <K, V> Map<K, V> fill(K[] keys, Function<K, V> mapper) {
		return StreamEx.of(keys).toMap(progress(mapper));
	}
	static <K, V> Map<K, V> fill(Stream<K> keys, Function<K, V> mapper) {
		return StreamEx.of(keys).toMap(progress(mapper));
	}
	default MapCache<K, V> keep() {
		var cache = new ConcurrentHashMap<K, V>();
		return k -> cache.computeIfAbsent(k, this::get);
	}
	default Map<K, V> toMap(List<K> keys) {
		return StreamEx.of(keys).toMap(this);
	}
	default Map<K, V> toMap(Stream<K> keys) {
		return StreamEx.of(keys).toMap(this);
	}
	default Map<K, V> toMap(K[] keys) {
		return StreamEx.of(keys).toMap(this);
	}
	static <K, V> MapCache<K, V> lazy(Supplier<MapCache<K, V>> supplier) {
		return k -> supplier.get().get(k);
	}
	default <W> MapCache<K, W> map(Function<V, W> mapping) {
		return k -> mapping.apply(get(k));
	}
}
