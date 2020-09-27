// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.cache;

import java.io.*;
import org.apache.commons.lang3.exception.*;

public class PersistedException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final String formatted;
	public String getFormattedCause() {
		return formatted;
	}
	public PersistedException(String message, String cause) {
		super(message);
		this.formatted = cause;
	}
	public PersistedException(String message, Throwable cause) {
		super(message);
		StringWriter writer = new StringWriter();
		ExceptionUtils.printRootCauseStackTrace(cause, new PrintWriter(writer));
		formatted = writer.toString();
	}
	public PersistedException(String cause) {
		this(null, cause);
	}
	public PersistedException(Throwable cause) {
		this(null, cause);
	}
}
