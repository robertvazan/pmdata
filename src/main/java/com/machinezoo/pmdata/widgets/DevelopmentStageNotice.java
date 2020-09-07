// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
public abstract class DevelopmentStageNotice {
	protected abstract String name();
	protected abstract String title();
	protected abstract String description();
	protected abstract String leadin();
	public static class Stub extends DevelopmentStageNotice {
		@Override
		protected String name() {
			return "stub";
		}
		@Override
		protected String title() {
			return "Stub article";
		}
		@Override
		protected String description() {
			return "This page is a placeholder for future article.";
		}
		@Override
		protected String leadin() {
			return "Notes:";
		}
	}
	public static class Draft extends DevelopmentStageNotice {
		@Override
		protected String name() {
			return "draft";
		}
		@Override
		protected String title() {
			return "Draft article";
		}
		@Override
		protected String description() {
			return "You are reading a working draft that might have some quality issues.";
		}
		@Override
		protected String leadin() {
			return "Known issues:";
		}
	}
	public void render(DomContent content) {
		Notice.info(new DomFragment()
			.add(Html.b().add(title()))
			.add(Html.br())
			.add(description())
			.add(content == null ? null : new DomFragment()
				.add(Html.br())
				.add(leadin() + " ")
				.add(content)));
	}
	public void render() {
		render(null);
	}
	private void expand() {
		var content = SiteTemplate.element().children();
		if (content.isEmpty())
			render();
		else
			render(new DomFragment().add(content));
	}
	public void register(SiteTemplate template) {
		template.register(name(), this::expand);
	}
	public static void registerAll(SiteTemplate template) {
		new Stub().register(template);
		new Draft().register(template);
	}
}
