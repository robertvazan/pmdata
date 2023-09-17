// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.text.*;

public class NumberFormatter implements DoubleValueFormatter {
	private static final char[] BIG_PREFIXES = "KMG".toCharArray();
	@Override
	public String plain(double value) {
		if (value < 0)
			return "-" + plain(-value);
		if (value >= 1) {
			for (int i = -1; i < BIG_PREFIXES.length; ++i) {
				for (int j = 0; j < 3; ++j) {
					if (value < Math.pow(10, 3 * i + j + 4)) {
						var format = NumberFormat.getInstance();
						format.setMinimumFractionDigits(0);
						format.setMaximumFractionDigits(2 - j);
						return format.format(Math.pow(0.001, i + 1) * value) + (i >= 0 ? " " + BIG_PREFIXES[i] : "");
					}
				}
			}
			return new ScientificFormatter().plain(value);
		} else {
			for (int i = 1; i <= 4; ++i) {
				if (value >= Math.pow(0.1, i)) {
					var format = NumberFormat.getInstance();
					format.setMinimumFractionDigits(0);
					format.setMaximumFractionDigits(i + 2);
					return format.format(value);
				}
			}
			if (value > 0)
				return new ScientificFormatter().plain(value);
			return "0";
		}
	}
	@Override
	public String detail(double value) {
		if (value < 0)
			return "-" + detail(-value);
		var format = NumberFormat.getInstance();
		format.setMinimumFractionDigits(0);
		format.setMaximumFractionDigits(Integer.MAX_VALUE);
		if (value >= 1) {
			for (int i = -1; i < BIG_PREFIXES.length; ++i)
				for (int j = 0; j < 3; ++j)
					if (value < Math.pow(10, 3 * i + j + 4))
						return format.format(Math.pow(0.001, i + 1) * value) + (i >= 0 ? " " + BIG_PREFIXES[i] : "");
			return new ScientificFormatter().plain(value);
		} else {
			if (value >= Math.pow(0.1, 4))
				return format.format(value);
			if (value > 0)
				return new ScientificFormatter().plain(value);
			return "0";
		}
	}
}
