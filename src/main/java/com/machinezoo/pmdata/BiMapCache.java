// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;
import org.apache.commons.lang3.tuple.*;
import com.machinezoo.stagean.*;
import one.util.streamex.*;

@DraftApi
@FunctionalInterface
public interface BiMapCache<K1, K2, V> extends BiFunction<K1, K2, V> {
	V get(K1 key1, K2 key2);
	default V get(Pair<K1, K2> key) {
		return get(key.getLeft(), key.getRight());
	}
	@Override
	default V apply(K1 key1, K2 key2) {
		return get(key1, key2);
	}
	default MapCache<K2, V> bind1(K1 key1) {
		return k2 -> get(key1, k2);
	}
	default MapCache<K1, V> bind2(K2 key2) {
		return k1 -> get(k1, key2);
	}
	static <K1, K2, V> BiMapCache<K1, K2, V> of(BiFunction<K1, K2, V> function) {
		return function::apply;
	}
	static <K1, K2, V> BiMapCache<K1, K2, V> of(Function<Pair<K1, K2>, V> function) {
		return (k1, k2) -> function.apply(Pair.of(k1, k2));
	}
	static <K1, K2, V> BiMapCache<K1, K2, V> of(Map<Pair<K1, K2>, V> map) {
		return (k1, k2) -> map.get(Pair.of(k1, k2));
	}
	private static <K1, K2, V> V apply(BiFunction<K1, K2, V> mapper, K1 key1, K2 key2) {
		BlobCache.progress("%s + %s", Objects.toString(key1), Objects.toString(key2));
		return mapper.apply(key1, key2);
	}
	private static <K1, K2, V> V apply(BiFunction<K1, K2, V> mapper, Pair<K1, K2> pair) {
		return apply(mapper, pair.getLeft(), pair.getRight());
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(Collection<K1> keys1, Collection<K2> keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(K1[] keys1, K2[] keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(Stream<K1> keys1, Stream<K2> keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(K1[] keys1, Collection<K2> keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(Stream<K1> keys1, Collection<K2> keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(Collection<K1> keys1, K2[] keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(Collection<K1> keys1, Stream<K2> keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(K1[] keys1, Stream<K2> keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	static <K1, K2, V> Map<Pair<K1, K2>, V> fill(Stream<K1> keys1, K2[] keys2, BiFunction<K1, K2, V> mapper) {
		return StreamEx.of(keys1).flatMap(k1 -> StreamEx.of(keys2).map(k2 -> Pair.of(k1, k2))).toMap(p -> apply(mapper, p));
	}
	default BiMapCache<K1, K2, V> keep() {
		var cache = new ConcurrentHashMap<Pair<K1, K2>, V>();
		return (k1, k2) -> cache.computeIfAbsent(Pair.of(k1, k2), this::get);
	}
	static <K1, K2, V> BiMapCache<K1, K2, V> lazy(Supplier<BiMapCache<K1, K2, V>> supplier) {
		return (k1, k2) -> supplier.get().get(k1, k2);
	}
	default <W> BiMapCache<K1, K2, W> map(Function<V, W> mapping) {
		return (k1, k2) -> mapping.apply(get(k1, k2));
	}
}
