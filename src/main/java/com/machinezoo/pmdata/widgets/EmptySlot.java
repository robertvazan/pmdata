// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;

/*
 * It is possible to mark a point in the dialog where content will be inserted later.
 * This makes visible order of dialog items independent of construction order.
 */
public class EmptySlot {
	/*
	 * This functionality can be accomplished in several ways. There's no perfect solution.
	 * We could just remember current position in the SiteFragment and then insert at this position later,
	 * but that would fail when earlier empty widgets get substituted and our insertion point moves.
	 * We can add a marker element (probably TEMPLATE as it has no effect on rendering if left in place),
	 * then search for it and replace it, which would work well while SiteFragment's content is still being updated,
	 * but once SiteFragment's content is consumed (e.g. by merging it into parent SiteFragment)
	 * our writes to SiteFragment's content will not reach the user.
	 * We will have to use auxiliary container element and leave it in generated HTML.
	 * The least semantic elements are DIV and SPAN. Calling code will have to pick the more suitable one.
	 * We can minimize impact on page layout with "display: contents" in CSS.
	 */
	private final DomElement content;
	public DomElement content() {
		return content;
	}
	private EmptySlot(DomElement container) {
		this.content = container;
		SiteFragment.get().add(content);
	}
	public static EmptySlot block() {
		return new EmptySlot(Html.div().clazz("transparent-container"));
	}
	public static EmptySlot inline() {
		return new EmptySlot(Html.span().clazz("transparent-container"));
	}
}
