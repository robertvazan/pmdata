// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import com.machinezoo.closeablescope.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * It is sometimes efficient to create large dialogs that are more akin to tables than forms.
 * While tables might be sometimes preferable, they have issues with horizontal space and flexibility.
 * Dialog sections are more general at the cost of some verbosity.
 */
@DraftApi
public class ContentGroup {
	private String title;
	public ContentGroup title(String title) {
		this.title = title;
		return this;
	}
	private DomContent content;
	public ContentGroup add(DomContent content) {
		this.content = new DomFragment()
			.add(this.content)
			.add(content);
		return this;
	}
	public ContentGroup(String title, DomContent content) {
		this.title = title;
		this.content = content;
	}
	public ContentGroup(String title) {
		this.title = title;
	}
	public ContentGroup() {
	}
	public void render() {
		SiteFragment.get().add(Html.section()
			.clazz("group")
			.add(Html.div()
				.clazz("group-header")
				.add(title == null
					? null
					: Html.div()
						.clazz("group-title")
						.add(title)))
			.add(content));
	}
	public CloseableScope define() {
		Objects.requireNonNull(title, "Only content groups with title can be used as a nested scope.");
		var fragment = SiteFragment.get().nest(title);
		return fragment.open().andThen(() -> {
			add(fragment.content());
			render();
		});
	}
	public static CloseableScope define(String title) {
		return new ContentGroup(title).define();
	}
}
