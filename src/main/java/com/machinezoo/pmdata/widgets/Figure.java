// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * Figure may contain multiple items. Using try-with-resources for it is a neat way to allow that.
 * Caption may contain links and what not, so we allow arbitrary DomContent in it.
 */
@DraftApi
public class Figure {
	private DomContent caption;
	public Figure caption(DomContent caption) {
		this.caption = caption;
		return this;
	}
	public Figure caption(String caption) {
		/*
		 * Make sure not to wrap null, which signals no caption at all.
		 */
		return caption(caption != null ? new DomText(caption) : null);
	}
	private DomContent content;
	public Figure add(DomContent content) {
		this.content = new DomFragment()
			.add(this.content)
			.add(content);
		return this;
	}
	public Figure(DomContent caption, DomContent content) {
		this.caption = caption;
		this.content = content;
	}
	public Figure(DomContent caption) {
		this.caption = caption;
	}
	public Figure(String caption) {
		caption(caption);
	}
	public Figure() {
	}
	public void render() {
		/*
		 * If caption is null, inline the content. This allows callers to have content optionally wrapped in figure
		 * if caption is provided without extra conditions.
		 */
		SiteFragment.get().add(caption == null ? content : Html.figure()
			.add(content)
			.add(Html.figcaption().add(caption)));
	}
	public CloseableScope define() {
		/*
		 * Do not nest new fragment. Content of figure should appear under the same fragment key as surrounding content.
		 * We will just create new empty fragment for the same key, so that we can capture content in it.
		 */
		var fragment = SiteFragment.forKey(SiteFragment.get().key());
		return fragment.open().andThen(() -> {
			add(fragment.content());
			render();
		});
	}
	public static CloseableScope define(DomContent caption) {
		return new Figure(caption).define();
	}
	public static CloseableScope define(String caption) {
		return new Figure(caption).define();
	}
}
