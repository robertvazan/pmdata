// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.charts;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.data.xy.*;
import it.unimi.dsi.fastutil.doubles.*;
import it.unimi.dsi.fastutil.ints.*;

/*
 * This is not intended to be a fully featured XY chart.
 * It is deliberately limited to the most basic features required for quick data visualization.
 */
public class XYChart {
	public static class Point {
		public final double x;
		public final double y;
		public Point(double x, double y) {
			this.x = x;
			this.y = y;
		}
	}
	/*
	 * We refrain from adding styling features, but this actually expands the spectrum of data that can be visualized.
	 * Scatter plot is for random 2D data while line plot is for series. Markers are for a few 1-dimensional data points.
	 */
	public static enum Style {
		LINE,
		SCATTER,
		CONNECTED,
		MARKER
	}
	public static class Series {
		private String label;
		public Series label(String label) {
			this.label = label;
			return this;
		}
		public Series() {
		}
		public Series(String label) {
			this.label = label;
		}
		private Style style = Style.LINE;
		public Series style(Style style) {
			Objects.requireNonNull(style);
			this.style = style;
			return this;
		}
		private final DoubleList xs = new DoubleArrayList();
		private final DoubleList ys = new DoubleArrayList();
		public Series add(double x, double y) {
			xs.add(x);
			ys.add(y);
			return this;
		}
		public Series add(Stream<Point> points) {
			points.forEach(p -> add(p.x, p.y));
			return this;
		}
		public Series add(Collection<Point> points) {
			return add(points.stream());
		}
		public Series add(Point... points) {
			return add(Arrays.stream(points));
		}
		public Series addX(double value) {
			xs.add(value);
			return this;
		}
		public Series addX(DoubleStream values) {
			values.forEach(this::addX);
			return this;
		}
		public Series addX(double... values) {
			return addX(Arrays.stream(values));
		}
		public Series addX(DoubleCollection values) {
			return addX(values.toDoubleArray());
		}
		public Series addX(IntStream values) {
			values.forEach(this::addX);
			return this;
		}
		public Series addX(int... values) {
			return addX(Arrays.stream(values));
		}
		public Series addX(IntCollection values) {
			return addX(values.toIntArray());
		}
		public Series addY(double value) {
			ys.add(value);
			return this;
		}
		public Series addY(DoubleStream values) {
			values.forEach(this::addY);
			return this;
		}
		public Series addY(double... values) {
			return addY(Arrays.stream(values));
		}
		public Series addY(DoubleCollection values) {
			return addY(values.toDoubleArray());
		}
		public Series addY(IntStream values) {
			values.forEach(this::addY);
			return this;
		}
		public Series addY(int... values) {
			return addY(Arrays.stream(values));
		}
		public Series addY(IntCollection values) {
			return addY(values.toIntArray());
		}
		/*
		 * This really only works for linear X axis.
		 */
		public Series sample(int samples, double minX, double maxX, DoubleUnaryOperator function) {
			for (int i = 0; i < samples; ++i) {
				double x = i * (maxX - minX) / (samples - 1) + minX;
				add(x, function.applyAsDouble(x));
			}
			return this;
		}
	}
	public XYChart() {
	}
	private String title;
	public XYChart title(String title) {
		this.title = title;
		return this;
	}
	private String labelX = "X";
	public XYChart labelX(String labelX) {
		this.labelX = labelX;
		return this;
	}
	private String labelY = "Y";
	public XYChart labelY(String labelY) {
		this.labelY = labelY;
		return this;
	}
	public XYChart(String title) {
		this.title = title;
	}
	private int width = 800;
	private int height = 600;
	public XYChart size(int width, int height) {
		this.width = width;
		this.height = height;
		return this;
	}
	private int scale = 100;
	public XYChart scale(int scale) {
		this.scale = scale;
		return this;
	}
	private boolean logX;
	private boolean logY;
	public XYChart logX(boolean log) {
		logX = log;
		return this;
	}
	public XYChart logY(boolean log) {
		logY = log;
		return this;
	}
	private List<Series> data = new ArrayList<>();
	public XYChart add(Series series) {
		data.add(series);
		return this;
	}
	/*
	 * Use default JFreeChart theme. This might need to be customized in the future.
	 * JFreeChart is designed for Swing. We might want settings that work better with SVG.
	 */
	private static final ChartTheme THEME = new StandardChartTheme("JFree");
	private static boolean valid(double value, boolean logarithmic) {
		if (logarithmic && value <= 0)
			return false;
		return Double.isFinite(value);
	}
	public void view() {
		var jdata = new XYSeriesCollection();
		for (var series : data) {
			if (series.style != Style.MARKER) {
				if (series.xs.size() != series.ys.size())
					throw new IllegalStateException("Number of X and Y coordinates must be the same.");
				/*
				 * Don't sort the data as that may damage the chart if the points are drawn in unexpected order.
				 */
				var jseries = new XYSeries(series.label, false);
				for (int i = 0; i < series.xs.size(); ++i) {
					double x = series.xs.getDouble(i);
					double y = series.ys.getDouble(i);
					if (valid(x, logX) && valid(y, logY))
						jseries.add(x, y);
				}
				jdata.addSeries(jseries);
			}
		}
		var axisX = logX ? new LogarithmicAxis(labelX) : new NumberAxis(labelX);
		/*
		 * JFreeChart enables zero inclusion by default.
		 */
		axisX.setAutoRangeIncludesZero(false);
		var axisY = logY ? new LogarithmicAxis(labelY) : new NumberAxis(labelY);
		axisY.setAutoRangeIncludesZero(false);
		var renderer = new XYLineAndShapeRenderer(true, false);
		var plot = new XYPlot(jdata, axisX, axisY, renderer);
		for (var series : data) {
			if (series.style == Style.MARKER) {
				for (var x : series.xs) {
					var marker = new ValueMarker(x);
					marker.setLabel(series.label);
					plot.addDomainMarker(marker);
				}
				for (var y : series.ys) {
					var marker = new ValueMarker(y);
					marker.setLabel(series.label);
					plot.addRangeMarker(marker);
				}
			}
		}
		/*
		 * No point showing a legend if we are showing only one curve.
		 */
		boolean legend = data.stream().filter(s -> s.style != Style.MARKER).count() > 1;
		var chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, legend);
		THEME.apply(chart);
		int seriesId = 0;
		for (var series : data) {
			if (series.style != Style.MARKER) {
				switch (series.style) {
				case SCATTER:
					renderer.setSeriesLinesVisible(seriesId, false);
					renderer.setSeriesShapesVisible(seriesId, true);
					break;
				case CONNECTED:
					renderer.setSeriesShapesVisible(seriesId, true);
					break;
				default:
					break;
				}
				++seriesId;
			}
		}
		new JFreeChartImage()
			.chart(chart)
			.size(width, height)
			.scale(scale)
			/*
			 * Use PNG format only when the number of points on the chart is clearly unreasonable.
			 */
			.view(data.stream().mapToInt(s -> s.xs.size()).max().getAsInt() > 2_000);
	}
}