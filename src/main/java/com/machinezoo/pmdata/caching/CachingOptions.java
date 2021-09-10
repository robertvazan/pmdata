// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.time.*;

public interface CachingOptions {
	CachingOptions DEFAULT = new CachingOptions() {
	};
	default CacheRefreshMode mode() {
		return CacheRefreshMode.AUTOMATIC;
	}
	/*
	 * Refresh period useful for non-reactive sources like downloads.
	 */
	default Duration period() {
		return null;
	}
	/*
	 * Prevent other caches from refreshing while this one is being evaluated.
	 */
	default boolean exclusive() {
		return false;
	}
	/*
	 * Reactively block when the cache is empty.
	 * This only makes sense in conjunction with non-manual refresh mode.
	 */
	default boolean blocking() {
		return false;
	}
}
