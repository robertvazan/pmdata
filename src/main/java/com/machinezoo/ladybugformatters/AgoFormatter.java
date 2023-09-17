// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.time.*;
import java.time.format.*;
import com.machinezoo.hookless.time.*;

public class AgoFormatter implements InstantValueFormatter {
	@Override
	public String plain(Instant value) {
		if (value == null)
			return null;
		ReactiveDuration delta = ReactiveDuration.between(value, ReactiveInstant.now());
		if (delta.isNegative()) {
			delta = delta.negated();
			if (delta.compareTo(Duration.ofDays(2 * 365)) >= 0)
				return String.format("in %d years", delta.toDays() / 365);
			if (delta.compareTo(Duration.ofDays(60)) >= 0)
				return String.format("in %d months", delta.toDays() / 30);
			if (delta.compareTo(Duration.ofDays(30)) >= 0)
				return "in a month";
			if (delta.compareTo(Duration.ofDays(2)) >= 0)
				return String.format("in %d days", delta.toDays());
			if (delta.compareTo(Duration.ofDays(1)) >= 0)
				return "in a day";
			if (delta.compareTo(Duration.ofHours(2)) >= 0)
				return String.format("in %d hours", delta.toHours());
			if (delta.compareTo(Duration.ofHours(1)) >= 0)
				return "in an hour";
			if (delta.compareTo(Duration.ofMinutes(2)) >= 0)
				return String.format("in %d minutes", delta.toMinutes());
			if (delta.compareTo(Duration.ofMinutes(1)) >= 0)
				return "in a minute";
			return "imminent";
		} else {
			if (delta.compareTo(Duration.ofDays(2 * 365)) >= 0)
				return String.format("%d years ago", delta.toDays() / 365);
			if (delta.compareTo(Duration.ofDays(60)) >= 0)
				return String.format("%d months ago", delta.toDays() / 30);
			if (delta.compareTo(Duration.ofDays(30)) >= 0)
				return "a month ago";
			if (delta.compareTo(Duration.ofDays(2)) >= 0)
				return String.format("%d days ago", delta.toDays());
			if (delta.compareTo(Duration.ofDays(1)) >= 0)
				return "a day ago";
			if (delta.compareTo(Duration.ofHours(2)) >= 0)
				return String.format("%d hours ago", delta.toHours());
			if (delta.compareTo(Duration.ofHours(1)) >= 0)
				return "an hour ago";
			if (delta.compareTo(Duration.ofMinutes(2)) >= 0)
				return String.format("%d minutes ago", delta.toMinutes());
			if (delta.compareTo(Duration.ofMinutes(1)) >= 0)
				return "a minute ago";
			return "just now";
		}
	}
	@Override
	public String detail(Instant value) {
		if (value == null)
			return null;
		return DateTimeFormatter.RFC_1123_DATE_TIME.format(value.atZone(ZoneId.systemDefault()));
	}
}
