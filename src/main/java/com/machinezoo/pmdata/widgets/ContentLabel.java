// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * Most of the controls will be simple pickers that will together form sort of a dialog box above controlled content.
 * These controls need label for visual navigation as well as an element ID, as a key into preferences and other for services.
 */
@DraftApi
public class ContentLabel {
	private String label;
	public ContentLabel label(String label) {
		this.label = label;
		return this;
	}
	public ContentLabel() {
	}
	public ContentLabel(String label) {
		this.label = label;
	}
	/*
	 * If we are labeling just one input element, it is desirable to associate the label text with the control with <label> element.
	 * The <label> element however shouldn't be used always as sometimes we have multiple input elements under one label (e.g. radio buttons).
	 * When <label> is used, its 'for' attribute must be set, so we can use the presence of target ID as an indication that <label> should be used.
	 */
	private String target;
	public ContentLabel target(String target) {
		this.target = target;
		return this;
	}
	/*
	 * Some labeled widgets might want to customize the overall look, which is much easier to do with widget-specific CSS class.
	 */
	private String clazz;
	public ContentLabel clazz(String clazz) {
		this.clazz = clazz;
		return this;
	}
	private DomContent content;
	public ContentLabel add(DomContent content) {
		this.content = new DomFragment()
			.add(this.content)
			.add(content);
		return this;
	}
	public void render() {
		var fragment = SiteFragment.get();
		/*
		 * Allow null label to signal optional display of content without any label.
		 */
		if (label == null)
			fragment.add(content);
		/*
		 * We will use one top-level element to wrap label+content, so that the result is somewhat easier to style.
		 * We have to choose whether to put <label> at the top level or add it as a child.
		 * Since we don't have control over content and we don't want to just flag all non-input content as label,
		 * we are safer with <label> added as a child of generic div parent.
		 */
		fragment.add(Html.div()
			.key(fragment.elementId(label, "label-container"))
			.clazz("label-container", clazz)
			.add((target != null ? Html.label().forid(target) : Html.div())
				.clazz("label-text")
				.add(label))
			.add(content));
	}
	public CloseableScope define() {
		/*
		 * Do not nest new fragment. Labeled content should appear under the same fragment key as surrounding content.
		 * We will just create new empty fragment for the same key, so that we can capture content in it.
		 */
		var fragment = SiteFragment.forKey(SiteFragment.get().key());
		return fragment.open().andThen(() -> {
			add(fragment.content());
			render();
		});
	}
}