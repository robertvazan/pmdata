// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.lang.annotation.*;
import com.machinezoo.stagean.*;

@DraftApi
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataWidget {
	String name() default "";
	String value() default "";
}
