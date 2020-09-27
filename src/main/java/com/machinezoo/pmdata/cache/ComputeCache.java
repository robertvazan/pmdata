// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.cache;

import com.google.common.cache.*;

public class ComputeCache {
	/*
	 * Derived class must implements equals() and hashCode() for cache lookup to work.
	 */
	public static abstract class Query<T> {
		public abstract T evaluate();
	}
	/*
	 * Soft-valued cache may cause extremely inefficient GC behavior:
	 * https://bugs.openjdk.java.net/browse/JDK-6912889
	 * 
	 * It is however very simple and it will use all RAM that is allocated to Java process,
	 * which is usually some fraction of physical RAM.
	 * This cache can be tuned indirectly with -Xmx and -XX:SoftRefLRUPolicyMSPerMB.
	 */
	private static final LoadingCache<Query<?>, Object> cache = CacheBuilder.newBuilder()
		.softValues()
		.build(CacheLoader.from(k -> k.evaluate()));
	@SuppressWarnings("unchecked")
	public static <T> T get(Query<T> query) {
		return (T)cache.getUnchecked(query);
	}
}
