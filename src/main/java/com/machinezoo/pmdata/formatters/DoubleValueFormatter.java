// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.formatters;

import com.google.common.base.*;
import com.machinezoo.pushmode.dom.*;

public interface DoubleValueFormatter {
	String plain(double value);
	String detail(double value);
	default DomContent format(double value) {
		var plain = plain(value);
		var detail = detail(value);
		if (Objects.equal(plain, detail))
			return Html.span().add(plain);
		return Html.abbr()
			.title(detail(value))
			.add(plain(value));
	}
}
