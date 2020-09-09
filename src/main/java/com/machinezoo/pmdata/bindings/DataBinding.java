// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.bindings;

import java.util.*;
import java.util.function.*;
import com.machinezoo.noexception.*;

public abstract class DataBinding<T> implements Supplier<Optional<T>> {
	/*
	 * Give callers an opportunity to provide fallback via Optional.orElse() or throw via Optional.get()/orElseThrow().
	 * They could of course wrap raw result via Optional.ofNullable(), but making use of Optional
	 * is expected to be the typical use case, so default to returning Optional.
	 * We could have an entire separate OptionalDataBinding, but we are trying to keep the number of classes down.
	 * 
	 * We have to choose how to handle read/write errors. For convenience, these methods do not throw.
	 * Getters just return empty Optional and setters silently ignore failed writes. This matches behavior of Preferences.
	 * Bindings are usually non-essential and weakly typed. Throwing or even logging upon read/write error is undesirable.
	 */
	public abstract Optional<T> get();
	public abstract void set(T value);
	/*
	 * Supplier parameter returns raw value instead of Optional. This is because so many APIs return raw types
	 * and wrapping other APIs is the main purpose of this method.
	 */
	public static <T> DataBinding<T> define(Supplier<T> getter, Consumer<T> setter) {
		return new DataBinding<>() {
			@Override
			public Optional<T> get() {
				return Optional.ofNullable(getter.get());
			}
			public void set(T value) {
				setter.accept(value);
			}
		};
	}
	public static <K, V> DataBinding<V> of(Map<K, V> map, K key) {
		return define(() -> map.get(key), v -> {
			if (v != null)
				map.put(key, v);
			else
				map.remove(key);
		});
	}
	/*
	 * Methods get/set should never throw, but it's sometimes more convenient to define DataBinding
	 * that breaks this property and then wrap it with exception handlers.
	 */
	public DataBinding<T> silence() {
		return define(Exceptions.silence().supplier(() -> get().orElse(null)).orElse(null), Exceptions.silence().consumer(this::set));
	}
	/*
	 * Most often, fallbacks should be provided when the binding is used, but let's allow predefined fallback,
	 * which may be useful sometimes. Plus this mechanism allows callers to chain several bindings.
	 */
	public DataBinding<T> orElse(T fallback) {
		return define(() -> get().orElse(fallback), this::set);
	}
	public DataBinding<T> orElseGet(Supplier<T> supplier) {
		return define(() -> get().orElseGet(supplier), this::set);
	}
	public DataBinding<T> or(Supplier<Optional<T>> supplier) {
		return define(() -> get().or(supplier).orElse(null), this::set);
	}
}