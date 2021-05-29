// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

public class NoticeException extends WidgetException {
	private static final long serialVersionUID = 1L;
	private final Tone tone;
	private final String message;
	public NoticeException(Tone tone, String message) {
		super(message);
		this.tone = tone;
		this.message = message;
	}
	@Override
	public void render() {
		Notice.show(tone, message);
	}
	public static NoticeException info(String message) {
		return new NoticeException(Tone.INFO, message);
	}
	public static NoticeException warn(String message) {
		return new NoticeException(Tone.WARNING, message);
	}
	public static NoticeException fail(String message) {
		return new NoticeException(Tone.FAILURE, message);
	}
}
