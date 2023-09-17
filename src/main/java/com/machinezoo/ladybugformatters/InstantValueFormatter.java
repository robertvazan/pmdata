// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.time.*;
import com.machinezoo.pushmode.dom.*;

public interface InstantValueFormatter {
	String plain(Instant value);
	String detail(Instant value);
	default DomContent format(Instant value) {
		if (value == null)
			return null;
		return Html.time()
			.datetime(value.toString())
			.title(detail(value))
			.add(plain(value));
	}
}
