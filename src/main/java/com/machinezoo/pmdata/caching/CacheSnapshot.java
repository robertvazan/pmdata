// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;
import java.time.*;
import java.util.concurrent.*;

public class CacheSnapshot<T extends CacheFile> {
	T data;
	public T get() {
		if (data == null) {
			if (exception != null)
				throw new CachedException(exception);
			if (cancelled)
				throw new CancellationException();
		}
		return data;
	}
	/*
	 * This may be present even if get() succeeds, because failing refresh does not erase last good value.
	 */
	String exception;
	public String exception() {
		return exception;
	}
	/*
	 * This may be present in addition to valid data and/or exception. It is used to persist cancellation,
	 * so that cancelled refreshes are not automatically retried every time the program is restarted.
	 */
	boolean cancelled;
	public boolean cancelled() {
		return cancelled;
	}
	/*
	 * Hash of the input as observed by cache's supplier.
	 */
	String input;
	public String input() {
		return input;
	}
	public Path path() {
		return data != null ? data.path() : null;
	}
	/*
	 * Hash is null if this snapshot contains only exception and no data.
	 */
	String hash;
	public String hash() {
		return hash;
	}
	long size;
	public long size() {
		return size;
	}
	Instant updated;
	public Instant updated() {
		return updated;
	}
	Instant refreshed;
	public Instant refreshed() {
		return refreshed;
	}
	/*
	 * How long did it take to refresh the cache last time.
	 */
	Duration cost;
	public Duration cost() {
		return cost;
	}
}
