// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.formatters;

import com.machinezoo.hookless.time.*;
import com.machinezoo.pushmode.dom.*;

public interface ReactiveDurationValueFormatter {
	String plain(ReactiveDuration value);
	String detail(ReactiveDuration value);
	default DomContent format(ReactiveDuration value) {
		return Html.span()
			.title(detail(value))
			.add(plain(value));
	}
}
