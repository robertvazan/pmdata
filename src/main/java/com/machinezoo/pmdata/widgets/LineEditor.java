// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.remorabindings.*;
import com.machinezoo.stagean.*;

@DraftApi
public class LineEditor {
	public LineEditor() {
	}
	private String title;
	public LineEditor title(String title) {
		this.title = title;
		return this;
	}
	public LineEditor(String title) {
		this.title = title;
	}
	private StringBinding binding;
	public LineEditor binding(StringBinding binding) {
		this.binding = binding;
		return this;
	}
	/*
	 * Text editor API guarantees non-null return no matter what parameters are passed in.
	 */
	private String fallback = "";
	public LineEditor fallback(String fallback) {
		Objects.requireNonNull(fallback);
		this.fallback = fallback;
		return this;
	}
	private boolean sidebar = true;
	public LineEditor sidebar(boolean sidebar) {
		this.sidebar = sidebar;
		return this;
	}
	public String edit() {
		var binding = this.binding != null ? this.binding : StringBinding.of(title);
		new ContentLabel(title)
			.sidebar(sidebar)
			.add(Html.input()
				.clazz("line-editor")
				.id(SiteFragment.get().elementId(title))
				.type("text")
				.value(binding.get().orElse(fallback), binding::set))
			.render();
		return binding.get().orElse(fallback);
	}
	public static String edit(String title) {
		return edit(title, "");
	}
	public static String edit(String title, String fallback) {
		return new LineEditor().title(title).fallback(fallback).edit();
	}
}
