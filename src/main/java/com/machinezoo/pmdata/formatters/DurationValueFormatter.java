// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.formatters;

import java.time.*;
import com.machinezoo.pushmode.dom.*;

public interface DurationValueFormatter {
	String plain(Duration value);
	String detail(Duration value);
	default DomContent format(Duration value) {
		if (value == null)
			return null;
		return Html.time()
			.datetime(value.toString())
			.title(detail(value))
			.add(plain(value));
	}
}
