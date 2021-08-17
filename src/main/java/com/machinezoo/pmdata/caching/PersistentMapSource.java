package com.machinezoo.pmdata.caching;

import java.util.*;

public interface PersistentMapSource<K, V> extends PersistentSource<Map<K, V>> {
	default V get(K key) {
		return get().get(key);
	}
}
