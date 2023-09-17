// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

/*
 * A gallery of default formatters. Apps can define their own formatter gallery.
 */
public class Pretty {
	public static NumberFormatter number() {
		return new NumberFormatter();
	}
	public static ScientificFormatter scientific() {
		return new ScientificFormatter();
	}
	public static PercentFormatter percents() {
		return new PercentFormatter();
	}
	public static Percent100Formatter percents100() {
		return new Percent100Formatter();
	}
	public static ByteFormatter bytes() {
		return new ByteFormatter();
	}
	public static PixelFormatter pixels() {
		return new PixelFormatter();
	}
	public static DurationFormatter duration() {
		return new DurationFormatter();
	}
	public static SecondFormatter seconds() {
		return new SecondFormatter();
	}
	public static InstantFormatter instant() {
		return new InstantFormatter();
	}
	public static AgoFormatter ago() {
		return new AgoFormatter();
	}
	public static UnitFormatter unit(String unit) {
		return new UnitFormatter(unit);
	}
	public static ObjectFormatter object() {
		return new ObjectFormatter();
	}
}
