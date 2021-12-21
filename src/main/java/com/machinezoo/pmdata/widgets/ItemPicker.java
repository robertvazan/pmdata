// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import com.machinezoo.pmdata.bindings.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
public class ItemPicker<T> {
	private String title;
	public ItemPicker<T> title(String title) {
		this.title = title;
		return this;
	}
	/*
	 * Items cannot be null, because we wouldn't be able to tell the difference
	 * between null value and uninitialized storage behind binding that should default to fallback.
	 */
	private final List<T> items = new ArrayList<>();
	public ItemPicker<T> add(T item) {
		Objects.requireNonNull(item, "Items must be non-null.");
		items.add(item);
		return this;
	}
	public ItemPicker<T> add(Stream<? extends T> items) {
		items.forEach(this::add);
		return this;
	}
	public ItemPicker<T> add(Collection<? extends T> items) {
		return add(items.stream());
	}
	/*
	 * This must be final to allow @SafeVarargs. It must be @SafeVarargs to avoid warnings.
	 */
	@SafeVarargs
	public final ItemPicker<T> add(T... items) {
		return add(Arrays.stream(items));
	}
	public ItemPicker() {
	}
	public ItemPicker(String title) {
		this.title = title;
	}
	@SafeVarargs
	public ItemPicker(String title, T... items) {
		this.title = title;
		add(items);
	}
	public ItemPicker(String title, Collection<? extends T> items) {
		this.title = title;
		add(items);
	}
	public ItemPicker(String title, Stream<? extends T> items) {
		this.title = title;
		add(items);
	}
	/*
	 * Binding is optional since we can fall back to storing labels.
	 */
	private DataBinding<T> binding;
	public ItemPicker<T> binding(DataBinding<T> binding) {
		this.binding = binding;
		return this;
	}
	/*
	 * Must never return null.
	 */
	private Function<T, String> naming = Objects::toString;
	public ItemPicker<T> naming(Function<T, String> naming) {
		Objects.requireNonNull(naming);
		this.naming = naming;
		return this;
	}
	private T fallback;
	public ItemPicker<T> fallback(T fallback) {
		this.fallback = fallback;
		return this;
	}
	private boolean sidebar = true;
	public ItemPicker<T> sidebar(boolean sidebar) {
		this.sidebar = sidebar;
		return this;
	}
	public T pick() {
		if (items.isEmpty())
			throw new IllegalStateException("Picker must have at least one item.");
		T fallback = this.fallback != null ? this.fallback : items.stream().findFirst().get();
		if (!items.contains(fallback))
			throw new IllegalStateException("Fallback value must be in the item list.");
		T current;
		Consumer<T> setter;
		if (binding != null) {
			T bound = binding.get().orElse(null);
			current = items.contains(bound) ? bound : fallback;
			setter = binding::set;
		} else {
			/*
			 * If no binding is provided, fall back to storing item labels. Title is required for this.
			 * 
			 * This forced choice between binding and storing labels in fragment's preferences
			 * prevents callers from redirecting the default label-based storage elsewhere.
			 * We don't care, because custom binding is a rare use case
			 * and we can always provide StringifiedPicker (or some such) later,
			 * perhaps even add new StringBinding field to this class.
			 */
			Objects.requireNonNull(title, "Picker must have a title or a binding.");
			var sbinding = StringBinding.of(title);
			String bound = sbinding.get().orElse(null);
			current = items.stream().filter(v -> naming.apply(v).equals(bound)).findFirst().orElse(fallback);
			setter = v -> sbinding.set(naming.apply(v));
		}
		new ContentLabel(title)
			.sidebar(sidebar)
			.add(Html.ul()
				.clazz("item-picker")
				.add(items.stream()
					.map(v -> Html.li()
						.clazz(Objects.equals(current, v) ? "item-picker-current" : null)
						.add(Html.button()
							.id(SiteFragment.get().elementId(title, naming.apply(v)))
							.onclick(() -> setter.accept(v))
							.add(naming.apply(v))))))
			.render();
		return current;
	}
	/*
	 * This picker is used for quick-n-dirty implementation, so no fancy features are added here.
	 * It requires toString() to return meaningful label that can be displayed.
	 */
	@SafeVarargs
	public static <T> T pick(String title, T... items) {
		return new ItemPicker<T>().title(title).add(items).pick();
	}
	/*
	 * These are intended for cases when the caller has or computes dynamic item collection.
	 */
	public static <T> T pick(String title, Collection<T> items, Function<T, String> naming) {
		return new ItemPicker<T>().title(title).add(items).naming(naming).pick();
	}
	public static <T> T pick(String title, Collection<T> items) {
		return new ItemPicker<T>().title(title).add(items).pick();
	}
	public static <T> T pick(String title, Stream<T> items, Function<T, String> naming) {
		return new ItemPicker<T>().title(title).add(items).naming(naming).pick();
	}
	public static <T> T pick(String title, Stream<T> items) {
		return new ItemPicker<T>().title(title).add(items).pick();
	}
}
