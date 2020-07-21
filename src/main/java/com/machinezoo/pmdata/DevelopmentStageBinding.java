// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
public class DevelopmentStageBinding extends SiteBinding {
	private final String name;
	private final String title;
	private final String description;
	private final String leadin;
	public DevelopmentStageBinding(String name, String title, String description, String leadin) {
		this.name = name;
		this.title = title;
		this.description = description;
		this.leadin = leadin;
	}
	@Override
	public String name() {
		return name;
	}
	@Override
	public DomContent expand(SiteBindingContext context) {
		try (var dialog = new SiteDialog(null)) {
			Dialog.notice(new DomFragment()
				.add(Html.b().add(title))
				.add(Html.br())
				.add(description)
				.add(context.source().children().isEmpty() ? null : new DomFragment()
					.add(Html.br())
					.add(leadin + " ")
					.add(context.source().children())));
			return dialog.content();
		}
	}
	public static final DevelopmentStageBinding stub() {
		return new DevelopmentStageBinding("stub", "Stub article", "This page is a placeholder for future article.", "Notes:");
	}
	public static final DevelopmentStageBinding draft() {
		return new DevelopmentStageBinding("draft", "Draft article", "You are reading a working draft that might have some quality issues.", "Known issues:");
	}
}
