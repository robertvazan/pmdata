// Part of Remora Bindings: https://remorabindings.machinezoo.com
module com.machinezoo.remorabindings {
    exports com.machinezoo.remorabindings;
    requires com.machinezoo.stagean;
    requires com.machinezoo.noexception;
    /*
     * Reactive, because we accept arbitrary ReactivePreferences.
     */
    requires transitive com.machinezoo.hookless.prefs;
    requires com.machinezoo.pmsite;
}
