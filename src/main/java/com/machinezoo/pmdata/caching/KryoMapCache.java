// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;

public interface KryoMapCache<K, V> extends KryoCache<Map<K, V>>, PersistentMapSource<K, V> {
}
