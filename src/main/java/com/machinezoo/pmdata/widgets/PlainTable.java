// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import static java.util.stream.Collectors.*;
import java.util.*;
import java.util.stream.*;
import com.machinezoo.pmdata.formatters.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;
import it.unimi.dsi.fastutil.objects.*;
import one.util.streamex.*;

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
@DraftApi
public class PlainTable implements AutoCloseable {
	private final String caption;
	public PlainTable(String caption) {
		this.caption = caption;
	}
	public PlainTable() {
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
	/*
	 * AutoCloseable with chained (this-returning) methods would trigger lots of resource leak warnings.
	 * We will therefore force every add() call to be independent, i.e. add() calls will not return the Table.
	 * We however want some sort of chaining for cell attributes, so we return Cell object instead that allows cell-local chaining.
	 */
	public static class Cell {
		private DomContent content;
		private Alignment alignment = Alignment.CENTER;
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
		private Tone tone;
		public Cell tone(Tone tone) {
			this.tone = tone;
			return this;
		}
	}
	/*
	 * Application code sometimes needs to set cell attributes conditionally and therefore without chaining.
	 * Storing the Cell object makes the application code more verbose. We will instead provide access to the last cell as a convenience.
	 * We could have also duplicated styling methods on Table class, but that's not necessary since most uses are unconditional.
	 */
	private PlainTable.Cell last;
	public PlainTable.Cell last() {
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
			if (!(obj instanceof PlainTable.CellKey))
				return false;
			var other = (PlainTable.CellKey)obj;
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
	 */
	public Cell add(String column, Object data) {
		return add(column, Pretty.object().format(data));
	}
	private String fallback;
	public void fallback(String fallback) {
		this.fallback = fallback;
	}
	@Override
	public void close() {
		if (rows == 0) {
			if (fallback != null)
				Notice.info(fallback);
			if (caption != null)
				Notice.info("Table is not shown, because it is empty: %s", caption);
			else
				Notice.info("Table is not shown, because it is empty.");
		} else {
			try (var figure = Figure.define(caption)) {
				/*
				 * Standard scrolling div lets us create tables that scroll horizontally.
				 * This saves us from doing fancy tricks to reformat the table for narrow screens.
				 * It's also much nicer UI than breaking the table down into a sequence of property lists.
				 */
				SiteFragment.get()
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
													c.tone != null ? c.tone.css() : null)
												.add(c.content))))))));
			}
		}
	}
}