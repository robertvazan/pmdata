// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import com.machinezoo.pmdata.bindings.*;
import com.machinezoo.stagean.*;

/*
 * Variation of EnumPicker that allows empty Optional result.
 */
@DraftApi
public class OptionalEnumPicker<T extends Enum<T>> {
	private String title;
	public OptionalEnumPicker<T> title(String title) {
		this.title = title;
		return this;
	}
	/*
	 * Only one of clazz, fallback, and subset needs to be provided.
	 */
	private Class<T> clazz;
	public OptionalEnumPicker<T> clazz(Class<T> clazz) {
		this.clazz = clazz;
		return this;
	}
	private Optional<T> fallback = Optional.empty();
	public OptionalEnumPicker<T> fallback(T fallback) {
		this.fallback = Optional.ofNullable(fallback);
		return this;
	}
	private final List<T> items = new ArrayList<>();
	public OptionalEnumPicker<T> add(T item) {
		Objects.requireNonNull(item);
		if (!items.contains(item))
			items.add(item);
		return this;
	}
	public OptionalEnumPicker<T> add(Stream<? extends T> items) {
		items.forEach(this::add);
		return this;
	}
	public OptionalEnumPicker<T> add(Collection<? extends T> items) {
		return add(items.stream());
	}
	@SafeVarargs
	public final OptionalEnumPicker<T> add(T... items) {
		return add(Arrays.stream(items));
	}
	public OptionalEnumPicker() {
	}
	public OptionalEnumPicker(String title) {
		this.title = title;
	}
	public OptionalEnumPicker(String title, T fallback) {
		this.title = title;
		fallback(fallback);
	}
	public OptionalEnumPicker(String title, Class<T> clazz) {
		this.title = title;
		this.clazz = clazz;
	}
	public OptionalEnumPicker(String title, Collection<? extends T> items) {
		this.title = title;
		add(items);
	}
	public OptionalEnumPicker(String title, Stream<? extends T> items) {
		this.title = title;
		add(items);
	}
	@SafeVarargs
	public OptionalEnumPicker(String title, T... items) {
		this.title = title;
		add(items);
	}
	private DataBinding<Optional<T>> binding;
	public OptionalEnumPicker<T> binding(DataBinding<Optional<T>> binding) {
		this.binding = binding;
		return this;
	}
	private Function<T, String> naming = Object::toString;
	public OptionalEnumPicker<T> naming(Function<T, String> naming) {
		Objects.requireNonNull(naming);
		this.naming = naming;
		return this;
	}
	private boolean sidebar = true;
	public OptionalEnumPicker<T> sidebar(boolean sidebar) {
		this.sidebar = sidebar;
		return this;
	}
	@SuppressWarnings("unchecked")
	public Optional<T> pick() {
		if (clazz == null && fallback.isEmpty() && items.isEmpty())
			throw new IllegalStateException("Enum type must be specified implicitly or explicitly.");
		if (fallback.isPresent() && !items.isEmpty() && !items.contains(fallback.get()))
			throw new IllegalStateException("Fallback must be in the item list.");
		var clazz = this.clazz != null ? this.clazz : fallback.isPresent() ? (Class<T>)fallback.get().getClass() : (Class<T>)items.get(0).getClass();
		var items = !this.items.isEmpty() ? this.items : Arrays.asList(clazz.getEnumConstants());
		if (binding == null)
			binding = StringBinding.of(title).asOptionalEnum(clazz);
		return new OptionalPicker<T>(title, items)
			.fallback(fallback.orElse(null))
			.binding(binding)
			.naming(naming)
			.sidebar(sidebar)
			.pick();
	}
	public static <T extends Enum<T>> Optional<T> pick(String title, Class<T> clazz) {
		return new OptionalEnumPicker<T>().title(title).clazz(clazz).pick();
	}
	public static <T extends Enum<T>> Optional<T> pick(String title, T fallback) {
		return new OptionalEnumPicker<T>().title(title).fallback(fallback).pick();
	}
	public static <T extends Enum<T>> Optional<T> pick(Class<T> clazz) {
		return new OptionalEnumPicker<T>().title(clazz.getSimpleName()).clazz(clazz).pick();
	}
	public static <T extends Enum<T>> Optional<T> pick(T fallback) {
		return new OptionalEnumPicker<T>().title(fallback.getClass().getSimpleName()).fallback(fallback).pick();
	}
}
