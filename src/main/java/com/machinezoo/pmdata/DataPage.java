// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.util.stream.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.stagean.*;

@DraftApi
public class DataPage extends SitePage {
	@Override
	public DataSite site() {
		SiteConfiguration site = super.site();
		if (site != null)
			return (DataSite)site;
		return null;
	}
	@DraftCode
	@Override
	public Stream<String> css() {
		/*
		 * File normalize.css should be in resources and uploaded to CDN on demand.
		 */
		return Stream.concat(super.css(), Stream.of("https://cdn.machinezoo.com/lib/normalize-css/4.1.1/normalize.css", "/defaults.css"));
	}
	@Override
	protected void widgets(SiteTemplate template) {
		super.widgets(template);
		DataWidgets.register(this, template);
		new ArticleHeaderWidget().register(template);
		DevelopmentStageWidget.registerAll(template);
	}
}
