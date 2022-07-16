// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.function.*;
import com.machinezoo.stagean.*;

@ApiIssue("Auto-detect dependencies. Nest cache refreshes. Then there's no need for touch/link.")
public interface PersistentSource<T> extends Supplier<T> {
	void touch();
	T get();
}
