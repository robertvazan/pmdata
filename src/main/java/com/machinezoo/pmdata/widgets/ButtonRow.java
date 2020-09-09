// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
public class ButtonRow {
	/*
	 * We could also have method pressed(String) here that would return boolean
	 * and button code would then run in an if block, but that would not work correctly,
	 * because any error output from the button would not be persisted and shown.
	 * We aren't handling errors now, but we want to start with correct API.
	 */
	public static void add(String name, Runnable action) {
		/*
		 * Buttons are shown in a row, so we need a container element around them.
		 * Container element also makes it easy to align buttons with the text column.
		 */
		List<DomContent> children = SiteFragment.get().content().children();
		DomElement container = null;
		if (!children.isEmpty()) {
			DomContent last = children.get(children.size() - 1);
			if (last instanceof DomElement) {
				DomElement element = (DomElement)last;
				if ("button-row".equals(element.clazz()))
					container = element;
			}
		}
		if (container == null) {
			container = Html.div()
				.clazz("button-row");
			SiteFragment.get().add(container);
		}
		container
			.add(Html.button()
				.id(SiteFragment.get().elementId(name))
				.add(name)
				/*
				 * TODO: Need exception handler here. Exception should be shown until next run.
				 * We might want some progress reporting around it too if long processes end up here.
				 */
				.onclick(action));
	}
}
