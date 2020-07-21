// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.*;
import com.google.common.base.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pmsite.preferences.*;
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
	private final Supplier<PreferenceStorage> preferences =
		Suppliers.memoize(() -> PreferenceStorage.chained(super.preferences(), new MapDbPreferences()).group(getClass()).pinned());
	@Override
	public PreferenceStorage preferences() {
		return preferences.get();
	}
	@DraftCode
	@Override
	public Stream<String> css() {
		return Stream
			/*
			 * File normalize.css should be in resources and uploaded to CDN on demand.
			 */
			.of(super.css(), Stream.of("https://cdn.machinezoo.com/lib/normalize-css/4.1.1/normalize.css", "/defaults.css"), Dialog.style())
			.flatMap(Function.identity());
	}
	@Override
	protected SiteTemplate templateSetup() {
		return super.templateSetup()
			.bind(new ArticleHeaderBinding())
			.bind(DevelopmentStageBinding.stub())
			.bind(DevelopmentStageBinding.draft());
	}
}
