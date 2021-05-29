// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

public abstract class WidgetException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	public abstract void render();
	protected WidgetException(String message, Throwable cause) {
		super(message, cause);
	}
	protected WidgetException(String message) {
		super(message);
	}
	protected WidgetException(Throwable cause) {
		super(cause);
	}
	protected WidgetException() {
	}
}
