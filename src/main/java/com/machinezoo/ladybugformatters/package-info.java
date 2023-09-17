// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
/*
 * We don't want to put any unit conversions here. There are dedicated libraries that do it better.
 * We will just take values in some unit and add/remove scaling factors (kilo-, milli-, ...).
 * 
 * Every formatter has its own class, so that formatting can be customized.
 * There is however one public class that exposes default formatter configurations.
 * 
 * Formatters are mutable, because they have builder API. That makes them hard to reuse across threads.
 * We will not attempt to cache configured formatters in any way and instead opt for convenience.
 * Performance-sensitive application code can just use custom code to do the formatting.
 * 
 * DOM tree output is preferred and thus gets the default method name format().
 * Plain text is produced by plain() and hover tooltip by detail().
 * All generated text is Unicode. Special characters will be used if necessary.
 * Only English output is fully supported, although locale-specific formatting methods are used where possible.
 * 
 * Default rounding step is 0.1% to 1%, which is sufficient precisions for humans.
 */
/**
 * Collection of formatters generating both plain text and HTML.
 */
@com.machinezoo.stagean.NoTests
@com.machinezoo.stagean.StubDocs
@com.machinezoo.stagean.DraftApi
package com.machinezoo.ladybugformatters;
