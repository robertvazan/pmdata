// Part of PMData: https://pmdata.machinezoo.com
module com.machinezoo.pmdata {
    exports com.machinezoo.pmdata.widgets;
    requires java.desktop;
    requires com.machinezoo.stagean;
    /*
     * Transitive, because block widgets use CloseableScope.
     */
    requires transitive com.machinezoo.closeablescope;
    requires com.machinezoo.noexception;
    /*
     * Transitive, because widgets accept and return DomContent.
     */
    requires transitive com.machinezoo.pushmode;
    /*
     * Transitive, because several classes are used in the API: SitePage, SiteFragment, ...
     */
    requires transitive com.machinezoo.pmsite;
    requires com.machinezoo.ladybugformatters;
    /*
     * Transitive, because widgets expose their bindings.
     */
    requires transitive com.machinezoo.remorabindings;
    requires it.unimi.dsi.fastutil;
    requires one.util.streamex;
}
