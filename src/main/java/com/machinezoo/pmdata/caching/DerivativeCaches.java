// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.machinezoo.hookless.*;

public class DerivativeCaches {
	private static class DerivingQuery<T> implements ComputeCache<ReactiveLazy<CacheDerivative<T>>> {
		final DerivativeCache<T> query;
		DerivingQuery(DerivativeCache<T> query) {
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
	public static <T> T query(DerivativeCache<T> cache) {
		return new DerivingQuery<T>(cache).get().get().unpack();
	}
}
