// Part of PMData: https://pmdata.machinezoo.com
module com.machinezoo.pmdata {
    exports com.machinezoo.pmdata.bindings;
    requires com.machinezoo.stagean;
    requires com.machinezoo.noexception;
    /*
     * Reactive, because we accept arbitrary ReactivePreferences.
     */
    requires transitive com.machinezoo.hookless.prefs;
    requires com.machinezoo.pmsite;
}
