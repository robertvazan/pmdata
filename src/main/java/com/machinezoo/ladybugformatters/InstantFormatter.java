// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import com.machinezoo.hookless.time.*;

public class InstantFormatter implements InstantValueFormatter {
	@Override
	public String plain(Instant value) {
		if (value == null)
			return null;
		var delta = ReactiveDuration.between(value, ReactiveInstant.now());
		var distance = delta.isNegative() ? delta.negated() : delta;
		var time = value.atZone(ZoneId.systemDefault());
		if (distance.compareTo(Duration.ofDays(365)) >= 0)
			return time.format(DateTimeFormatter.ofPattern("MMM, yyyy"));
		if (distance.compareTo(Duration.ofDays(60)) >= 0)
			return time.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
		if (distance.compareTo(Duration.ofDays(3)) >= 0)
			return time.format(DateTimeFormatter.ofPattern("MMM d"));
		var today = ReactiveInstant.now().truncatedTo(ChronoUnit.DAYS);
		var day = value.truncatedTo(ChronoUnit.DAYS);
		if (today.minus(1, ChronoUnit.DAYS).equals(day))
			return time.format(DateTimeFormatter.ofPattern("yesterday, H:mm"));
		if (today.plus(1, ChronoUnit.DAYS).equals(day))
			return time.format(DateTimeFormatter.ofPattern("tomorrow, H:mm"));
		if (!today.equals(day))
			return time.format(DateTimeFormatter.ofPattern("EEE, H:mm"));
		if (distance.toMinutes() >= 3)
			return time.format(DateTimeFormatter.ofPattern("H:mm"));
		return time.format(DateTimeFormatter.ofPattern("H:mm:ss"));
	}
	@Override
	public String detail(Instant value) {
		if (value == null)
			return null;
		return value.toString();
	}
}
