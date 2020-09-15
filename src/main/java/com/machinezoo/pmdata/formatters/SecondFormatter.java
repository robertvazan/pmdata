// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.formatters;

import java.time.*;

public class SecondFormatter implements DoubleValueFormatter {
	private static Duration convert(double seconds) {
		return Duration.ofNanos((long)(1_000_000_000 * seconds));
	}
	@Override
	public String plain(double value) {
		return new DurationFormatter().plain(convert(value));
	}
	@Override
	public String detail(double value) {
		return new DurationFormatter().detail(convert(value));
	}
}
