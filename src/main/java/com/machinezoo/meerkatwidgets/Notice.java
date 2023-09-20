// Part of Meerkat Widgets: https://meerkatwidgets.machinezoo.com
package com.machinezoo.meerkatwidgets;

import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * Wikipedia-like notices. We preferentially support the same levels as in logging: info, warning, error.
 */
@DraftApi
public class Notice {
	private String key;
	public Notice key(String key) {
		this.key = key;
		return this;
	}
	private Tone tone = Tone.INFO;
	public Notice tone(Tone tone) {
		this.tone = tone;
		return this;
	}
	private DomContent content;
	public Notice content(DomContent content) {
		this.content = content;
		return this;
	}
	public Notice text(String text) {
		return content(new DomText(text));
	}
	public Notice format(String format, Object... args) {
		return text(String.format(format, args));
	}
	public void render() {
		SiteFragment.get()
			.add(Html.aside()
				.key(key)
				.clazz("notice", tone.css())
				.add(content));
	}
	public static void show(Tone tone, DomContent content) {
		new Notice()
			.tone(tone)
			.content(content)
			.render();
	}
	public static void show(Tone tone, String text) {
		new Notice()
			.tone(tone)
			.text(text)
			.render();
	}
	public static void show(Tone tone, String format, Object... args) {
		new Notice()
			.tone(tone)
			.format(format, args)
			.render();
	}
	public static void info(DomContent content) {
		show(Tone.INFO, content);
	}
	public static void info(String text) {
		show(Tone.INFO, text);
	}
	public static void info(String format, Object... args) {
		show(Tone.INFO, format, args);
	}
	public static void warn(DomContent content) {
		show(Tone.WARNING, content);
	}
	public static void warn(String text) {
		show(Tone.WARNING, text);
	}
	public static void warn(String format, Object... args) {
		show(Tone.WARNING, format, args);
	}
	public static void fail(DomContent content) {
		show(Tone.FAILURE, content);
	}
	public static void fail(String text) {
		show(Tone.FAILURE, text);
	}
	public static void fail(String format, Object... args) {
		show(Tone.FAILURE, format, args);
	}
}
