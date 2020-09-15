// Part of PMData: https://pmdata.machinezoo.com
/*
 * This is minimal chart support. No charts are defined directly here.
 * Third party libraries should be used for that. This package just generates PNG/SVG and renders into SiteFragment.
 * 
 * There are several libraries producing charts. Currently we only support JFreeChart and Smile.
 * Here are reasons for exclusion of others:
 * - Orson Charts (also from JFree) is GPL-encumbered.
 * - XChart is lacking in features. JFreeChart and Smile are its superset.
 * - Tablesaw only renders through Plot.ly in JavaScript.
 * - Morpheus has weird API apparently designed to show charts in Swing windows.
 * - DataMelt asks for premium subscription all the time, so it isn't really free.
 * 
 * Both JFreeChart and Smile are licensed under LGPL, which is important for licensing of this library.
 * JFreeChart book is paid for, but we don't need it, because there seem to be tutorials for everything on the Internet.
 */
/**
 * Minimal chart support for quick data visualization.
 */
@com.machinezoo.stagean.NoTests
@com.machinezoo.stagean.StubDocs
@com.machinezoo.stagean.DraftApi
package com.machinezoo.pmdata.charts;
