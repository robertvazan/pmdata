// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
module com.machinezoo.ladybugformatters {
    exports com.machinezoo.ladybugformatters;
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
