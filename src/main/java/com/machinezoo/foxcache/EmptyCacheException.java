// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import com.machinezoo.noexception.*;

public class EmptyCacheException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private static class EmptyCacheExceptionSilencing extends ExceptionHandler {
		@Override
		public boolean handle(Throwable exception) {
			return exception instanceof EmptyCacheException;
		}
	}
	public static ExceptionHandler silence() {
		return new EmptyCacheExceptionSilencing();
	}
}
