// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import static java.util.stream.Collectors.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
@DraftCode("structure & CSS cleanup")
public class ArticleHeaderBinding extends SiteBinding {
	@Override
	public String name() {
		return "header";
	}
	@Override
	public DomContent expand(SiteBindingContext context) {
		return Html.header()
			.add(Html.h1()
				.add(title(context)))
			.add(Html.div()
				.clazz("article-header-details")
				.add(breadcrumbs(context.page().location())));
	}
	protected String title(SiteBindingContext context) {
		return context.page().location().title();
	}
	private DomElement breadcrumbs(SiteLocation location) {
		if (location == null || location.parent() == null)
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
}
