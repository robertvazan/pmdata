// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.text.*;
import java.time.*;
import com.machinezoo.hookless.time.*;

public class DurationFormatter implements DurationValueFormatter, ReactiveDurationValueFormatter {
	@Override
	public String plain(Duration value) {
		if (value == null)
			return null;
		if (value.isNegative())
			return "-" + plain(value.negated());
		if (value.toDays() > 0)
			return String.format("%dd %dh", value.toDays(), value.toHoursPart());
		if (value.toHours() > 0)
			return String.format("%dh %dm", value.toHours(), value.toMinutesPart());
		if (value.toMinutes() > 0)
			return String.format("%dm %ds", value.toMinutes(), value.toSecondsPart());
		if (value.toSeconds() >= 10)
			return String.format("%.1f s", value.toSeconds() + 0.000_000_001 * value.toNanosPart());
		if (value.toSeconds() >= 1)
			return String.format("%.2f s", value.toSeconds() + 0.000_000_001 * value.toNanosPart());
		if (value.toMillis() >= 100)
			return String.format("%.0f ms", 0.000_001 * value.toNanos());
		if (value.toMillis() >= 10)
			return String.format("%.1f ms", 0.000_001 * value.toNanos());
		if (value.toMillis() >= 1)
			return String.format("%.2f ms", 0.000_001 * value.toNanos());
		if (value.toNanos() >= 100_000)
			return String.format("%.0f μs", 0.001 * value.toNanos());
		if (value.toNanos() >= 10_000)
			return String.format("%.1f μs", 0.001 * value.toNanos());
		if (value.toNanos() >= 1_000)
			return String.format("%.2f μs", 0.001 * value.toNanos());
		if (!value.isZero())
			return String.format("%d ns", value.toNanos());
		return "0";
	}
	@Override
	public String detail(Duration value) {
		if (value == null)
			return null;
		if (value.isNegative())
			return "-" + detail(value.negated());
		var seconds = new DecimalFormat("0.#########").format(value.toSecondsPart() + 0.000_000_001 * value.toNanosPart());
		if (value.toDays() > 0)
			return String.format("%dd %dh %dm %ss", value.toDays(), value.toHoursPart(), value.toMinutesPart(), seconds);
		if (value.toHours() > 0)
			return String.format("%dh %dm %ss", value.toHours(), value.toMinutesPart(), seconds);
		if (value.toMinutes() > 0)
			return String.format("%dm %ss", value.toMinutes(), seconds);
		if (value.toSeconds() > 0)
			return String.format("%s s", seconds);
		if (value.toMillis() > 0)
			return String.format("%s ms", new DecimalFormat("0.######").format(0.000_001 * value.toNanosPart()));
		if (value.toNanos() >= 1_000)
			return String.format("%s μs", new DecimalFormat("0.###").format(0.001 * value.toNanosPart()));
		return String.format("%d ns", value.toNanosPart());
	}
	@Override
	public String plain(ReactiveDuration value) {
		if (value == null)
			return null;
		if (value.isNegative())
			return "-" + plain(value.negated());
		if (value.compareTo(Duration.ofDays(1)) >= 0)
			return String.format("%dd %dh", value.toDays(), value.toHours() % 24);
		if (value.compareTo(Duration.ofHours(1)) >= 0)
			return String.format("%dh %dm", value.toHours(), value.toMinutes() % 60);
		if (value.compareTo(Duration.ofMinutes(1)) >= 0)
			return String.format("%dm %ds", value.toMinutes(), value.getSeconds() % 60);
		if (value.compareTo(Duration.ofSeconds(10)) >= 0)
			return String.format("%.1f s", 0.001 * value.truncatedTo(Duration.ofMillis(100)).toMillis());
		if (value.compareTo(Duration.ofSeconds(1)) >= 0)
			return String.format("%.2f s", 0.001 * value.truncatedTo(Duration.ofMillis(10)).toMillis());
		if (value.compareTo(Duration.ofMillis(100)) >= 0)
			return String.format("%d ms", value.truncatedTo(Duration.ofMillis(1)).toMillis());
		return "0";
	}
	@Override
	public String detail(ReactiveDuration value) {
		return null;
	}
}
