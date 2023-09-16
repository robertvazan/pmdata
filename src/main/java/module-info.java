// Part of PMData: https://pmdata.machinezoo.com
module com.machinezoo.pmdata {
    exports com.machinezoo.pmdata.formatters;
    requires com.machinezoo.stagean;
    /*
     * Transitive, because we have reactive time formatters.
     */
    requires transitive com.machinezoo.hookless.time;
    /*
     * Transitive, because formatters return DomContent.
     */
    requires transitive com.machinezoo.pushmode;
}
