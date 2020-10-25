// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;

public abstract class KryoMapCache<K, V> extends KryoCache<Map<K, V>> {
	public V get(K key) {
		return get().get(key);
	}
}
