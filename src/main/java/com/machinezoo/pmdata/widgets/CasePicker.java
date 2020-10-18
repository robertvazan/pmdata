// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.util.*;
import com.machinezoo.pmdata.bindings.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

/*
 * Another quick-n-dirty string-based picker is the switch statement emulation.
 * We cannot use switch statement directly, because we don't want to create an extra enum
 * and we don't want to list all labels in the enum and then repeat them in case blocks.
 * 
 * We could either configure case picker upfront, providing every case as a Runnable,
 * or we could build it incrementally, which affords neater if-then-else API.
 * The downside of the incremental solution, aside from being more complicated to implement,
 * is that we don't have the full list of cases until the end and by that time
 * it is impossible to trigger fallback, because we already skipped it in the incremental process.
 * We will nevertheless opt for the incremental API as it avoids overuse of lambdas
 * and it is more flexible, allowing mutation of local variables in case handlers
 * or returning values from function, for example. The flexibility is important,
 * because case picker will be often used as the outermost scope of a complex method.
 * 
 * We will deal with the fallback issue in two ways. Firstly, we will always use the first case as fallback.
 * Second, if we miss that fallback, perhaps because the binding contains garbage
 * left over from previous version of the software, there will be no fallback.
 * This will likely cause large parts of the dialog to be missing.
 * That's why this class is only suitable for prototypes and other undemanding code
 * where the user can be expected to understand the situation and click a case to fix it.
 * Production code where usability matters should use enum picker instead.
 * 
 * We can then choose between try-with-resources and explicit render() method.
 * Explicit render() method is easy to forget, especially when the case picker
 * is an outer scope in a large method, so we will opt for try-with-resources.
 * The downside of try-with-resources is that we cannot throw exceptions in close(),
 * because such exceptions shadow exceptions thrown from the try block,
 * which is particularly problematic when the try block exception causes the exception in close(),
 * for example when close() detects zero cases only because the try block terminated early due to an exception.
 * For this reason, we will neither throw nor log anything and just make the situation visible to the user.
 * 
 * Then there's the question of how to specify the default case.
 * Case picker is intended for quick jobs where manual selection of default is an overkill.
 * We could have fallback() in addition to is(label) or to pass a parameter to the constructor,
 * but all this will fail anyway when binding contains garbage.
 * Explicit fallback() would also make it impossible to default to the first case, which is the typical scenario.
 * We will therefore avoid fallback API and just always default to the first case.
 * If binding contains garbage, we will just make the situation visible and let the user fix it.
 * 
 * We could also render case picker in two phases, determine default in the first phase and use it in the second.
 * The second phase could be triggered by marking the computation as blocking and writing a variable to invalidate it.
 * But that would entail a lot of problems. It would, among other things, cause issues with exceptions.
 * If the exception is triggered when none of the cases collected so far is selected,
 * then resetting the binding to the first case would make the exception invisible and the failing case impossible to select.
 */
@DraftApi
public class CasePicker implements AutoCloseable {
	private final String title;
	private StringBinding binding;
	private String selected;
	private final List<String> cases = new ArrayList<>();
	private final EmptySlot empty;
	private boolean taken;
	public CasePicker(String title) {
		this.title = title;
		try (var label = new ContentLabel(title).define()) {
			empty = EmptySlot.block();
		}
	}
	public boolean is(String label) {
		if (cases.contains(label))
			throw new IllegalArgumentException("Duplicate case label.");
		if (binding == null) {
			binding = StringBinding.of(title);
			selected = binding.get().orElse(label);
		}
		cases.add(label);
		taken |= label.equals(selected);
		return label.equals(selected);
	}
	@Override
	public void close() {
		/*
		 * Silently tolerate configuration with zero cases.
		 * It is most likely caused by an exception thrown in the try block before first case was tested.
		 */
		if (!cases.isEmpty()) {
			/*
			 * This code was copied from list picker. The only substantial modification is that
			 * we will not mark any case as selected if the binding contains garbage.
			 */
			empty.content().add(Html.ul()
				.clazz("item-picker")
				.add(cases.stream()
					.map(c -> Html.li()
						.clazz(Objects.equals(selected, c) ? "item-picker-current" : null)
						.add(Html.button()
							.id(SiteFragment.get().elementId(title, c))
							.onclick(() -> binding.set(c))
							.add(c)))));
			if (!taken) {
				SiteFragment.temporary()
					.run(() -> Notice.warn("Nothing selected. Pick one option manually."))
					.render(empty.content());
			}
		}
	}
}