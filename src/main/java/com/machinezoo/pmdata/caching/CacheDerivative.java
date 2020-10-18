// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;
import com.machinezoo.hookless.*;

public class CacheDerivative<T> {
	private final ReactiveValue<T> value;
	public ReactiveValue<T> value() {
		return value;
	}
	private final CacheInput input;
	public CacheInput input() {
		return input;
	}
	public CacheDerivative(ReactiveValue<T> value, CacheInput input) {
		this.value = value;
		this.input = input;
	}
	public static <T> CacheDerivative<T> capture(Supplier<T> supplier) {
		var input = new CacheInput();
		try (var recording = input.record()) {
			var value = ReactiveValue.capture(supplier);
			input.freeze();
			return new CacheDerivative<>(value, input);
		}
	}
	public T unpack() {
		input.unpack();
		return value.get();
	}
}
