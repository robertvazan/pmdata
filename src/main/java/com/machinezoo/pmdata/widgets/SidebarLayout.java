// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * Simple API that allows throwing widgets to the left and right of the main content.
 * This is intended to make good use of horizontally oriented displays.
 * It will fall back to vertical layout on small screens.
 * 
 * There's currently no support for left-only or right-only layout as that adds a lot of complexity.
 * Wide content (tables) in the main column will have scrollbar if it supports one.
 * 
 * This object is AutoCloseable for use in try-with-resources.
 * That may be inconvenient in rare cases, but it makes for concise and simple API in most cases.
 */
@DraftApi
public class SidebarLayout implements AutoCloseable {
	private final SiteFragment parent = SiteFragment.get();
	private final SiteFragment main = parent.nest("main");
	private final SiteFragment left = parent.nest("left");
	private final SiteFragment right = parent.nest("right");
	private CloseableScope current;
	public SidebarLayout() {
		current = main.open();
	}
	private void select(SiteFragment fragment) {
		current.close();
		current = fragment.open();
	}
	public void main() {
		select(main);
	}
	public void left() {
		select(left);
	}
	public void right() {
		select(right);
	}
	@Override
	public void close() {
		current.close();
		parent.add(Html.div()
			.clazz("sidebar-container")
			.add(Html.div()
				.clazz("sidebar-left")
				.add(Html.div()
					.clazz("sidebar-left-box")
					.add(left.content())))
			.add(Html.div()
				.clazz("sidebar-right")
				.add(Html.div()
					.clazz("sidebar-right-box")
					.add(right.content())))
			.add(Html.div()
				.clazz("sidebar-main")
				.add(main.content())));
	}
}
