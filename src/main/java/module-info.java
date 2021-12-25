// Part of PMData: https://pmdata.machinezoo.com
module com.machinezoo.pmdata {
	exports com.machinezoo.pmdata;
	exports com.machinezoo.pmdata.caching;
	exports com.machinezoo.pmdata.bindings;
	exports com.machinezoo.pmdata.formatters;
	exports com.machinezoo.pmdata.widgets;
	exports com.machinezoo.pmdata.charts;
	requires java.desktop;
	requires com.machinezoo.stagean;
	/*
	 * Transitive, because we return CloseableScope from many methods.
	 * Transitivity should be removed once CloseableScope is in separate library.
	 */
	requires transitive com.machinezoo.noexception;
	/*
	 * Transitive, because several classes are used in the API: ReactiveDuration, ReactivePreferences, ...
	 */
	requires transitive com.machinezoo.hookless;
	requires com.machinezoo.hookless.servlets;
	/*
	 * Transitive, because widgets take and return DomContent.
	 */
	requires transitive com.machinezoo.pushmode;
	/*
	 * Transitive, because several classes are used in the API: SitePage, SiteFragment, ...
	 */
	requires transitive com.machinezoo.pmsite;
	/*
	 * Transitive, because kryo configuration is exposed via ThreadLocalKryo.
	 */
	requires transitive com.esotericsoftware.kryo;
	requires com.google.common;
	requires com.google.gson;
	requires com.ning.compress.lzf;
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
	 * SLF4J is pulled in transitively via noexception and hookless,
	 * but the transitive dependency will be removed in future versions of noexception.
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
}
