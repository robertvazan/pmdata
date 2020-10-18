// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.util.concurrent.*;
import com.google.common.cache.*;
import com.machinezoo.hookless.*;

public class CachePool {
	private static final ConcurrentMap<LazyCacheQuery<?>, Object> lazy = new ConcurrentHashMap<>();
	@SuppressWarnings("unchecked")
	public static <T> T lazy(LazyCacheQuery<T> query) {
		return (T)lazy.computeIfAbsent(query, LazyCacheQuery::compute);
	}
	/*
	 * Soft-valued cache may cause extremely inefficient GC behavior:
	 * https://bugs.openjdk.java.net/browse/JDK-6912889
	 * 
	 * It is however very simple and it will use all RAM that is allocated to Java process,
	 * which is usually some fraction of physical RAM.
	 * This cache can be tuned indirectly with -Xmx and -XX:SoftRefLRUPolicyMSPerMB.
	 */
	private static final LoadingCache<ComputeCacheQuery<?>, Object> computed = CacheBuilder.newBuilder()
		.softValues()
		.build(CacheLoader.from(k -> k.compute()));
	@SuppressWarnings("unchecked")
	public static <T> T compute(ComputeCacheQuery<T> query) {
		return (T)computed.getUnchecked(query);
	}
	private static class DerivingQuery<T> implements ComputeCacheQuery<ReactiveLazy<CacheDerivative<T>>> {
		final DerivativeCacheQuery<T> query;
		DerivingQuery(DerivativeCacheQuery<T> query) {
			this.query = query;
		}
		@Override
		public ReactiveLazy<CacheDerivative<T>> compute() {
			return new ReactiveLazy<>(() -> CacheDerivative.capture(query::compute));
		}
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof DerivingQuery))
				return false;
			var other = (DerivingQuery<?>)obj;
			return query.equals(other.query);
		}
		@Override
		public int hashCode() {
			return query.hashCode();
		}
	}
	public static <T> T derive(DerivativeCacheQuery<T> query) {
		return compute(new DerivingQuery<T>(query)).get().unpack();
	}
	private static final ConcurrentMap<PersistentCacheQuery<?>, PersistentCache<?>> persistent = new ConcurrentHashMap<>();
	@SuppressWarnings("unchecked")
	static <T extends CacheData> PersistentCache<T> persist(PersistentCacheQuery<T> query) {
		return (PersistentCache<T>)persistent.computeIfAbsent(query, key -> new PersistentCache<>(query.format())
			.id(query)
			.policy(query.policy())
			.link(query::link)
			.supply(query::supply));
	}
}
