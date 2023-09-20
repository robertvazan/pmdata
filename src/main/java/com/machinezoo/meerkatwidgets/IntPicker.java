// Part of Meerkat Widgets: https://meerkatwidgets.machinezoo.com
package com.machinezoo.meerkatwidgets;

import static java.util.stream.Collectors.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import com.machinezoo.remorabindings.*;
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
	public IntPicker range(int end) {
		return range(0, end);
	}
	public IntPicker rangeClosed(int start, int end) {
		return add(IntStream.rangeClosed(start, end));
	}
	public IntPicker() {
	}
	public IntPicker(String title) {
		this.title = title;
	}
	public IntPicker(String title, IntStream items) {
		this.title = title;
		add(items);
	}
	public IntPicker(String title, int... items) {
		this.title = title;
		add(items);
	}
	public IntPicker(String title, IntCollection items) {
		this.title = title;
		add(items);
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
	private boolean sidebar = true;
	public IntPicker sidebar(boolean sidebar) {
		this.sidebar = sidebar;
		return this;
	}
	public int pick() {
		if (items.isEmpty())
			throw new IllegalStateException("Picker must have at least one item.");
		List<Integer> list = items.intStream().boxed().collect(toList());
		var fallback = this.fallback != null ? this.fallback : list.get(0);
		var binding = this.binding != null ? this.binding : IntBinding.of(title);
		return new ItemPicker<Integer>(title, list)
			.fallback(fallback)
			.binding(binding.asData())
			.naming(n -> naming.apply(n))
			.sidebar(sidebar)
			.pick();
	}
	/*
	 * Integers are rarely picked raw (sliders serve this purpose better), so only stringer overloads make sense.
	 * Range overloads are not provided, because it would not be clear whether open or closed range is requested.
	 * Items are placed at the end in order to support ellipsis syntax.
	 */
	public static int pick(String title, int fallback, IntFunction<String> naming, int... items) {
		return new IntPicker().title(title).add(items).fallback(fallback).naming(naming).pick();
	}
	public static int pick(String title, IntFunction<String> naming, int... items) {
		return new IntPicker().title(title).add(items).naming(naming).pick();
	}
}
