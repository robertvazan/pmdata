// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.util.*;
import com.machinezoo.pushmode.dom.*;

public interface ObjectValueFormatter {
    String plain(Object value);
    String detail(Object value);
    default DomContent format(Object value) {
        if (value == null)
            return null;
        var plain = plain(value);
        var detail = detail(value);
        if (Objects.equals(plain, detail))
            return Html.span().add(plain);
        return Html.abbr()
            .title(detail(value))
            .add(plain(value));
    }
}
