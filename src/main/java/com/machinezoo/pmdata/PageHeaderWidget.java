// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.net.*;
import java.util.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi("drop the subtitle feature")
public class PageHeaderWidget {
	private final SitePage page;
	public PageHeaderWidget(SitePage page) {
		Objects.requireNonNull(page);
		this.page = page;
		title = page.site().title();
	}
	private String currentPath() {
		return Optional.ofNullable(Exceptions.sneak().get(() -> new URI(page.request().url())).getPath()).orElse("/");
	}
	private SiteLocation currentLocation() {
		return page.location();
	}
	private String title;
	public PageHeaderWidget title(String title) {
		this.title = title;
		return this;
	}
	private String subtitle;
	public PageHeaderWidget subtitle(String subtitle) {
		this.subtitle = subtitle;
		return this;
	}
	private String root = "/";
	public PageHeaderWidget root(String root) {
		this.root = root;
		return this;
	}
	private String self = "Home";
	public PageHeaderWidget self(String self) {
		this.self = self;
		return this;
	}
	private class MenuItem {
		final String path;
		final String text;
		final String pattern;
		MenuItem(String path, String text, String pattern) {
			this.path = path;
			this.text = text;
			this.pattern = pattern;
		}
		boolean active() {
			if (currentLocation() != null && currentLocation().ancestors().stream().anyMatch(l -> l.path().equals(path)))
				return true;
			if (pattern != null && currentPath().matches(pattern))
				return true;
			return false;
		}
	}
	private final List<MenuItem> items = new ArrayList<>();
	public PageHeaderWidget menu(String path, String text) {
		items.add(new MenuItem(path, text, path));
		return this;
	}
	public PageHeaderWidget menu(String path, String text, String pattern) {
		items.add(new MenuItem(path, text, pattern));
		return this;
	}
	public DomElement html() {
		return Html.header()
			.add(Html.ul()
				.add(Html.li()
					.add(Html.a()
						.href(subtitle != null ? "/" : root)
						.add(title))
					.add(subtitle == null ? null : new DomFragment()
						.add(Html.span().add("»"))
						.add(Html.a()
							.href(root)
							.add(subtitle))))
				.add(items.isEmpty() || self == null ? null : Html.li()
					.add(Html.a()
						.href(root)
						.clazz(currentPath().equals(root) ? "page-header-active" : null)
						.add(self)))
				.add(items.stream()
					.map(m -> Html.li()
						.add(Html.a()
							.href(m.path)
							.clazz(m.active() ? "page-header-active" : null)
							.add(m.text)))));
	}
}
