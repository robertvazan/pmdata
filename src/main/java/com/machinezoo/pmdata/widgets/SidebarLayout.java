// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import java.util.function.*;
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
 */
@DraftApi
public class SidebarLayout {
	private final SiteFragment main;
	public SiteFragment main() {
		return main;
	}
	private final SiteFragment left;
	public SiteFragment left() {
		return left;
	}
	private final SiteFragment right;
	public SiteFragment right() {
		return right;
	}
	private SidebarLayout(SiteFragment main, SiteFragment left, SiteFragment right) {
		this.main = main;
		this.left = left;
		this.right = right;
	}
	private SidebarLayout(SiteFragment parent) {
		this(parent.isolate(), parent.isolate(), parent.isolate());
	}
	public SidebarLayout() {
		this(SiteFragment.get());
	}
	private static final ThreadLocal<SidebarLayout> current = new ThreadLocal<>();
	public static Optional<SidebarLayout> current() {
		var layout = current.get();
		if (layout == null)
			return Optional.empty();
		var fragment = SiteFragment.current().orElse(null);
		/*
		 * Do not return the current sidebar layout if current fragment is not part of the layout.
		 * This happens when sidebar layout is hidden below nested fragment.
		 * Returning the sidebar layout in this situation would allow widgets to escape isolation.
		 */
		if (fragment != layout.main && fragment != layout.left && fragment != layout.right)
			return Optional.empty();
		return Optional.of(layout);
	}
	public static SidebarLayout get() {
		return current().orElseGet(() -> {
			/*
			 * If there is no current sidebar layout (or there is a nested SiteFragment over it),
			 * return fake instance that just references current SiteFragment from all three columns.
			 * This will cause widgets to render into current SiteFragment instead.
			 */
			var fragment = SiteFragment.get();
			return new SidebarLayout(fragment, fragment, fragment);
		});
	}
	public CloseableScope open() {
		var outer = current.get();
		current.set(this);
		var fragmentScope = main.open();
		return () -> {
			fragmentScope.close();
			current.set(outer);
		};
	}
	public void render() {
		SiteFragment.get().add(Html.div()
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
	public CloseableScope define() {
		var scope = open();
		return () -> {
			scope.close();
			render();
		};
	}
	public static <T> T supplyLeft(Supplier<T> supplier) {
		try (var scope = SidebarLayout.get().left().open()) {
			return supplier.get();
		}
	}
	public static <T> T supplyRight(Supplier<T> supplier) {
		try (var scope = SidebarLayout.get().right().open()) {
			return supplier.get();
		}
	}
}
