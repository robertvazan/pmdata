// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

public enum Tone {
	YES("yes"),
	NO("no"),
	MAYBE("maybe"),
	GOOD("yes"),
	BAD("no"),
	POSITIVE("yes"),
	NEGATIVE("no"),
	NEUTRAL("maybe"),
	COMPLETE("yes"),
	MISSING("no"),
	PARTIAL("maybe"),
	SUCCESS("yes"),
	FAILURE("no"),
	OK("yes"),
	INFO("yes"),
	NOTICE("yes"),
	ERROR("no"),
	WARNING("maybe");
	private final String css;
	public String css() {
		return css;
	}
	Tone(String css) {
		this.css = css;
	}
}
