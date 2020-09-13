// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * Very similar to ContentLabel, but it wraps and centers the content, which is presumed to be usually text.
 */
@DraftApi
public class StaticContent {
	private String label;
	public StaticContent label(String label) {
		this.label = label;
		return this;
	}
	public StaticContent() {
	}
	public StaticContent(String label) {
		this.label = label;
	}
	/*
	 * Some content might need custom CSS.
	 */
	private String clazz;
	public StaticContent clazz(String clazz) {
		this.clazz = clazz;
		return this;
	}
	private DomContent content;
	public StaticContent add(DomContent content) {
		this.content = new DomFragment()
			.add(this.content)
			.add(content);
		return this;
	}
	public StaticContent add(String text) {
		this.content = new DomFragment()
			.add(this.content)
			.add(text);
		return this;
	}
	public StaticContent add(String format, Object... args) {
		return add(String.format(format, args));
	}
	public void render() {
		new ContentLabel(label)
			.clazz(clazz)
			.add(Html.div()
				.clazz("static-content")
				.add(content))
			.render();
	}
	public CloseableScope define() {
		var fragment = SiteFragment.forKey(SiteFragment.get().key());
		return fragment.open().andThen(() -> {
			add(fragment.content());
			render();
		});
	}
	/*
	 * Sometimes we just want to display information under a label instead of offering editable control.
	 */
	public static void show(String label, DomContent content) {
		new StaticContent(label).add(content).render();
	}
	public static void show(String label, String text) {
		new StaticContent(label).add(text).render();
	}
	public static void show(String label, String format, Object... args) {
		new StaticContent(label).add(format, args).render();
	}
}
