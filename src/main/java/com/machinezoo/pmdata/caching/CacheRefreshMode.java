// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

public enum CacheRefreshMode {
	/*
	 * Cache is only initialized via explicit user action, not automatically upon access.
	 */
	MANUAL,
	/*
	 * Cache is initialized automatically, but refreshes are always manual.
	 */
	INITIAL,
	/*
	 * Automatic initialization and refresh.
	 */
	AUTOMATIC;
}
