// Part of PMData: https://pmdata.machinezoo.com
/*
 * This is narrowly specialized charting API to be used in data science.
 * The API is designed to be as concise as possible and to render into SiteFragment.
 * 
 * There are several libraries producing charts. This class chooses best one for every chart type.
 * Currently we only support JFreeChart and Smile. Here are reasons for exclusion of others:
 * - Orson Charts (also from JFree) is GPL-encumbered.
 * - XChart is lacking in features. JFreeChart and Smile are its superset.
 * - Tablesaw only renders through Plot.ly in JavaScript.
 * - Morpheus has weird API apparently designed to show charts in Swing windows.
 * - DataMelt asks for premium subscription all the time, so it isn't really free.
 * 
 * Both JFreeChart and Smile are licensed under LGPL, which is important for licensing of this library.
 * JFreeChart book is paid for, but we don't need it, because there seem to be tutorials for everything on the Internet.
 * 
 * JFreeChart is generally preferred as it is more featureful, but Smile has some unique features.
 * 
 * We will provide specialized classes to generate commonly used charts.
 * We won't support every possible option and focus on common data visualization needs.
 * Special charts can be still shown via view() methods.
 * 
 * Chart classes represent an empty canvas where many different things can be drawn.
 * Methods of chart classes specify what and how will be drawn.
 * Some methods specify features of the chart like axes or chart size.
 * Every class has view() method to put the configured chart on the screen.
 *
 * In order to support quickly throwing data on the screen, we will accept data in a variety of formats,
 * specifically (primitive) arrays, (primitive) collections, and (primitive) streams.
 * 
 * When we have to choose between caption embedded in the chart and separate figcaption in HTML,
 * we choose embedded caption, because it makes the charts more meaningful in image search.
 * Even better if we can use axis name instead of the embedded caption.
 */
/**
 * Minimal charts for quick data visualization.
 */
@com.machinezoo.stagean.NoTests
@com.machinezoo.stagean.StubDocs
@com.machinezoo.stagean.DraftApi
package com.machinezoo.pmdata.charts;
