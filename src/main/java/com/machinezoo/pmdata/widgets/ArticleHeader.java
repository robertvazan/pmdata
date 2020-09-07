// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import static java.util.stream.Collectors.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
@DraftCode("structure & CSS cleanup")
public class ArticleHeader {
	protected String title() {
		return SiteFragment.get().page().location().title();
	}
	private DomElement breadcrumbs() {
		var location = SiteFragment.get().page().location();
		if (location.parent() == null)
			return null;
		return Html.div()
			.clazz("article-header-breadcrumbs")
			.add(DomFragment.join(" » ", location.ancestors().stream()
				.filter(a -> !a.virtual() && a.path() != null && a.breadcrumb() != null)
				.map(a -> Html.a()
					.href(a.path())
					.add(a.breadcrumb()))
				.collect(toList())))
			.add(" » ")
			.add(location.breadcrumb());
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
