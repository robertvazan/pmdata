// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.bindings;

import java.util.*;
import java.util.function.*;
import com.machinezoo.hookless.prefs.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;

public abstract class IntBinding implements Supplier<OptionalInt> {
	public abstract OptionalInt get();
	public abstract void set(OptionalInt value);
	public void set(Integer value) {
		set(value != null ? OptionalInt.of(value) : OptionalInt.empty());
	}
	public void set(int value) {
		set(OptionalInt.of(value));
	}
	public static IntBinding define(Supplier<OptionalInt> getter, Consumer<OptionalInt> setter) {
		return new IntBinding() {
			@Override
			public OptionalInt get() {
				return getter.get();
			}
			public void set(OptionalInt value) {
				setter.accept(value);
			}
		};
	}
	public static IntBinding of(ReactivePreferences preferences, String key) {
		return StringBinding.of(preferences, key).asInt();
	}
	public static IntBinding of(String key) {
		return of(SiteFragment.get().preferences(), key);
	}
	public IntBinding silence() {
		return define(Exceptions.silence().supplier(this::get).orElse(OptionalInt.empty()), Exceptions.silence().consumer(this::set));
	}
	public IntBinding orElse(int fallback) {
		return define(() -> OptionalInt.of(get().orElse(fallback)), this::set);
	}
	public IntBinding orElseGet(IntSupplier supplier) {
		return define(() -> OptionalInt.of(get().orElseGet(supplier)), this::set);
	}
	public IntBinding or(Supplier<OptionalInt> supplier) {
		return define(() -> {
			var value = get();
			return value.isPresent() ? value : supplier.get();
		}, this::set);
	}
	public DataBinding<Integer> asData() {
		var outer = this;
		return new DataBinding<Integer>() {
			@Override
			public Optional<Integer> get() {
				var value = outer.get();
				return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
			}
			@Override
			public void set(Integer value) {
				outer.set(value != null ? OptionalInt.of(value) : OptionalInt.empty());
			}
		};
	}
	public StringBinding asString() {
		return new StringBinding() {
			@Override
			public Optional<String> get() {
				var value = IntBinding.this.get();
				return value.isPresent() ? Optional.of(Integer.toString(value.getAsInt())) : Optional.empty();
			}
			@Override
			public void set(String value) {
				IntBinding.this.set(value != null ? Exceptions.silence().getAsInt(() -> Integer.parseInt(value)) : OptionalInt.empty());
			}
		};
	}
}