// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import static java.util.stream.Collectors.*;
import java.util.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
@DraftCode("structure & CSS cleanup")
public class ArticleHeader {
	private boolean showClass;
	public ArticleHeader showClass(boolean showClass) {
		this.showClass = showClass;
		return this;
	}
	private final List<Class<?>> ignoredClasses = new ArrayList<>();
	public ArticleHeader ignoreClass(Class<?> clazz) {
		ignoredClasses.add(clazz);
		return this;
	}
	protected String title() {
		return SiteFragment.get().page().location().title();
	}
	private DomElement breadcrumbs() {
		var page = SiteFragment.get().page();
		var location = page.location();
		if (location.parent() == null)
			return null;
		var crumbs = Html.div()
			.clazz("article-header-breadcrumbs")
			.add(DomFragment.join(" » ", location.ancestors().stream()
				.filter(a -> !a.virtual() && a.path() != null && a.breadcrumb() != null)
				.map(a -> Html.a()
					.href(a.path())
					.add(a.breadcrumb()))
				.collect(toList())))
			.add(" » ")
			.add(location.breadcrumb());
		if (showClass && !ignoredClasses.contains(page.getClass())) {
			crumbs
				.add(" (")
				.add(Html.code()
					.add(page.getClass().getSimpleName()))
				.add(")");
		}
		return crumbs;
	}
	public void render() {
		SiteFragment.get()
			.add(Html.header()
				.add(Html.h1()
					.add(title()))
				.add(Html.div()
					.clazz("article-header-details")
					.add(breadcrumbs())));
	}
	public void register(SiteTemplate template) {
		template.register("header", this::render);
	}
}
