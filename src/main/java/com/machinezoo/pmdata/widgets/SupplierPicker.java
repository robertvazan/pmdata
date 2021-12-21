// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import java.util.function.*;
import org.apache.commons.lang3.*;
import org.apache.commons.lang3.tuple.*;
import com.machinezoo.stagean.*;

/*
 * Alternative to EnumPicker for cases when the enum is only used to pick one of several complex objects.
 */
@DraftApi
public class SupplierPicker<T> {
	private final String title;
	public SupplierPicker(String title) {
		Objects.requireNonNull(title);
		this.title = title;
	}
	private final List<Pair<String, Supplier<T>>> items = new ArrayList<>();
	public SupplierPicker<T> add(String label, Supplier<T> supplier) {
		Objects.requireNonNull(label);
		Objects.requireNonNull(supplier);
		Validate.isTrue(items.stream().noneMatch(p -> p.getKey().equals(label)), "Duplicate item label.");
		items.add(Pair.of(label, supplier));
		return this;
	}
	/*
	 * This has to be called last, because we are immediately checking whether the label exists.
	 */
	private String fallback;
	public SupplierPicker<T> fallback(String label) {
		if (label == null)
			fallback = null;
		else
			fallback = items.stream().map(p -> p.getKey()).filter(l -> l.equals(label)).findFirst().orElseThrow();
		return this;
	}
	private boolean sidebar = true;
	public SupplierPicker<T> sidebar(boolean sidebar) {
		this.sidebar = sidebar;
		return this;
	}
	public Supplier<T> supplier() {
		var label = new ItemPicker<String>(title)
			.add(items.stream().map(p -> p.getKey()))
			.fallback(fallback)
			.sidebar(sidebar)
			.pick();
		return items.stream().filter(p -> p.getKey().equals(label)).findFirst().get().getValue();
	}
	public T pick() {
		return supplier().get();
	}
}
