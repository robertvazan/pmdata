// Part of Meerkat Widgets: https://meerkatwidgets.machinezoo.com
package com.machinezoo.meerkatwidgets;

import com.machinezoo.closeablescope.*;
import com.machinezoo.ladybugformatters.*;
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
	public StaticContent add(Object data) {
		return add(Pretty.object().format(data));
	}
	private Tone tone;
	public StaticContent tone(Tone tone) {
		this.tone = tone;
		return this;
	}
	private boolean sidebar = true;
	public StaticContent sidebar(boolean sidebar) {
		this.sidebar = sidebar;
		return this;
	}
	public void render() {
		new ContentLabel(label)
			.sidebar(sidebar)
			.clazz(clazz)
			.add(Html.div()
				.clazz("static-content", tone != null ? tone.css() : null)
				.add(content))
			.render();
	}
	public CloseableScope define() {
		var fragment = SiteFragment.get().isolate();
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
	public static void show(String label, Object data) {
		new StaticContent(label).add(data).render();
	}
}
