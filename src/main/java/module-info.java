// Part of Fox Cache: https://foxcache.machinezoo.com
module com.machinezoo.pmdata {
    exports com.machinezoo.foxcache;
    requires com.machinezoo.stagean;
    /*
     * Transitive, because certain semi-internal but public APIs return CloseableScope.
     */
    requires transitive com.machinezoo.closeablescope;
    /*
     * Transitive, because we expose silencing handler for empty cache exception.
     */
    requires transitive com.machinezoo.noexception;
    requires com.machinezoo.noexception.slf4j;
    requires com.machinezoo.hookless;
    requires com.machinezoo.ladybugformatters;
    requires com.machinezoo.meerkatwidgets;
    /*
     * Transitive, because kryo configuration is exposed via ThreadLocalKryo.
     * There should be a better solution for this.
     */
    requires transitive com.esotericsoftware.kryo;
    requires com.google.gson;
    requires one.util.streamex;
    requires org.objenesis;
    requires org.slf4j;
    /*
     * JSON descriptors of caches.
     */
    opens com.machinezoo.foxcache to com.google.gson;
}
