// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import com.machinezoo.meerkatwidgets.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.stagean.*;

@DraftApi
public class DataSite extends SiteConfiguration {
    @Override public String language() { return "en"; }
    @Override public SitePage viewer() { return new DataPage(); }
    @Override protected void extras(SiteLocation root) {
        super.extras(root);
        root.add(new SiteLocation()
            .resources(StaticContent.class)
            .asset("defaults.css"));
    }
}
