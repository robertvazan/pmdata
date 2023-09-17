// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

public class PercentFormatter implements DoubleValueFormatter {
	private int precision = 3;
	public PercentFormatter precision(int precision) {
		this.precision = precision;
		return this;
	}
	private Percent100Formatter as100() {
		return new Percent100Formatter()
			.precision(precision);
	}
	@Override
	public String plain(double value) {
		return as100().plain(100 * value);
	}
	@Override
	public String detail(double value) {
		return as100().detail(100 * value);
	}
}
