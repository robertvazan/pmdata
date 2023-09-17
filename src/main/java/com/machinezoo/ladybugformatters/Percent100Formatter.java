// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.text.*;

public class Percent100Formatter implements DoubleValueFormatter {
	private int precision = 3;
	public Percent100Formatter precision(int precision) {
		this.precision = precision;
		return this;
	}
	@Override
	public String plain(double value) {
		if (value < 0)
			return "-" + plain(-value);
		if (value >= 1_000_000)
			return new ScientificFormatter().plain(0.01 * value);
		for (int i = 2; i >= -4; --i) {
			if (value >= Math.pow(10, i)) {
				var format = NumberFormat.getInstance();
				format.setMinimumFractionDigits(0);
				format.setMaximumFractionDigits(Math.max(0, precision - 1 - i));
				return format.format(value) + " %";
			}
		}
		if (value > 0)
			return new ScientificFormatter().plain(0.01 * value);
		return "0 %";
	}
	@Override
	public String detail(double value) {
		if (value < 0)
			return "-" + detail(-value);
		if (value >= 1_000_000)
			return new ScientificFormatter().plain(0.01 * value);
		for (int i = 2; i >= -4; --i) {
			if (value >= Math.pow(10, i)) {
				var format = NumberFormat.getInstance();
				format.setMinimumFractionDigits(0);
				format.setMaximumFractionDigits(Integer.MAX_VALUE);
				return format.format(value) + " %";
			}
		}
		if (value > 0)
			return new ScientificFormatter().plain(0.01 * value);
		return "0 %";
	}
}
