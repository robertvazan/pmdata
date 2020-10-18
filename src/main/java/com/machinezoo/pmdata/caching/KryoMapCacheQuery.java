// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;

public interface KryoMapCacheQuery<K, V> extends KryoCacheQuery<Map<K, V>> {
	default V get(K key) {
		return get().get(key);
	}
}
