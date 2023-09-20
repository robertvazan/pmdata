// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import java.util.function.*;
import com.machinezoo.stagean.*;

@ApiIssue("Auto-detect dependencies. Nest cache refreshes. Then there's no need for touch/link.")
public interface PersistentSource<T> extends Supplier<T> {
	void touch();
	T get();
}
