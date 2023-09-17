// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import static java.util.stream.Collectors.*;
import java.text.*;
import com.machinezoo.pushmode.dom.*;

public class ScientificFormatter implements DoubleValueFormatter {
	private static char superscript(char symbol) {
		if (symbol == '-')
			return '⁻';
		if (symbol == '2')
			return '²';
		if (symbol == '3')
			return '³';
		if (symbol >= '0' && symbol <= '9')
			return (char)(symbol - '0' + '⁰');
		return symbol;
	}
	private static String reformat(String ascii) {
		var separator = ascii.indexOf('E');
		if (separator < 0)
			return ascii;
		return ascii.substring(0, separator) + "×10" + ascii.substring(separator + 1).chars().mapToObj(c -> Character.toString(superscript((char)c))).collect(joining());
	}
	@Override
	public String plain(double value) {
		return reformat(new DecimalFormat("0.##E0").format(value));
	}
	@Override
	public String detail(double value) {
		return reformat(new DecimalFormat("0.####################E0").format(value));
	}
	@Override
	public DomContent format(double value) {
		var ascii = new DecimalFormat("0.##E0").format(value);
		var separator = ascii.indexOf('E');
		if (separator < 0)
			return DoubleValueFormatter.super.format(value);
		return Html.span()
			.title(detail(value))
			.add(ascii.substring(0, separator))
			.add("×10")
			.add(Html.sup()
				.add(ascii.substring(separator + 1)));
	}
}
