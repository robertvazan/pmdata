// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import org.apache.commons.lang3.*;
import org.apache.commons.lang3.tuple.*;
import com.machinezoo.stagean.*;

/*
 * Alternative to EnumPicker for cases when one of several renderers or other Runnables is chosen.
 */
@DraftApi
public class RunnablePicker implements Runnable {
	private final String title;
	public RunnablePicker(String title) {
		Objects.requireNonNull(title);
		this.title = title;
	}
	private final List<Pair<String, Runnable>> items = new ArrayList<>();
	public RunnablePicker add(String label, Runnable runnable) {
		Objects.requireNonNull(label);
		Objects.requireNonNull(runnable);
		Validate.isTrue(items.stream().noneMatch(p -> p.getKey().equals(label)), "Duplicate item label.");
		items.add(Pair.of(label, runnable));
		return this;
	}
	/*
	 * This has to be called last, because we are immediately checking whether the label exists.
	 */
	private String fallback;
	public RunnablePicker fallback(String label) {
		if (label == null)
			fallback = null;
		else
			fallback = items.stream().map(p -> p.getKey()).filter(l -> l.equals(label)).findFirst().orElseThrow();
		return this;
	}
	private boolean sidebar = true;
	public RunnablePicker sidebar(boolean sidebar) {
		this.sidebar = sidebar;
		return this;
	}
	public Runnable runnable() {
		var label = new ItemPicker<String>(title)
			.add(items.stream().map(p -> p.getKey()))
			.fallback(fallback)
			.sidebar(sidebar)
			.pick();
		return items.stream().filter(p -> p.getKey().equals(label)).findFirst().get().getValue();
	}
	@Override
	public void run() {
		runnable().run();
	}
}
