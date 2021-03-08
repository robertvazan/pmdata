// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import static java.util.stream.Collectors.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import com.machinezoo.pmdata.bindings.*;
import com.machinezoo.stagean.*;
import it.unimi.dsi.fastutil.ints.*;

/*
 * Integers have specialized picker, because they have well-defined serialization for persistent preferences
 * and they are often found in ranges, which provides an easy way to enumerate them.
 */
@DraftApi
public class IntPicker {
	private String title;
	public IntPicker title(String title) {
		this.title = title;
		return this;
	}
	/*
	 * We want something strongly typed, so Iterable<Integer> is out of question.
	 * We could use primitive collection, but integers will be represented as an array in most cases.
	 * Where primitive collections or streams are used, they are easy to convert to an array.
	 */
	private final IntList items = new IntArrayList();
	public IntPicker add(int item) {
		items.add(item);
		return this;
	}
	public IntPicker add(IntStream items) {
		items.forEach(this::add);
		return this;
	}
	public IntPicker add(int... items) {
		return add(Arrays.stream(items));
	}
	public IntPicker add(IntCollection items) {
		return add(items.toIntArray());
	}
	public IntPicker range(int start, int end) {
		return add(IntStream.range(start, end));
	}
	private IntBinding binding;
	public IntPicker binding(IntBinding binding) {
		this.binding = binding;
		return this;
	}
	private Integer fallback;
	public IntPicker fallback(Integer fallback) {
		this.fallback = fallback;
		return this;
	}
	private IntFunction<String> naming = Integer::toString;
	public IntPicker naming(IntFunction<String> naming) {
		this.naming = naming;
		return this;
	}
	public int render() {
		if (items.isEmpty())
			throw new IllegalStateException("Picker must have at least one item.");
		List<Integer> list = items.intStream().boxed().collect(toList());
		var fallback = this.fallback != null ? this.fallback : list.get(0);
		var binding = this.binding != null ? this.binding : IntBinding.of(title);
		return new ItemPicker<Integer>().title(title).add(list).fallback(fallback).binding(binding.asData()).naming(n -> naming.apply(n)).pick();
	}
	/*
	 * Integers are rarely picked raw (sliders serve this purpose better), so only stringer overloads make sense.
	 * While range overloads are going to be used more often, array overloads need to be provided for the general case.
	 */
	public static int pickInt(String title, int[] items, int fallback, IntFunction<String> naming) {
		return new IntPicker().title(title).add(items).fallback(fallback).naming(naming).render();
	}
	public static int pickInt(String title, int[] items, IntFunction<String> naming) {
		return new IntPicker().title(title).add(items).naming(naming).render();
	}
	public static int pickInt(String title, int start, int end, int fallback, IntFunction<String> naming) {
		return new IntPicker().title(title).range(start, end).fallback(fallback).naming(naming).render();
	}
	public static int pickInt(String title, int start, int end, IntFunction<String> naming) {
		return new IntPicker().title(title).range(start, end).naming(naming).render();
	}
}
