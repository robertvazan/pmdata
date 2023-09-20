// Part of Meerkat Widgets: https://meerkatwidgets.machinezoo.com
package com.machinezoo.meerkatwidgets;

import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;

public class LinkButton {
	private String title;
	public LinkButton title(String title) {
		this.title = title;
		return this;
	}
	public LinkButton() {
	}
	public LinkButton(String title) {
		this.title = title;
	}
	private Runnable action;
	public LinkButton handle(Runnable action) {
		this.action = action;
		return this;
	}
	public DomElement html() {
		return Html.button()
			.id(SiteFragment.get().elementId(title))
			.clazz("link-button")
			.add(title)
			.onclick(action);
	}
	public void render() {
		SiteFragment.get().add(html());
	}
}
