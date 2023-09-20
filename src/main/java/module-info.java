// Part of PMData: https://pmdata.machinezoo.com
module com.machinezoo.pmdata {
    exports com.machinezoo.pmdata;
    exports com.machinezoo.pmdata.caching;
    exports com.machinezoo.pmdata.charts;
    requires java.desktop;
    requires com.machinezoo.stagean;
    requires com.machinezoo.noexception;
    requires com.machinezoo.noexception.slf4j;
    requires com.machinezoo.hookless;
    requires com.machinezoo.pushmode;
    /*
     * Transitive, because at least SitePage is used in the API.
     */
    requires transitive com.machinezoo.pmsite;
    requires com.machinezoo.ladybugformatters;
    requires com.machinezoo.meerkatwidgets;
    /*
     * Transitive, because kryo configuration is exposed via ThreadLocalKryo.
     * There should be a better solution for this.
     */
    requires transitive com.esotericsoftware.kryo;
    requires com.google.common;
    requires com.google.gson;
    requires it.unimi.dsi.fastutil;
    requires one.util.streamex;
    requires org.apache.commons.collections4;
    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires org.objenesis;
    /*
     * Transitive, because we have to accept JFreeChart objects for rendering.
     */
    requires transitive org.jfree.jfreechart;
    /*
     * SLF4J is pulled in transitively via noexception-slf4j, but it's better to be explicit.
     */
    requires org.slf4j;
    /*
     * Transitive, because we have to accept smile plot objects for rendering.
     * Smile is not using modules yet: https://github.com/haifengl/smile/issues/704
     */
    requires transitive smile.plot;
    requires batik.dom;
    requires batik.svggen;
    requires batik.awt.util;
    /*
     * JSON descriptors of caches.
     */
    opens com.machinezoo.pmdata.caching to com.google.gson;
}
