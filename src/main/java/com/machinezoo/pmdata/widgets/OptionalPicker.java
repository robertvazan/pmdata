// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import com.machinezoo.pmdata.bindings.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * Variation of ItemPicker that allows empty Optional result.
 */
@DraftApi
public class OptionalPicker<T> {
	private String title;
	public OptionalPicker<T> title(String title) {
		this.title = title;
		return this;
	}
	private final List<T> items = new ArrayList<>();
	public OptionalPicker<T> add(T item) {
		Objects.requireNonNull(item, "Items must be non-null.");
		items.add(item);
		return this;
	}
	public OptionalPicker<T> add(Stream<? extends T> items) {
		items.forEach(this::add);
		return this;
	}
	public OptionalPicker<T> add(Collection<? extends T> items) {
		return add(items.stream());
	}
	@SafeVarargs
	public final OptionalPicker<T> add(T... items) {
		return add(Arrays.stream(items));
	}
	public OptionalPicker() {
	}
	public OptionalPicker(String title) {
		this.title = title;
	}
	@SafeVarargs
	public OptionalPicker(String title, T... items) {
		this.title = title;
		add(items);
	}
	public OptionalPicker(String title, Collection<? extends T> items) {
		this.title = title;
		add(items);
	}
	public OptionalPicker(String title, Stream<? extends T> items) {
		this.title = title;
		add(items);
	}
	private DataBinding<Optional<T>> binding;
	public OptionalPicker<T> binding(DataBinding<Optional<T>> binding) {
		this.binding = binding;
		return this;
	}
	private Function<T, String> naming = Objects::toString;
	public OptionalPicker<T> naming(Function<T, String> naming) {
		Objects.requireNonNull(naming);
		this.naming = naming;
		return this;
	}
	private Optional<T> fallback = Optional.empty();
	public OptionalPicker<T> fallback(T fallback) {
		this.fallback = Optional.ofNullable(fallback);
		return this;
	}
	public Optional<T> pick() {
		if (fallback.isPresent() && !items.contains(fallback))
			throw new IllegalStateException("Fallback value must be in the item list.");
		Optional<T> current;
		if (binding != null) {
			Optional<T> bound = binding.get().orElse(null);
			if (bound == null)
				current = fallback;
			else if (bound.isEmpty())
				current = Optional.empty();
			else if (items.contains(bound.get()))
				current = bound;
			else
				current = fallback;
		} else {
			Objects.requireNonNull(title, "Picker must have a title or a binding.");
			String bound = StringBinding.of(title).get().orElse(null);
			if (bound == null)
				current = fallback;
			else if (bound.isEmpty())
				current = Optional.empty();
			else
				current = items.stream().filter(v -> naming.apply(v).equals(bound)).findFirst().or(() -> fallback);
		}
		new ContentLabel(title)
			.add(Html.ul()
				.clazz("item-picker")
				.add(Html.li()
					.clazz("item-picker-none", current.isEmpty() ? "item-picker-current" : null)
					.add(Html.button()
						.id(SiteFragment.get().elementId(title, "empty"))
						.onclick(() -> {
							if (binding != null)
								binding.set(Optional.empty());
							else
								StringBinding.of(title).set("");
						})
						.add(Svg.svg()
							.viewBox("-10 -10 20 20")
							.add(Svg.line()
								.x1(10)
								.y1(-10)
								.x2(-10)
								.y2(10)
								.stroke("black")
								.strokeWidth(3))
							.add(Svg.line()
								.x1(-10)
								.y1(-10)
								.x2(10)
								.y2(10)
								.stroke("black")
								.strokeWidth(3)))))
				.add(items.stream()
					.map(v -> Html.li()
						.clazz(Objects.equals(current.orElse(null), v) ? "item-picker-current" : null)
						.add(Html.button()
							.id(SiteFragment.get().elementId(title, "item", naming.apply(v)))
							.onclick(() -> {
								if (binding != null)
									binding.set(Optional.of(v));
								else
									StringBinding.of(title).set(naming.apply(v));
							})
							.add(naming.apply(v))))))
			.render();
		return current;
	}
	@SafeVarargs
	public static <T> Optional<T> pick(String title, T... items) {
		return new OptionalPicker<T>().title(title).add(items).pick();
	}
	public static <T> Optional<T> pick(String title, Collection<T> items, Function<T, String> naming) {
		return new OptionalPicker<T>().title(title).add(items).naming(naming).pick();
	}
	public static <T> Optional<T> pick(String title, Collection<T> items) {
		return new OptionalPicker<T>().title(title).add(items).pick();
	}
	public static <T> Optional<T> pick(String title, Stream<T> items, Function<T, String> naming) {
		return new OptionalPicker<T>().title(title).add(items).naming(naming).pick();
	}
	public static <T> Optional<T> pick(String title, Stream<T> items) {
		return new OptionalPicker<T>().title(title).add(items).pick();
	}
}
