// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.io.*;
import org.apache.commons.lang3.exception.*;

public class CachedException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final String formatted;
	public String getFormattedCause() {
		return formatted;
	}
	public CachedException(String message, String cause) {
		super(message);
		this.formatted = cause;
	}
	public CachedException(String message, Throwable cause) {
		super(message);
		StringWriter writer = new StringWriter();
		ExceptionUtils.printRootCauseStackTrace(cause, new PrintWriter(writer));
		for (var nested : ExceptionUtils.getThrowables(cause)) {
			if (nested instanceof CachedException) {
				writer.append("\nPersisted exception:\n");
				writer.append(((CachedException)nested).getFormattedCause());
			}
		}
		formatted = writer.toString();
	}
	public CachedException(String cause) {
		this(null, cause);
	}
	public CachedException(Throwable cause) {
		this(null, cause);
	}
}
