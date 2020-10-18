// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.time.*;
import java.util.*;

public class CachePolicy implements Cloneable {
	private CacheRefreshMode mode = CacheRefreshMode.AUTOMATIC;
	public CacheRefreshMode mode() {
		return mode;
	}
	public CachePolicy mode(CacheRefreshMode mode) {
		Objects.requireNonNull(mode);
		this.mode = mode;
		return this;
	}
	/*
	 * Refresh period useful for non-reactive sources like downloads.
	 */
	private Duration period;
	public Duration period() {
		return period;
	}
	public CachePolicy period(Duration period) {
		if (period != null && (period.isZero() || period.isNegative()))
			throw new IllegalArgumentException();
		this.period = period;
		return this;
	}
	/*
	 * Prevent other caches from refreshing while this one is being evaluated.
	 */
	private boolean exclusive;
	public boolean exclusive() {
		return exclusive;
	}
	public CachePolicy exclusive(boolean exclusive) {
		this.exclusive = exclusive;
		return this;
	}
	/*
	 * Reactively block when the cache is empty.
	 * This only makes sense in conjunction with non-manual refresh mode.
	 */
	private boolean blocking;
	public boolean blocking() {
		return blocking;
	}
	public CachePolicy blocking(boolean blocking) {
		this.blocking = blocking;
		return this;
	}
	@Override
	public CachePolicy clone() {
		var clone = new CachePolicy();
		clone.mode = mode;
		clone.period = period;
		clone.exclusive = exclusive;
		clone.blocking = blocking;
		return clone;
	}
}
