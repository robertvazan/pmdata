// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import static java.util.stream.Collectors.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import java.util.regex.*;
import java.util.stream.*;
import javax.imageio.*;
import javax.imageio.plugins.jpeg.*;
import javax.imageio.stream.*;
import com.google.common.collect.Streams;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pmsite.preferences.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;
import it.unimi.dsi.fastutil.objects.*;
import one.util.streamex.*;

/*
 * These controls are designed for SiteDialog. They return value while rendering into thread-local context.
 * They are designed to be very concise, because the code will be littered with them.
 */
@DraftApi("should be several separate classes")
public class Dialog {
	/*
	 * It is possible to mark a point in the dialog where content will be inserted later.
	 * This makes visible order of dialog items independent of construction order.
	 */
	public static class Empty {
		/*
		 * We need to mark the position with a cookie element, because offset could be changed by earlier Empty instances.
		 * We cannot use empty string as a cookie, because PushMode discards empty text elements.
		 * We cannot use empty DomFragment, because that one would be inlined and discarded upon insertion.
		 * 
		 * HTML doesn't have any entirely transparent element that could be inserted anywhere.
		 * The two closest options are OUTPUT and TEMPLATE elements, perhaps even DIV.
		 * The downside of OUTPUT is that it could have heavy CSS styling attached to it.
		 * This applies to a lesser extent to DIV too. TEMPLATE element is not rendered, so it doesn't have this problem.
		 * 
		 * It is however not a good form to leave junk in the output HTML.
		 * Calling code should ensure the placeholder is either replaced or discarded.
		 */
		private final DomElement cookie = Html.template();
		/*
		 * Remember current dialog's container reference as replacement could happen while nested dialog is active.
		 */
		private final DomContainer container;
		/*
		 * While this class is designed to work with SiteDialog, we allow specifying arbitrary DomContainer.
		 * That is particularly important for labeled content like pickers.
		 */
		public Empty(DomContainer container) {
			this.container = container;
			container.add(cookie);
		}
		public Empty() {
			this(SiteDialog.out());
		}
		/*
		 * This must be called when dialog is still active.
		 * Replacement on already closed dialog will have no effect.
		 */
		public void replace(DomContent content) {
			/*
			 * PushMode child list is immutable. We will have to completely replace it.
			 * We therefore have to make a copy of it before calling clear().
			 */
			var children = new ArrayList<>(container.children());
			for (int i = 0; i < children.size(); ++i) {
				/*
				 * Do not use List.indexOf() as that one would use equality instead of reference comparison,
				 * which would not only be slow, it would also treat all TEMPLATE elements as equal,
				 * so multiple Empty placeholders would be mixed up.
				 */
				if (children.get(i) == cookie) {
					container.children().clear();
					container.add(children.subList(0, i));
					container.add(content);
					container.add(children.subList(i + 1, children.size()));
					break;
				}
			}
		}
		public void discard() {
			replace(null);
		}
	}
	/*
	 * Wikipedia-like notices. We support the same levels as in logging: info, warning, error.
	 */
	private static void notice(String clazz, DomContent content) {
		SiteDialog.out()
			.add(Html.aside()
				.clazz(clazz)
				.add(content));
	}
	public static void notice(DomContent content) {
		notice("notice", content);
	}
	public static void notice(String text) {
		notice(new DomText(text));
	}
	public static void notice(String format, Object... args) {
		notice(String.format(format, args));
	}
	public static void warn(DomContent content) {
		notice("notice notice-warn", content);
	}
	public static void warn(String text) {
		warn(new DomText(text));
	}
	public static void warn(String format, Object... args) {
		warn(String.format(format, args));
	}
	public static void fail(DomContent content) {
		notice("notice notice-error", content);
	}
	public static void fail(String text) {
		fail(new DomText(text));
	}
	public static void fail(String format, Object... args) {
		fail(String.format(format, args));
	}
	/*
	 * Get rid of the checked exception on AutoCloseable.
	 */
	public static interface Scope extends AutoCloseable {
		@Override
		void close();
	}
	/*
	 * It is sometimes efficient to create large dialogs that are more akin to tables than forms.
	 * While tables might be sometimes preferable, they have issues with horizontal space and flexibility.
	 * Dialog sections are more general at the cost of some verbosity.
	 * 
	 * Return Scope instead of SiteDialog, because implementation may change in the future.
	 */
	public static Scope section(String id) {
		var context = SiteDialog.out().children();
		if (!context.isEmpty()) {
			var last = context.get(context.size() - 1);
			if (!(last instanceof DomElement) || !((DomElement)last).tagname().equals("hr"))
				SiteDialog.out().add(Html.hr());
		}
		var section = Html.section();
		SiteDialog.out()
			.add(section)
			.add(Html.hr());
		var dialog = new SiteDialog(SiteDialog.slot(id), section);
		return new Scope() {
			@Override
			public void close() {
				dialog.close();
			}
		};
	}
	/*
	 * Figure may contain multiple items. Using try-with-resources for it is a neat way to allow that.
	 * Caption may contain links and what not, so we allow arbitrary DomContent in it.
	 */
	public static Scope figure(DomContent caption) {
		/*
		 * We want to make all captions optional by simply passing in null instead of the caption.
		 */
		if (caption == null) {
			return new Scope() {
				@Override
				public void close() {
				}
			};
		}
		DomElement container = Html.figure();
		SiteDialog.out().add(container);
		SiteDialog dialog = new SiteDialog(SiteDialog.slot(caption.text()), container);
		return new Scope() {
			@Override
			public void close() {
				container.add(Html.figcaption().add(caption));
				dialog.close();
			}
		};
	}
	public static Scope figure(String caption) {
		/*
		 * Make sure not to wrap null, which signals no caption at all.
		 */
		return figure(caption != null ? new DomText(caption) : null);
	}
	/*
	 * We will allow arbitrary images on API level and convert them to browser-compatible formats as needed.
	 * Currently we always convert to JPEG, which is a mistake. We should have an API to specify compression.
	 * Perhaps viewPng/viewJpeg. Or perhaps Viewer class, which would also allow CSS classes for size & border.
	 */
	private static String mime(byte[] image) {
		if (image[1] == 'P' && image[2] == 'N' && image[3] == 'G')
			return "image/png";
		if (image[0] == (byte)0xff && image[1] == (byte)0xd8)
			return "image/jpeg";
		if (image[0] == (byte)0x49 && image[1] == (byte)0x49 && image[2] == (byte)0x2a)
			return "image/tiff";
		if (image[0] == (byte)0x4d && image[1] == (byte)0x4d && image[2] == (byte)0x2a)
			return "image/tiff";
		if (image[0] == '<')
			return "image/svg+xml";
		throw new IllegalArgumentException();
	}
	/*
	 * When replacing one image with another, there is no image on the page for a short period of time.
	 * This happens even with data URIs that should theoretically have zero download times.
	 * When there is no image and no placeholder can be created due to missing width/height on img,
	 * the page will be rendered without image, which means the page will be significantly shorter.
	 * If the image appears near the end of the page, then the page will be scrolled up.
	 * When the image is loaded a split-second later, the page won't scroll back to its original position.
	 * This behavior will result in "jumpy" page when replacing one image with another.
	 * To avoid that, we will try hard to include width and height attributes on every img element.
	 * 
	 * Width/height attributes have no effect on how the image looks when it is fully loaded.
	 * They just result in properly sized placeholder when the image is missing.
	 * Including width/height attributes does not interfere with CSS styling.
	 * 
	 * This code has been optimized to scan only image header at the cost of some code complexity.
	 */
	private static final Pattern viewboxRe = Pattern.compile("<svg[^>]* viewBox=\"[-0-9.]+ [-0-9.]+ ([0-9]+)(?:\\.0)? ([0-9]+)(?:\\.0)?\"");
	private static Dimension size(byte[] image) {
		return Exceptions.log().get(Exceptions.sneak().supplier(() -> {
			switch (mime(image)) {
			case "image/png":
			case "image/jpeg":
				// https://stackoverflow.com/a/1560052/1981276
				try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
					var readers = ImageIO.getImageReaders(in);
					if (readers.hasNext()) {
						var reader = readers.next();
						try {
							reader.setInput(in);
							return new Dimension(reader.getWidth(0), reader.getHeight(0));
						} finally {
							reader.dispose();
						}
					}
				}
				break;
			case "image/svg+xml":
				/*
				 * Examine up to one kilobyte, because JFreeChart precedes viewBox attribute with lengthy style attribute.
				 */
				var header = new String(Arrays.copyOf(image, Math.min(image.length, 1000)), StandardCharsets.UTF_8);
				var matcher = viewboxRe.matcher(header);
				if (matcher.find())
					return new Dimension(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
				break;
			}
			return null;
		})).orElse(null);
	}
	private static byte[] toJpeg(byte[] image) {
		var original = Exceptions.wrap().get(() -> ImageIO.read(new ByteArrayInputStream(image)));
		if (original == null)
			throw new IllegalArgumentException("Unsupported image format.");
		int width = original.getWidth();
		int height = original.getHeight();
		var pixels = new int[width * height];
		original.getRGB(0, 0, width, height, pixels, 0, width);
		var converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < pixels.length; ++i)
			pixels[i] |= 0xff_00_00_00;
		converted.setRGB(0, 0, width, height, pixels, 0, width);
		JPEGImageWriteParam params = new JPEGImageWriteParam(null);
		params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		params.setCompressionQuality(0.9f);
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
		if (!writers.hasNext())
			throw new IllegalStateException("JPEG image writing is not supported.");
		ImageWriter writer = writers.next();
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		writer.setOutput(new MemoryCacheImageOutputStream(stream));
		Exceptions.sneak().run(() -> writer.write(null, new IIOImage(converted, null, null), params));
		return stream.toByteArray();
	}
	public static class Viewer {
		private String title;
		public Viewer title(String title) {
			this.title = title;
			return this;
		}
		private byte[] image;
		public Viewer image(byte[] image) {
			this.image = image;
			return this;
		}
		/*
		 * We have no way to determine reasonable maximum image width, so by default we ask.
		 * If the code that creates the image knows what size is appropriate, it should specify it.
		 * 
		 * Our CSS really supports only a few scale factors while we allow setting arbitrary integer factor here.
		 * Exposing specialized methods like scale125() would make the score hard to use when scaling is dynamically determined.
		 * Enum would be more type-safe, but it would be also more verbose and it would complicate dynamic size calculations.
		 */
		private int scale = -1;
		public Dialog.Viewer scale(int scale) {
			this.scale = scale;
			return this;
		}
		public void render() {
			/*
			 * Convert formats that aren't directly supported by browsers.
			 */
			switch (mime(image)) {
			case "image/tiff":
				image = toJpeg(image);
			}
			var img = Html.img();
			var size = size(image);
			if (size != null) {
				img
					.width(size.width)
					.height(size.height);
			}
			int scale = this.scale >= 0 ? this.scale : pickInt("Image size", new int[] { 50, 75, 100, 125, 150, 175, 200, 250, 0 }, 100, n -> n > 0 ? n + "%" : "Auto");
			try (var figure = figure(title)) {
				SiteDialog.out()
					.add(img
						/*
						 * TODO: We should probably round the scale factor to the nearest value supported by CSS.
						 */
						.clazz(scale > 0 ? "image-" + scale : null)
						/*
						 * All images are currently embedded as data URIs, which is inefficient.
						 * In the future, we should support automatic upload to CDN for frequently viewed images.
						 */
						.src("data:" + mime(image) + ";base64," + Base64.getEncoder().encodeToString(image))
						.freeze());
			}
		}
	}
	/*
	 * Images are common and all parameters may be used often, so provide overloads for all combinations.
	 */
	public static void view(byte[] image) {
		new Viewer().image(image).render();
	}
	public static void view(int scale, byte[] image) {
		new Viewer().scale(scale).image(image).render();
	}
	public static void view(String title, byte[] image) {
		new Viewer().title(title).image(image).render();
	}
	public static void view(String title, int scale, byte[] image) {
		new Viewer().title(title).scale(scale).image(image).render();
	}
	/*
	 * HTML tables are quite verbose. We need a widget to easily produce lots of tables.
	 * These tables are dumb. They have no built-in sorting, filtering, or pagination.
	 * Making tables too smart prevents us from drawing attention to the particular view of the data we want to show.
	 * Where multiple views are desirable, pickers can be used to introduce just the views that are useful.
	 * 
	 * Tables have closeable API for use in try-with-resources, so that we don't forget to render the constructed table.
	 * Most other widgets don't need it, because their construction involves only a simple sequence of fluent method calls.
	 * We could also render the table incrementally, but that's much more complicated to implement
	 * and it might prevent us from adding features in the future.
	 * 
	 * There's no setter chaining to avoid spurious resource leak warnings as this class is AutoCloseable.
	 */
	public static class Table implements AutoCloseable {
		private final String caption;
		public Table(String caption) {
			this.caption = caption;
		}
		public Table() {
			this(null);
		}
		/*
		 * The aim of the API is to have a simple table where we just throw data and it does everything right.
		 * We will nevertheless need some basic styling that is commonly used in tables.
		 * This is Pandora's box though. We have to be careful what we add here.
		 * 
		 * Styling is cell-specific. Header alignment follows dominant cell alignment.
		 * Rows currently cannot be styled. We instead encourage styling the most relevant cell.
		 */
		private static enum Alignment {
			CENTER, LEFT, RIGHT
		}
		private static enum Color {
			YES, NO, MAYBE
		}
		/*
		 * AutoCloseable with chained (this-returning) methods would trigger lots of resource leak warnings.
		 * We will therefore force every add() call to be independent, i.e. add() calls will not return the Table.
		 * We however want some sort of chaining for cell attributes, so we return Cell object instead that allows cell-local chaining.
		 */
		public static class Cell {
			DomContent content;
			Alignment alignment = Alignment.CENTER;
			Color color;
			/*
			 * We do not expose the enums in order to keep the API small and concise.
			 * This may result in some convoluted code when dynamic solution is needed,
			 * but we don't care, because this table is designed for simple cases.
			 */
			public Cell left() {
				alignment = Alignment.LEFT;
				return this;
			}
			public Cell right() {
				alignment = Alignment.RIGHT;
				return this;
			}
			/*
			 * Allow clearing left/right alignment.
			 * This is useful when column is left/right-aligned by default
			 * and exceptions are subsequently handled in a condition.
			 */
			public Cell center() {
				alignment = Alignment.CENTER;
				return this;
			}
			/*
			 * There is currently no way to clear coloring, because there's no nice method name for it.
			 * It's not a big deal, because coloring is almost never applied by default.
			 */
			public Cell yes() {
				color = Color.YES;
				return this;
			}
			public Cell no() {
				color = Color.NO;
				return this;
			}
			public Cell maybe() {
				color = Color.MAYBE;
				return this;
			}
		}
		/*
		 * Application code sometimes needs to set cell attributes conditionally and therefore without chaining.
		 * Storing the Cell object makes the application code more verbose. We will instead provide access to the last cell as a convenience.
		 * We could have also duplicated styling methods on Table class, but that's not necessary since most uses are unconditional.
		 */
		private Cell last;
		public Cell last() {
			return last;
		}
		/*
		 * Since the table is dumb and necessarily short (due to the lack of built-in pagination),
		 * we can afford a simpler and somewhat wasteful API to build tables with as little code as possible.
		 * The API is imperative, so that app code can use variables for subexpressions and conditions.
		 * We will be repeating column definitions with every cell definition,
		 * so that column definitions can share the line of code with corresponding cell definitions.
		 */
		private final Object2IntMap<String> columns = new Object2IntOpenHashMap<>();
		private int rows;
		private static class CellKey {
			final int column;
			final int row;
			CellKey(int column, int row) {
				this.column = column;
				this.row = row;
			}
			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof CellKey))
					return false;
				CellKey other = (CellKey)obj;
				return column == other.column && row == other.row;
			}
			@Override
			public int hashCode() {
				return 31 * row + column;
			}
		}
		private final Map<CellKey, Cell> cells = new HashMap<>();
		public Cell add(String column, DomContent content) {
			int columnAt;
			if (columns.containsKey(column))
				columnAt = columns.getInt(column);
			else {
				columnAt = columns.size();
				columns.put(column, columnAt);
			}
			/*
			 * We can avoid new row API by automatically adding a row when duplicate cell is detected.
			 */
			int row;
			if (rows <= 0) {
				row = 0;
				rows = 1;
			} else if (!cells.containsKey(new CellKey(columnAt, rows - 1)))
				row = rows - 1;
			else {
				row = rows;
				++rows;
			}
			last = new Cell();
			last.content = content;
			cells.put(new CellKey(columnAt, row), last);
			return last;
		}
		/*
		 * While column is in general anything that produces cell HTML, most columns are much simpler.
		 * Nearly all columns are plain text, often simply applying a formatter to a number.
		 */
		public Cell add(String column, String text) {
			return add(column, new DomText(text));
		}
		public Cell add(String column, String format, Object... args) {
			return add(column, String.format(format, args));
		}
		/*
		 * Table can be also asked to display arbitrary data and decide on formatting.
		 * We will provide overloads for primitive types and one overload that takes arbitrary object.
		 */
		public Cell add(String column, long number) {
			return add(column, Pretty.number(number));
		}
		public Cell add(String column, double number) {
			return add(column, Pretty.number(number));
		}
		public Cell add(String column, Object data) {
			return add(column, Pretty.any(data));
		}
		private String fallback;
		public void fallback(String fallback) {
			this.fallback = fallback;
		}
		@Override
		public void close() {
			if (rows == 0) {
				if (fallback != null)
					notice(fallback);
				if (caption != null)
					notice("Table is not shown, because it is empty: %s", caption);
				else
					notice("Table is not shown, because it is empty.");
			} else {
				try (var figure = Dialog.figure(caption)) {
					/*
					 * Standard scrolling div lets us create tables that scroll horizontally.
					 * This saves us from doing fancy tricks to reformat the table for narrow screens.
					 * It's also much nicer UI than breaking the table down into a sequence of property lists.
					 */
					SiteDialog.out()
						.add(Html.div().clazz("horizontal-scroll")
							.add(Html.table().clazz("table")
								.add(Html.thead()
									.add(Html.tr()
										.add(columns.keySet().stream()
											.sorted(Comparator.comparingInt(c -> columns.getInt(c)))
											.map(column -> {
												/*
												 * Header alignment follows dominant cell alignment.
												 */
												int at = columns.getInt(column);
												var alignments = IntStream.range(0, rows)
													.mapToObj(r -> cells.get(new CellKey(at, r)))
													.filter(Objects::nonNull)
													.map(c -> c.alignment)
													.collect(toList());
												long left = StreamEx.of(alignments).filterBy(a -> a, Alignment.LEFT).count();
												long right = StreamEx.of(alignments).filterBy(a -> a, Alignment.RIGHT).count();
												return Html.th()
													.clazz(left > alignments.size() / 2 ? "align-left" : right > alignments.size() / 2 ? "align-right" : null)
													.add(column);
											}))))
								.add(Html.tbody()
									.add(IntStream.range(0, rows)
										.mapToObj(r -> Html.tr()
											.add(IntStream.range(0, columns.size())
												.mapToObj(c -> cells.get(new CellKey(c, r)))
												.map(c -> c == null ? Html.td() : Html.td()
													.clazz(
														c.alignment != Alignment.CENTER ? "align-" + c.alignment.name().toLowerCase() : null,
														c.color != null ? c.color.name().toLowerCase() : null)
													.add(c.content))))))));
				}
			}
		}
	}
	/*
	 * Most of the controls will be simple pickers that will together form sort of a dialog box above controlled content.
	 * HTML's dl/dt/dd is perfect for this. We need some clever code to merge individual controls into single dl list.
	 */
	public static class Label implements AutoCloseable {
		private final DomElement dd;
		private final SiteDialog dialog;
		public Label(String title, String clazz) {
			List<DomContent> children = SiteDialog.out().children();
			DomElement container = null;
			if (!children.isEmpty()) {
				DomContent last = children.get(children.size() - 1);
				if (last instanceof DomElement) {
					DomElement element = (DomElement)last;
					if ("labelled-group".equals(element.clazz()))
						container = element;
				}
			}
			if (container == null) {
				container = Html.dl()
					.clazz("labelled-group");
				SiteDialog.out().add(container);
			}
			container
				.add(Html.dt()
					.key(SiteDialog.slot(title).nested("label").id())
					.add(title))
				.add(dd = Html.dd()
					.key(SiteDialog.slot(title).nested("content").id())
					/*
					 * Since all controls are in one big dl list and we don't want to insert intermediate divs here,
					 * we will apply control-specific CSS class on dd element. Label shouldn't have custom style anyway.
					 * 
					 * The intermediate divs are allowed by HTML spec, but we want to keep the HTML code simple.
					 * We might change this in the future in case something needs the div grouping.
					 */
					.clazz(clazz));
			/*
			 * Use the DD element as the container as otherwise Empty would not work inside the label.
			 */
			dialog = new SiteDialog(SiteDialog.slot(title), dd);
		}
		public Label(String title) {
			this(title, null);
		}
		@Override
		public void close() {
			dialog.close();
		}
	}
	public static void label(String title, String clazz, DomContent content) {
		try (var label = new Label(title, clazz)) {
			SiteDialog.out().add(content);
		}
	}
	public static void label(String title, DomContent content) {
		label(title, null, content);
	}
	/*
	 * Sometimes we just want to display information under a label instead of offering editable control.
	 * These controls are usually called "static" or some such in UI toolkits,
	 * but we name them label() here as they are essentially just thin wrappers around label() above.
	 */
	public static void label(String title, String text) {
		label(title, new DomText(text));
	}
	public static void label(String title, String format, Object... args) {
		label(title, String.format(format, args));
	}
	/*
	 * Control data is usually held in transient page-local storage, perhaps with persistence in user's preferences.
	 * We however want to allow editing of anything, so we provide this interface for arbitrary data sources.
	 * This should be probably moved up to pushmode or even hookless.
	 */
	public static interface Binding<T> {
		T get();
		void set(T value);
		/*
		 * We don't want to deal with fallbacks in every bind() method below.
		 * Besides handling fallbacks centrally here, this method allows chaining to fallback bindings.
		 */
		default Binding<T> orElse(T fallback) {
			Binding<T> outer = this;
			return new Binding<>() {
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
		default Binding<Integer> boxed(int fallback) {
			IntBinding outer = this;
			return new Binding<Integer>() {
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
	public static <T> Binding<T> bind(Supplier<T> getter, Consumer<T> setter) {
		return new Binding<>() {
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
	public static <K, V> Binding<V> bind(Map<K, V> map, K key) {
		return bind(() -> map.get(key), v -> {
			if (v != null)
				map.put(key, v);
			else
				map.remove(key);
		});
	}
	public static Binding<String> bindString(StringPreference preference) {
		return bind(preference::get, preference::set);
	}
	public static <T extends Enum<T>> Binding<T> bindEnum(EnumPreference<T> preference) {
		return bind(preference::get, preference::set);
	}
	public static IntBinding bindInt(IntPreference preference) {
		return bindInt(preference::get, preference::set);
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
		private Binding<String> binding;
		public Dialog.Editor binding(Binding<String> binding) {
			this.binding = binding;
			return this;
		}
		public Editor fallback(String fallback) {
			return binding(bindString(SiteDialog.slot(title).preferences().key("text").asString(fallback)));
		}
		public String render() {
			/*
			 * Text editor API guarantees non-null return no matter what parameters are passed in.
			 */
			binding = binding.orElse("");
			label(title, "text-picker", Html.input()
				.id(SiteDialog.slot(title).id())
				.type("text")
				.value(binding.get(), binding::set));
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
		private Binding<T> binding;
		public Picker<T> binding(Binding<T> binding) {
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
			label(title, "list-picker", Html.ul()
				.add(list.stream()
					.map(v -> Html.li()
						.clazz(Objects.equals(current, v) ? "list-picker-current" : null)
						.add(Html.button()
							.id(SiteDialog.slot(title).nested(naming.apply(v)).id())
							.onclick(() -> binding.set(v))
							.add(naming.apply(v))))));
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
			.binding(bindString(SiteDialog.slot(title).preferences().key("selected").asString(items[0])))
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
		private Binding<String> binding;
		private String selected;
		private final List<String> cases = new ArrayList<>();
		private final Empty placeholder;
		private boolean taken;
		public CasePicker(String title) {
			this.title = title;
			try (var label = new Label(title, "list-picker")) {
				placeholder = new Empty();
			}
		}
		public boolean is(String label) {
			if (cases.contains(label))
				throw new IllegalArgumentException("Duplicate case label.");
			if (binding == null) {
				binding = bindString(SiteDialog.slot(title).preferences().key("selected").asString(label));
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
				try {
					/*
					 * This notice might appear far away from the picker itself,
					 * because we used Empty placeholder and lots of other content could have been added meantime.
					 * We don't want to push it into the labeled control list where it could face layout issues.
					 * In the typical scenario however, if no case is taken, nothing is rendered
					 * and the notice appears directly under to case picker.
					 */
					if (!taken)
						warn("Nothing selected. Pick one option manually: %s", title);
					/*
					 * This code was copied from list picker. The only substantial modification is that
					 * we will not mark any case as selected if the binding contains garbage.
					 */
					placeholder.replace(Html.ul()
						.add(cases.stream()
							.map(c -> Html.li()
								.clazz(Objects.equals(selected, c) ? "list-picker-current" : null)
								.add(Html.button()
									.id(SiteDialog.slot(title).nested(c).id())
									.onclick(() -> binding.set(c))
									.add(c)))));
				} catch (Throwable ex) {
					/*
					 * The above code should never throw exceptions, especially when the try block terminates early.
					 * If it does throw, we don't want to propagate the exception as it could hide exception from try block.
					 * We will at least log the exception.
					 */
					Exceptions.log().handle(ex);
					/*
					 * Don't allow unused placeholder to escape to output HTML.
					 */
					placeholder.discard();
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
		private Binding<T> binding;
		public EnumPicker<T> binding(Binding<T> binding) {
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
				binding = bindEnum(SiteDialog.slot(title).preferences().key("selected").asEnum(fallback));
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
				binding = bindInt(SiteDialog.slot(title).preferences().key("selected").asInt(fallback));
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
		List<DomContent> children = SiteDialog.out().children();
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
			SiteDialog.out().add(container);
		}
		container
			.add(Html.button()
				.id(SiteDialog.slot(name).id())
				.add(name)
				/*
				 * TODO: Need exception handler here. Exception should be shown until next run.
				 * We might want some progress reporting around it too if long processes end up here.
				 */
				.onclick(action));
	}
}
