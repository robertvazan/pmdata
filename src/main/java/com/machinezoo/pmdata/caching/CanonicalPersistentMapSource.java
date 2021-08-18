// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;

public interface CanonicalPersistentMapSource<K, V> extends PersistentMapSource<K, V>, CanonicalPersistentSource<Map<K, V>> {
	@Override
	default PersistentMapSource<K, V> canonicalize() {
		return this;
	}
}
