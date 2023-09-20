// Part of Meerkat Widgets: https://meerkatwidgets.machinezoo.com
import com.machinezoo.stagean.*;
@ApiIssue("We need a better way to expose CSS that does not also expose everything else in the package.")
module com.machinezoo.meerkatwidgets {
    exports com.machinezoo.meerkatwidgets;
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
    /*
     * Predefined CSS in resources.
     */
    opens com.machinezoo.meerkatwidgets;
}
