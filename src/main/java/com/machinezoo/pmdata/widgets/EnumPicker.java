// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import com.machinezoo.pmdata.bindings.*;
import com.machinezoo.stagean.*;

/*
 * Enums have their own specialized picker, because enums have defined string conversion
 * and thus serialization and there is a way to enumerate enum constants, which together yields neat API.
 */
@DraftApi
public class EnumPicker<T extends Enum<T>> {
	private String title;
	public EnumPicker<T> title(String title) {
		this.title = title;
		return this;
	}
	/*
	 * Only one of clazz, fallback, and items needs to be provided.
	 */
	private Class<T> clazz;
	public EnumPicker<T> clazz(Class<T> clazz) {
		this.clazz = clazz;
		return this;
	}
	private T fallback;
	public EnumPicker<T> fallback(T fallback) {
		this.fallback = fallback;
		return this;
	}
	private final List<T> items = new ArrayList<>();
	public EnumPicker<T> add(T item) {
		Objects.requireNonNull(item);
		if (!items.contains(item))
			items.add(item);
		return this;
	}
	public EnumPicker<T> add(Stream<? extends T> items) {
		items.forEach(this::add);
		return this;
	}
	public EnumPicker<T> add(Collection<? extends T> items) {
		return add(items.stream());
	}
	@SafeVarargs
	public final EnumPicker<T> add(T... items) {
		return add(Arrays.stream(items));
	}
	public EnumPicker() {
	}
	public EnumPicker(String title) {
		this.title = title;
	}
	public EnumPicker(String title, T fallback) {
		this.title = title;
		this.fallback = fallback;
	}
	public EnumPicker(String title, Class<T> clazz) {
		this.title = title;
		this.clazz = clazz;
	}
	public EnumPicker(String title, Collection<? extends T> items) {
		this.title = title;
		add(items);
	}
	public EnumPicker(String title, Stream<? extends T> items) {
		this.title = title;
		add(items);
	}
	private DataBinding<T> binding;
	public EnumPicker<T> binding(DataBinding<T> binding) {
		this.binding = binding;
		return this;
	}
	private Function<T, String> naming = Object::toString;
	public EnumPicker<T> naming(Function<T, String> naming) {
		Objects.requireNonNull(naming);
		this.naming = naming;
		return this;
	}
	private Sidebar sidebar;
	public EnumPicker<T> sidebar(Sidebar sidebar) {
		this.sidebar = sidebar;
		return this;
	}
	@SuppressWarnings("unchecked")
	public T pick() {
		if (clazz == null && fallback == null && items.isEmpty())
			throw new IllegalStateException("Enum type must be specified implicitly or explicitly.");
		T fallback = this.fallback != null ? this.fallback : !items.isEmpty() ? items.get(0) : clazz.getEnumConstants()[0];
		if (!items.isEmpty() && !items.contains(fallback))
			throw new IllegalStateException("Fallback must be in the item list.");
		var clazz = this.clazz != null ? this.clazz : (Class<T>)fallback.getClass();
		var items = !this.items.isEmpty() ? this.items : Arrays.asList(clazz.getEnumConstants());
		if (binding == null)
			binding = StringBinding.of(title).asEnum(clazz);
		return new ItemPicker<T>(title, items)
			.fallback(fallback)
			.binding(binding)
			.naming(naming)
			.sidebar(sidebar)
			.pick();
	}
	/*
	 * We will provide static methods only for simple cases where we use default toString() naming.
	 * This includes all quick-n-dirty enum pickers. In other cases, the app can just configure picker instance.
	 * We will also support only overloads with fallback as fallbacks make most sense in most situations.
	 */
	public static <T extends Enum<T>> T pick(String title, T fallback) {
		return new EnumPicker<T>().title(title).fallback(fallback).pick();
	}
	/*
	 * Enums have the advantage of type information, which we can use to maximum
	 * if we derive title from enum's type name.
	 */
	public static <T extends Enum<T>> T pick(T fallback) {
		return new EnumPicker<T>().title(fallback.getClass().getSimpleName()).fallback(fallback).pick();
	}
}
