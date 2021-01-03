// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.*;
import java.util.function.*;
import com.google.common.cache.*;

public abstract class ComputeCache<T> implements Supplier<T> {
	public abstract T compute();
	/*
	 * Soft-valued cache may cause extremely inefficient GC behavior:
	 * https://bugs.openjdk.java.net/browse/JDK-6912889
	 * 
	 * It is however very simple and it will use all RAM that is allocated to Java process,
	 * which is usually some fraction of physical RAM.
	 * This cache can be tuned indirectly with -Xmx and -XX:SoftRefLRUPolicyMSPerMB.
	 * 
	 * Cached value is wrapped in Optional, because Guava cache does not tolerate null values.
	 */
	private static final LoadingCache<ComputeCache<?>, Optional<?>> all = CacheBuilder.newBuilder()
		.softValues()
		.build(CacheLoader.from(k -> Optional.ofNullable(k.compute())));
	@Override
	@SuppressWarnings("unchecked")
	public T get() {
		return (T)all.getUnchecked(this).orElse(null);
	}
}
