package com.machinezoo.pmdata.caching;

import java.util.function.*;

public interface PersistentSource<T> extends Supplier<T> {
	void touch();
	T get();
}
