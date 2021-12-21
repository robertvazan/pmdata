// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * Simple API that allows throwing widgets in a sidebar next to the main content.
 * This is intended to make good use of horizontally oriented displays.
 * It will fall back to vertical layout on small screens.
 * Wide main content (tables) will have scrollbar if it supports one.
 */
@DraftApi
public class SidebarLayout {
	private final SiteFragment main;
	public SiteFragment main() {
		return main;
	}
	private final SiteFragment sidebar;
	public SiteFragment sidebar() {
		return sidebar;
	}
	public SiteFragment in(boolean sidebar) {
		return sidebar ? this.sidebar : main;
	}
	private SidebarLayout(SiteFragment main, SiteFragment sidebar) {
		this.main = main;
		this.sidebar = sidebar;
	}
	private SidebarLayout(SiteFragment parent) {
		this(parent.isolate(), parent.isolate());
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
		if (fragment != layout.main && fragment != layout.sidebar)
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
			return new SidebarLayout(fragment, fragment);
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
				.clazz("sidebar-itself")
				.add(sidebar.content()))
			.add(Html.div()
				.clazz("sidebar-main")
				.add(main.content()))
			.add(Html.div()
				.clazz("sidebar-counterbalance")));
	}
	public CloseableScope define() {
		var scope = open();
		return () -> {
			scope.close();
			render();
		};
	}
}
