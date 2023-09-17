// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.text.*;
import com.machinezoo.pushmode.dom.*;

public class UnitFormatter implements DoubleValueFormatter {
	private final String unit;
	public String unit() {
		return unit;
	}
	public UnitFormatter(String unit) {
		this.unit = unit;
	}
	private static final char[] BIG_PREFIXES = "KMGTP".toCharArray();
	private static final char[] SMALL_PREFIXES = "mμnp".toCharArray();
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
						return format.format(Math.pow(0.001, i + 1) * value) + " " + (i >= 0 ? BIG_PREFIXES[i] : "") + unit;
					}
				}
			}
			return new ScientificFormatter().plain(value) + " " + unit;
		} else {
			for (int i = 0; i < SMALL_PREFIXES.length; ++i) {
				for (int j = 0; j < 3; ++j) {
					if (value >= Math.pow(0.1, 3 * i + j + 1)) {
						var format = NumberFormat.getInstance();
						format.setMinimumFractionDigits(0);
						format.setMaximumFractionDigits(j);
						return format.format(Math.pow(1_000, i + 1) * value) + " " + SMALL_PREFIXES[i] + unit;
					}
				}
			}
			if (value > 0)
				return new ScientificFormatter().plain(value) + " " + unit;
			return "0 " + unit;
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
			if (value < 1_000)
				return format.format(value) + " " + unit;
			for (int i = 0; i < BIG_PREFIXES.length; ++i)
				if (value < Math.pow(1_000, i + 2))
					return format.format(Math.pow(0.001, i + 1) * value) + " " + BIG_PREFIXES[i] + unit;
			return new ScientificFormatter().detail(value) + " " + unit;
		} else {
			for (int i = 0; i < SMALL_PREFIXES.length; ++i)
				if (value >= Math.pow(0.001, i + 1))
					return format.format(Math.pow(1_000, i + 1) * value) + " " + SMALL_PREFIXES[i] + unit;
			if (value > 0)
				return new ScientificFormatter().detail(value) + " " + unit;
			return "0 " + unit;
		}
	}
	@Override
	public DomContent format(double value) {
		var abs = Math.abs(value);
		if (abs >= Math.pow(0.001, SMALL_PREFIXES.length) && abs < Math.pow(1_000, BIG_PREFIXES.length + 1) || abs == 0)
			return DoubleValueFormatter.super.format(value);
		var element = (DomElement)new ScientificFormatter().format(value);
		return element
			.add(" ")
			.add(unit);
	}
}
