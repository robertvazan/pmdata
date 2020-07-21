// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.text.*;
import java.time.*;
import java.util.*;
import com.machinezoo.hookless.time.*;
import com.machinezoo.stagean.*;

/*
 * Formatters for UIs. They perform automatic unit conversions and rounding.
 * Rounding step is 0.1% to 1%, which is sufficient precisions for humans.
 */
@DraftApi("should be several separate classes")
public class Pretty {
	public static String scientific(double number) {
		return String.format("%.2e", number);
	}
	public static String number(double number) {
		if (number < 0)
			return "-" + number(-number);
		if (number >= 1) {
			if (number < 10)
				return new DecimalFormat("0.##").format(number);
			if (number < 100)
				return new DecimalFormat("0.#").format(number);
			if (number < 1_000)
				return String.format("%.0f", number);
			if (number < 10_000)
				return new DecimalFormat("0.##").format(0.001 * number) + " K";
			if (number < 100_000)
				return new DecimalFormat("0.#").format(0.001 * number) + " K";
			if (number < 1_000_000)
				return String.format("%.0f K", 0.001 * number);
			if (number < 10_000_000)
				return new DecimalFormat("0.##").format(0.000_001 * number) + " M";
			if (number < 100_000_000)
				return new DecimalFormat("0.#").format(0.000_001 * number) + " M";
			if (number < 1_000_000_000)
				return String.format("%.0f M", 0.000_001 * number);
			if (number < 10_000_000_000L)
				return new DecimalFormat("0.##").format(0.000_000_001 * number) + " G";
			if (number < 100_000_000_000L)
				return new DecimalFormat("0.#").format(0.000_000_001 * number) + " G";
			if (number < 1_000_000_000_000L)
				return String.format("%.0f G", 0.000_000_001 * number);
			return scientific(number);
		} else {
			if (number >= 0.1)
				return String.format("%.0f m", 1_000 * number);
			if (number >= 0.01)
				return new DecimalFormat("0.#").format(1_000 * number) + " m";
			if (number >= 0.001)
				return new DecimalFormat("0.##").format(1_000 * number) + " m";
			if (number >= 0.000_1)
				return String.format("%.0f u", 1_000_000 * number);
			if (number >= 0.000_01)
				return new DecimalFormat("0.#").format(1_000_000 * number) + " u";
			if (number >= 0.000_001)
				return new DecimalFormat("0.##").format(1_000_000 * number) + " u";
			if (number >= 0.000_000_1)
				return String.format("%.0f n", 1_000_000_000 * number);
			if (number >= 0.000_000_01)
				return new DecimalFormat("0.#").format(1_000_000_000 * number) + " n";
			if (number >= 0.000_000_001)
				return new DecimalFormat("0.##").format(1_000_000_000 * number) + " n";
			if (number > 0)
				return scientific(number);
			return "0";
		}
	}
	public static String number(long number) {
		if (number < 0)
			return "-" + number(-number);
		if (number < 1000)
			return String.format("%d", number);
		return number((double)number);
	}
	public static String percents(double fraction) {
		if (fraction < 0)
			return "-" + percents(-fraction);
		double percents = 100 * fraction;
		if (percents >= 100)
			return String.format("%,.0f %%", percents);
		if (percents >= 10)
			return new DecimalFormat("0.#").format(percents) + " %";
		if (percents >= 1)
			return new DecimalFormat("0.##").format(percents) + " %";
		if (percents >= 0.1)
			return new DecimalFormat("0.###").format(percents) + " %";
		if (percents >= 0.01)
			return new DecimalFormat("0.####").format(percents) + " %";
		if (percents >= 0.001)
			return new DecimalFormat("0.#####").format(percents) + " %";
		if (percents >= 0.000_1)
			return new DecimalFormat("0.######").format(percents) + " %";
		if (percents > 0)
			return scientific(fraction);
		return "0 %";
	}
	public static String unit(long number, String unit) {
		String formatted = number(number);
		return formatted.contains(" ") ? formatted + unit : formatted + " " + unit;
	}
	public static String unit(double number, String unit) {
		String formatted = number(number);
		return formatted.contains(" ") ? formatted + unit : formatted + " " + unit;
	}
	public static String bytes(long bytes) {
		return unit(bytes, "B");
	}
	public static String bytes(double bytes) {
		return unit(bytes, "B");
	}
	public static String pixels(long pixels) {
		return unit(pixels, "px");
	}
	public static String pixels(double pixels) {
		return unit(pixels, "px");
	}
	public static String seconds(double seconds) {
		return unit(seconds, "s");
	}
	public static String duration(Duration duration) {
		if (duration.isNegative())
			return "-" + duration(duration.negated());
		if (duration.toDays() > 0)
			return duration.toDays() + "d" + (duration.toHours() % 24) + "h";
		if (duration.toHours() > 0)
			return duration.toHours() + "h" + (duration.toMinutes() % 60) + "m";
		if (duration.toMinutes() > 0)
			return duration.toMinutes() + "m" + (duration.getSeconds() % 60) + "s";
		return unit(duration.toNanos() / 1_000_000_000.0, "s");
	}
	public static String duration(ReactiveDuration duration) {
		if (duration.isNegative())
			return "-" + duration(duration.negated());
		if (duration.toDays() > 0)
			return duration.toDays() + "d" + (duration.toHours() % 24) + "h";
		if (duration.toHours() > 0)
			return duration.toHours() + "h" + (duration.toMinutes() % 60) + "m";
		if (duration.toMinutes() > 0)
			return duration.toMinutes() + "m" + (duration.getSeconds() % 60) + "s";
		return duration.getSeconds() + "s";
	}
	public static String ago(Instant time) {
		if (time == null)
			return "";
		ReactiveInstant now = ReactiveInstant.now();
		GrowingReactiveDuration delta = ReactiveDuration.between(time, now);
		if (delta.isNegative())
			return "in the future";
		if (delta.compareTo(Duration.ofMinutes(1)) < 0)
			return "moments ago";
		if (delta.compareTo(Duration.ofMinutes(2)) < 0)
			return "a minute ago";
		if (delta.compareTo(Duration.ofHours(1)) < 0)
			return String.format("%d mins ago", delta.toMinutes());
		if (delta.compareTo(Duration.ofHours(2)) < 0)
			return "an hour ago";
		if (delta.compareTo(Duration.ofDays(1)) < 0)
			return String.format("%d hours ago", delta.toHours());
		if (delta.compareTo(Duration.ofDays(2)) < 0)
			return "a day ago";
		if (delta.compareTo(Duration.ofDays(60)) < 0)
			return String.format("%d days ago", delta.toDays());
		return String.format("%d months ago", delta.toDays() / 30);
	}
	public static String any(Object data) {
		if (data == null)
			return "";
		/*
		 * Even though Long/Integer/Short end up calling the method with long parameter,
		 * they must be first casted to long/int/short, because direct cast to long would fail for Integer and Short.
		 * The same applies to Double/Float.
		 */
		if (data instanceof Long)
			return number((long)data);
		if (data instanceof Integer)
			return number((int)data);
		if (data instanceof Short)
			return number((short)data);
		if (data instanceof Double)
			return number((double)data);
		if (data instanceof Float)
			return number((float)data);
		if (data instanceof Instant)
			return ago((Instant)data);
		if (data instanceof Duration)
			return duration((Duration)data);
		if (data instanceof ReactiveDuration)
			return duration((ReactiveDuration)data);
		if (data instanceof Optional)
			return any(((Optional<?>)data).orElse(null));
		if (data instanceof OptionalInt) {
			var optional = (OptionalInt)data;
			return optional.isPresent() ? number(optional.getAsInt()) : "";
		}
		if (data instanceof OptionalLong) {
			var optional = (OptionalLong)data;
			return optional.isPresent() ? number(optional.getAsLong()) : "";
		}
		if (data instanceof OptionalDouble) {
			var optional = (OptionalDouble)data;
			return optional.isPresent() ? number(optional.getAsDouble()) : "";
		}
		return data.toString();
	}
}
