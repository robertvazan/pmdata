// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import com.google.common.collect.Streams;
import com.machinezoo.hookless.prefs.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmdata.widgets.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;
import one.util.streamex.*;

/*
 * These controls are designed for SiteFragment. They return value while rendering into thread-local context.
 * They are designed to be very concise, because the code will be littered with them.
 */
@DraftApi("should be several separate classes")
public class Dialog {
	/*
	 * Control data is usually held in transient page-local storage, perhaps with persistence in user's preferences.
	 * We however want to allow editing of anything, so we provide this interface for arbitrary data sources.
	 * This should be probably moved up to pushmode or even hookless.
	 */
	public static interface DataBinding<T> {
		T get();
		void set(T value);
		/*
		 * We don't want to deal with fallbacks in every bind() method below.
		 * Besides handling fallbacks centrally here, this method allows chaining to fallback bindings.
		 */
		default DataBinding<T> orElse(T fallback) {
			DataBinding<T> outer = this;
			return new DataBinding<>() {
				@Override
				public T get() {
					T value = outer.get();
					return value != null ? value : fallback;
				}
				@Override
				public void set(T value) {
					outer.set(value);
				}
			};
		}
	}
	/*
	 * Nullable binding for integers should be implemented simply as Binding<Integer>.
	 * This interface is for cases when null should be excluded from possible values.
	 * That means the binding always has a value or at least a fallback.
	 */
	public static interface IntBinding {
		int get();
		void set(int value);
		default DataBinding<Integer> boxed(int fallback) {
			IntBinding outer = this;
			return new DataBinding<Integer>() {
				@Override
				public Integer get() {
					return outer.get();
				}
				@Override
				public void set(Integer value) {
					outer.set(value != null ? value : fallback);
				}
			};
		}
	}
	public static <T> DataBinding<T> bind(Supplier<T> getter, Consumer<T> setter) {
		return new DataBinding<>() {
			@Override
			public T get() {
				return getter.get();
			}
			public void set(T value) {
				setter.accept(value);
			}
		};
	}
	public static IntBinding bindInt(IntSupplier getter, IntConsumer setter) {
		return new IntBinding() {
			@Override
			public int get() {
				return getter.getAsInt();
			}
			public void set(int value) {
				setter.accept(value);
			}
		};
	}
	public static <K, V> DataBinding<V> bind(Map<K, V> map, K key) {
		return bind(() -> map.get(key), v -> {
			if (v != null)
				map.put(key, v);
			else
				map.remove(key);
		});
	}
	public static DataBinding<String> bindString(ReactivePreferences preferences, String key) {
		return bind(() -> preferences.get(key, null), v -> preferences.put(key, v));
	}
	@SuppressWarnings("unchecked")
	public static <T extends Enum<T>> DataBinding<T> bindEnum(ReactivePreferences preferences, String key, T fallback) {
		Class<T> clazz = (Class<T>)fallback.getClass();
		return bind(
			() -> Exceptions.silence().get(() -> Enum.valueOf(clazz, preferences.get(key, fallback.name()))).orElse(fallback),
			v -> preferences.put(key, v.name()));
	}
	public static IntBinding bindInt(ReactivePreferences preferences, String key, int fallback) {
		return bindInt(() -> preferences.getInt(key, fallback), v -> preferences.putInt(key, v));
	}
	/*
	 * Many controls have both a builder implementation and several method implementations.
	 * Builder is the most complex and it is here to avoid overloads for every combination of parameters.
	 */
	public static class Editor {
		private String title;
		public Dialog.Editor title(String title) {
			this.title = title;
			return this;
		}
		private DataBinding<String> binding;
		public Dialog.Editor binding(DataBinding<String> binding) {
			this.binding = binding;
			return this;
		}
		public Editor fallback(String fallback) {
			return binding(bindString(SiteFragment.get().preferences(), title).orElse(fallback));
		}
		public String render() {
			/*
			 * Text editor API guarantees non-null return no matter what parameters are passed in.
			 */
			binding = binding.orElse("");
			new ContentLabel(title)
				.clazz("text-picker")
				.add(Html.input()
					.id(SiteFragment.get().elementId(title))
					.type("text")
					.value(binding.get(), binding::set))
				.render();
			return binding.get();
		}
	}
	/*
	 * Static widget constructors are named as verbType.
	 * Verb alone combined with overloading would not be sufficient to distinguish them,
	 * especially considering type erasure on bindings and collections.
	 */
	public static String edit(String title, String fallback) {
		return new Editor().title(title).fallback(fallback).render();
	}
	public static String edit(String title) {
		return edit(title, "");
	}
	public static class Picker<T> {
		private String title;
		public Picker<T> title(String title) {
			this.title = title;
			return this;
		}
		private Iterable<T> items;
		public Picker<T> items(Iterable<T> items) {
			this.items = items;
			return this;
		}
		/*
		 * List picker requires binding as there is no default storage for arbitrary objects.
		 * That's why specialized pickers need to be defined.
		 */
		private DataBinding<T> binding;
		public Picker<T> binding(DataBinding<T> binding) {
			this.binding = binding;
			return this;
		}
		private Function<T, String> naming = Object::toString;
		public Picker<T> naming(Function<T, String> naming) {
			this.naming = naming;
			return this;
		}
		public T render() {
			List<T> list = StreamEx.of(items.iterator()).toList();
			T bound = binding.get();
			/*
			 * Here we throw in case the list is empty, because we cannot satisfy the postcondition
			 * that returned object is one of the items in the list.
			 */
			T current = bound != null && list.contains(bound) ? bound : list.stream().findFirst().orElseThrow();
			new ContentLabel(title)
				.clazz("list-picker")
				.add(Html.ul()
					.add(list.stream()
						.map(v -> Html.li()
							.clazz(Objects.equals(current, v) ? "list-picker-current" : null)
							.add(Html.button()
								.id(SiteFragment.get().elementId(title, naming.apply(v)))
								.onclick(() -> binding.set(v))
								.add(naming.apply(v))))))
				.render();
			return current;
		}
	}
	/*
	 * Strings have easy persistence, so we provide specialized picker for them.
	 * This picker is used for quick-n-lazy implementation, so no fancy features are added here.
	 */
	public static String pickString(String title, String... items) {
		return new Picker<String>()
			.title(title)
			.items(Arrays.asList(items))
			.binding(bindString(SiteFragment.get().preferences(), title).orElse(items[0]))
			.render();
	}
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
	public static class CasePicker implements AutoCloseable {
		private final String title;
		private DataBinding<String> binding;
		private String selected;
		private final List<String> cases = new ArrayList<>();
		private final EmptySlot empty;
		private boolean taken;
		public CasePicker(String title) {
			this.title = title;
			try (var label = new ContentLabel(title).clazz("list-picker").define()) {
				empty = EmptySlot.block();
			}
		}
		public boolean is(String label) {
			if (cases.contains(label))
				throw new IllegalArgumentException("Duplicate case label.");
			if (binding == null) {
				binding = bindString(SiteFragment.get().preferences(), title).orElse(label);
				selected = binding.get();
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
					.add(cases.stream()
						.map(c -> Html.li()
							.clazz(Objects.equals(selected, c) ? "list-picker-current" : null)
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
	/*
	 * Enums have their own specialized picker, because enums have defined string conversion
	 * and thus serialization and there is a way to enumerate enum constants, which together yields neat API.
	 */
	public static class EnumPicker<T extends Enum<T>> {
		private String title;
		public EnumPicker<T> title(String title) {
			this.title = title;
			return this;
		}
		private Iterable<T> subset;
		public EnumPicker<T> subset(Iterable<T> subset) {
			this.subset = subset;
			return this;
		}
		private DataBinding<T> binding;
		public EnumPicker<T> binding(DataBinding<T> binding) {
			this.binding = binding;
			return this;
		}
		private Class<T> clazz;
		public EnumPicker<T> clazz(Class<T> clazz) {
			this.clazz = clazz;
			return this;
		}
		private T fallback;
		public EnumPicker<T> fallback(T fallback) {
			this.fallback = fallback;
			return this;
		}
		private Function<T, String> naming = Object::toString;
		public EnumPicker<T> naming(Function<T, String> naming) {
			this.naming = naming;
			return this;
		}
		@SuppressWarnings("unchecked")
		public T render() {
			/*
			 * This is a bit messy, but the idea is to make as many parameters optional as possible.
			 */
			if (clazz == null && fallback != null)
				clazz = (Class<T>)fallback.getClass();
			if (subset == null)
				subset = Arrays.asList(clazz.getEnumConstants());
			if (fallback == null)
				fallback = Streams.stream(subset).findFirst().orElseThrow();
			if (binding == null)
				binding = bindEnum(SiteFragment.get().preferences(), title, fallback);
			return new Picker<T>().title(title).items(subset).binding(binding.orElse(fallback)).naming(naming).render();
		}
	}
	/*
	 * Enums are often nicely named, so expose overloads with stringer parameter too.
	 * Enum's toString() should return "programmer-friendly" name, so it's not a good place do define UI-visible name.
	 * 
	 * We always require fallback in parameters, because we would otherwise get ambiguous call errors
	 * as parameter of type T accepts any type and type constraints (extends Enum...) are not used for disambiguation.
	 */
	public static <T extends Enum<T>> T pickEnum(String title, Iterable<T> subset, T fallback, Function<T, String> naming) {
		return new EnumPicker<T>().title(title).subset(subset).fallback(fallback).naming(naming).render();
	}
	public static <T extends Enum<T>> T pickEnum(String title, T fallback, Function<T, String> naming) {
		return new EnumPicker<T>().title(title).fallback(fallback).naming(naming).render();
	}
	public static <T extends Enum<T>> T pickEnum(String title, T fallback) {
		return new EnumPicker<T>().title(title).fallback(fallback).render();
	}
	/*
	 * Enums have the advantage of type information, which we can use to maximum
	 * if we derive title from enum's type name.
	 */
	public static <T extends Enum<T>> T pickEnum(T fallback) {
		return new EnumPicker<T>().title(fallback.getClass().getSimpleName()).fallback(fallback).render();
	}
	/*
	 * Integers have specialized picker, because they have well-defined serialization for persistent preferences
	 * and they are often found in ranges, which provides an easy way to enumerate them.
	 */
	public static class IntPicker {
		private String title;
		public Dialog.IntPicker title(String title) {
			this.title = title;
			return this;
		}
		/*
		 * We want something strongly typed, so Iterable<Integer> is out of question.
		 * We could use primitive collection, but integers will be represented as an array in most cases.
		 * Where primitive collections or streams are used, they are easy to convert to an array.
		 */
		private int[] items;
		public Dialog.IntPicker items(int[] items) {
			this.items = items;
			return this;
		}
		private IntBinding binding;
		public Dialog.IntPicker binding(IntBinding binding) {
			this.binding = binding;
			return this;
		}
		private Integer fallback;
		public Dialog.IntPicker fallback(Integer fallback) {
			this.fallback = fallback;
			return this;
		}
		private IntFunction<String> naming = Integer::toString;
		public Dialog.IntPicker naming(IntFunction<String> naming) {
			this.naming = naming;
			return this;
		}
		public IntPicker range(int start, int end) {
			return items(IntStream.range(start, end).toArray());
		}
		public int render() {
			List<Integer> list = IntStreamEx.of(items).boxed().toList();
			if (fallback == null)
				fallback = list.stream().findFirst().orElseThrow();
			if (binding == null)
				binding = bindInt(SiteFragment.get().preferences(), title, fallback);
			return new Picker<Integer>().title(title).items(list).binding(binding.boxed(fallback)).naming(n -> naming.apply(n)).render();
		}
	}
	/*
	 * Integers are rarely picked raw (sliders serve this purpose better), so only stringer overloads make sense.
	 * While range overloads are going to be used more often, array overloads need to be provided for the general case.
	 */
	public static int pickInt(String title, int[] items, int fallback, IntFunction<String> naming) {
		return new IntPicker().title(title).items(items).fallback(fallback).naming(naming).render();
	}
	public static int pickInt(String title, int start, int end, int fallback, IntFunction<String> naming) {
		return new IntPicker().title(title).range(start, end).fallback(fallback).naming(naming).render();
	}
	public static int pickInt(String title, int[] items, IntFunction<String> naming) {
		return new IntPicker().title(title).items(items).naming(naming).render();
	}
	public static int pickInt(String title, int start, int end, IntFunction<String> naming) {
		return new IntPicker().title(title).range(start, end).naming(naming).render();
	}
	/*
	 * We could also have method pressed(String) here that would return boolean
	 * and button code would then run in an if block, but that would not work correctly,
	 * because any error output from the button would not be persisted and shown.
	 * We aren't handling errors now, but we want to start with correct API.
	 */
	public static void button(String name, Runnable action) {
		/*
		 * Buttons are shown in a row, so we need a container element around them.
		 * Container element also makes it easy to align buttons with the text column.
		 */
		List<DomContent> children = SiteFragment.get().content().children();
		DomElement container = null;
		if (!children.isEmpty()) {
			DomContent last = children.get(children.size() - 1);
			if (last instanceof DomElement) {
				DomElement element = (DomElement)last;
				if ("dialog-buttons".equals(element.clazz()))
					container = element;
			}
		}
		if (container == null) {
			container = Html.div()
				.clazz("dialog-buttons");
			SiteFragment.get().add(container);
		}
		container
			.add(Html.button()
				.id(SiteFragment.get().elementId(name))
				.add(name)
				/*
				 * TODO: Need exception handler here. Exception should be shown until next run.
				 * We might want some progress reporting around it too if long processes end up here.
				 */
				.onclick(action));
	}
}
