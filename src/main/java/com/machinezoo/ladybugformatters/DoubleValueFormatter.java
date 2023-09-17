// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.util.*;
import com.machinezoo.pushmode.dom.*;

public interface DoubleValueFormatter {
    String plain(double value);
    String detail(double value);
    default DomContent format(double value) {
        var plain = plain(value);
        var detail = detail(value);
        if (Objects.equals(plain, detail))
            return Html.span().add(plain);
        return Html.abbr()
            .title(detail(value))
            .add(plain(value));
    }
}
