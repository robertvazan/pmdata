// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.bindings;

import java.util.*;
import java.util.function.*;
import com.machinezoo.hookless.prefs.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;

/*
 * Technically, we could as well use DataBinding<String>, but String bindings have unique features not available on DataBinding.
 * There will inevitably String-specific APIs that would be inconvenient to expose on DataBinding.
 * We do not inherit from DataBinding in order to avoid name clashes as we define several identical methods here with slightly different signature.
 */
public abstract class StringBinding implements Supplier<Optional<String>> {
	public abstract Optional<String> get();
	public abstract void set(String value);
	public static StringBinding define(Supplier<String> getter, Consumer<String> setter) {
		return new StringBinding() {
			@Override
			public Optional<String> get() {
				return Optional.ofNullable(getter.get());
			}
			@Override
			public void set(String value) {
				setter.accept(value);
			}
		};
	}
	public static StringBinding of(DataBinding<String> binding) {
		return define(() -> binding.get().orElse(null), binding::set);
	}
	public static StringBinding of(ReactivePreferences preferences, String key) {
		return StringBinding.define(() -> preferences.get(key, null), v -> {
			if (v != null)
				preferences.put(key, v);
			else
				preferences.remove(key);
		});
	}
	public static StringBinding of(String key) {
		return of(SiteFragment.get().preferences(), key);
	}
	public static <K> StringBinding of(Map<K, String> map, K key) {
		return of(DataBinding.of(map, key));
	}
	public StringBinding silence() {
		return define(Exceptions.silence().supplier(() -> get().orElse(null)).orElse(null), Exceptions.silence().consumer(this::set));
	}
	public StringBinding orElse(String fallback) {
		return define(() -> get().orElse(fallback), this::set);
	}
	public StringBinding orElseGet(Supplier<String> supplier) {
		return define(() -> get().orElseGet(supplier), this::set);
	}
	public StringBinding or(Supplier<Optional<String>> supplier) {
		return define(() -> get().or(supplier).orElse(null), this::set);
	}
	public DataBinding<String> asData() {
		return DataBinding.define(() -> get().orElse(null), this::set);
	}
	public <T extends Enum<T>> DataBinding<T> asEnum(Class<T> clazz) {
		return DataBinding.define(() -> get().map(v -> Enum.valueOf(clazz, v)).orElse(null), v -> set(v.name())).silence();
	}
	public <T extends Enum<T>> DataBinding<Optional<T>> asOptionalEnum(Class<T> clazz) {
		return new DataBinding<Optional<T>>() {
			@Override
			public Optional<Optional<T>> get() {
				var value = StringBinding.this.get().orElse(null);
				if (value == null)
					return Optional.empty();
				else if (value.isEmpty())
					return Optional.of(Optional.empty());
				else
					return Exceptions.silence().get(() -> Optional.of(Optional.of(Enum.valueOf(clazz, value)))).orElse(Optional.empty());
			}
			@Override
			public void set(Optional<T> value) {
				StringBinding.this.set(value == null ? null : value.isEmpty() ? "" : value.get().name());
			}
		};
	}
	public IntBinding asInt() {
		return new IntBinding() {
			@Override
			public OptionalInt get() {
				var value = StringBinding.this.get();
				if (value.isEmpty())
					return OptionalInt.empty();
				try {
					return OptionalInt.of(Integer.parseInt(value.get()));
				} catch (Throwable ex) {
					return OptionalInt.empty();
				}
			}
			@Override
			public void set(OptionalInt value) {
				StringBinding.this.set(value.isPresent() ? Integer.toString(value.getAsInt()) : null);
			}
		};
	}
}
