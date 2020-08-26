// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import static java.util.stream.Collectors.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import java.util.stream.*;
import javax.imageio.*;
import javax.swing.*;
import org.apache.batik.dom.*;
import org.apache.batik.svggen.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.data.statistics.*;
import org.jfree.data.xy.*;
import org.w3c.dom.*;
import com.machinezoo.noexception.*;
import com.machinezoo.stagean.*;
import it.unimi.dsi.fastutil.doubles.*;
import one.util.streamex.*;
import smile.plot.*;

/*
 * This is narrowly specialized charting API to be used in data science.
 * The API is designed to be as concise as possible and to render into SiteFragment.
 * 
 * There are several libraries producing charts. This class chooses best one for every chart type.
 * Currently we only support JFreeChart and Smile. Here are reasons for exclusion of others:
 * - Orson Charts (also from JFree) is GPL-encumbered.
 * - XChart is lacking in features. JFreeChart and Smile are its superset.
 * - Tablesaw only renders through Plot.ly in JavaScript.
 * - Morpheus has weird API apparently designed to show charts in Swing windows.
 * - DataMelt asks for premium subscription all the time, so it isn't really free.
 * 
 * Both JFreeChart and Smile are licensed under LGPL, which is important for licensing of this library.
 * JFreeChart book is paid for, but we don't need it, because there seem to be tutorials for everything on the Internet.
 * 
 * JFreeChart is generally preferred as it is more featureful, but Smile has some unique features.
 */
@DraftApi("multiple separate classes")
public class Charts {
	/*
	 * The actual size (800x600) is not that important in SVG, because it is scalable,
	 * but we choose width equal to paragraph width (800px),
	 * because this is the typical and almost always minimal size for charts,
	 * so rendering at this size ensures readability in all chart sizes.
	 * Height is chosen to be 600, because most charts are designed to look good in 4:3 aspect ratio.
	 */
	private static final int defaultWidth = 800;
	private static final int defaultHeight = 600;
	private static byte[] toSvg(int width, int height, Consumer<Graphics2D> painter) {
		/*
		 * JFreeChart and Smile charts can be exported to PNG much more easily, but we want scalable SVG output.
		 * Even though we usually end up displaying the chart at fixed 800x600 size anyway,
		 * we want to ensure it looks good on high DPI displays and when scaled down on small displays.
		 * 
		 * Both JFreeSVG and Batik offer Graphics2D implementation with SVG output,
		 * but JFreeSVG is GPL-encumbered, which is not acceptable in this library,
		 * so we stick with Batik and tolerate the slightly (imperceptibly) slower rendering.
		 * 
		 * This code has been pieced together from a number of samples found on the Internet.
		 */
		Document document = GenericDOMImplementation.getDOMImplementation().createDocument(null, "svg", null);
		SVGGraphics2D generator = new SVGGraphics2D(document);
		/*
		 * Call to SVGGraphics2D.setSVGCanvasSize() will ensure the SVG will have width and height attributes.
		 * This will not generate viewBox attribute though.
		 */
		generator.setSVGCanvasSize(new Dimension(width, height));
		painter.accept(generator);
		/*
		 * We could call SVGGraphics2D.stream() overload that doesn't take Element parameter.
		 * It would be simpler, but the generated SVG would not have viewBox attribute.
		 * We have to add viewBox attribute explicitly.
		 */
		Element root = generator.getRoot();
		root.setAttributeNS(null, "viewBox", "0 0 " + width + " " + height);
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Exceptions.sneak().run(() -> generator.stream(root, new OutputStreamWriter(buffer, "UTF-8"), true, false));
		return buffer.toByteArray();
	}
	public static byte[] toSvg(int width, int height, JFreeChart chart) {
		/*
		 * The Rectangle parameter here determines how big the SVG shapes will be,
		 * but it does not automatically generate width/height nor viewBox attributes.
		 */
		return toSvg(width, height, g -> chart.draw(g, new Rectangle(width, height)));
	}
	public static byte[] toSvg(JFreeChart chart) {
		return toSvg(defaultWidth, defaultHeight, chart);
	}
	public static byte[] toSvg(int width, int height, PlotCanvas plot) {
		/*
		 * Smile's PlotCanvas sets its preferred size to 1600x1200 in the constructor.
		 * We have to override it here to get it sized properly.
		 */
		plot.setPreferredSize(new Dimension(width, height));
		/*
		 * The Headless code was taken straight from Smile FAQ.
		 */
		Headless headless = new Headless(plot);
		headless.pack();
		headless.setVisible(true);
		/*
		 * The Headless-related calls above trigger some Swing layout events
		 * that get posted in Swing's event dispatcher thread.
		 * And then we are in a race with the event dispatcher thread.
		 * If the event dispatcher thread wins, layout is completed in time and the chart looks right.
		 * If we win, layout is not finished in time and our chart will be rendered at 0x0 size.
		 * 
		 * This bug was reported to Smile devs:
		 * https://github.com/haifengl/smile/issues/507
		 * 
		 * In order to prevent the race rule and subsequent image corruption,
		 * we will wait for all scheduled events to be processed by posting an empty event of our own.
		 * 
		 * Theoretically, there could be more race rules, because all of the chart code is Swing-based.
		 * Even just solving this one race rule with the empty event trick is questionable,
		 * because the layout events are already running concurrently while the code above is still finishing.
		 * We should wrap all chart-related code in an event posted to event dispatcher thread
		 * and then yield (create secondary loop via EventQueue.createSecondaryLoop) here.
		 * But that's a lot of complexity for questionable gain
		 * and most importantly it won't work when callers create custom charts on their own.
		 */
		Exceptions.sneak().run(() -> {
			SwingUtilities.invokeAndWait(() -> {
			});
		});
		return toSvg(width, height, g -> plot.paint(g));
	}
	public static byte[] toSvg(PlotCanvas plot) {
		return toSvg(defaultWidth, defaultHeight, plot);
	}
	/*
	 * While SVG is usually preferable, sometimes we might be interested in a PNG image,
	 * perhaps because SVG image doesn't always render well or it is too large or slow.
	 */
	public static byte[] toPng(int width, int height, JFreeChart chart) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Exceptions.sneak().run(() -> ChartUtils.writeChartAsPNG(buffer, chart, width, height));
		return buffer.toByteArray();
	}
	public static byte[] toPng(JFreeChart chart) {
		return toPng(defaultWidth, defaultHeight, chart);
	}
	public static byte[] toPng(int width, int height, PlotCanvas plot) {
		/*
		 * Setup PlotCanvas like in SVG code above.
		 */
		plot.setPreferredSize(new Dimension(width, height));
		Headless headless = new Headless(plot);
		headless.pack();
		headless.setVisible(true);
		/*
		 * Guard against race rules like in SVG code above.
		 */
		Exceptions.sneak().run(() -> {
			SwingUtilities.invokeAndWait(() -> {
			});
		});
		/*
		 * Here the Smile FAQ would make us call plot.save(File), but we don't want to create temporary files.
		 * The code below was pulled from plot.save(File) and modified to write to an in-memory buffer instead.
		 */
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		plot.print(graphics);
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Exceptions.sneak().run(() -> ImageIO.write(image, "PNG", buffer));
		return buffer.toByteArray();
	}
	public static byte[] toPng(PlotCanvas plot) {
		return toPng(defaultWidth, defaultHeight, plot);
	}
	/*
	 * We cannot always provide a specialized chart method, so allow viewing arbitrary charts here.
	 */
	public static void viewSvg(int scale, int width, int height, JFreeChart chart) {
		Dialog.view(scale, toSvg(width, height, chart));
	}
	public static void viewSvg(int width, int height, JFreeChart chart) {
		Dialog.view(toSvg(width, height, chart));
	}
	public static void viewSvg(int scale, JFreeChart chart) {
		/*
		 * If only scale is given, generate the image with assumption that it will be actually shown at this scale.
		 */
		Dialog.view(scale, toSvg(scale * defaultWidth / 100, scale * defaultHeight / 100, chart));
	}
	public static void viewSvg(JFreeChart chart) {
		Dialog.view(toSvg(chart));
	}
	/*
	 * Like above, but for PlotCanvas.
	 */
	public static void viewSvg(int scale, int width, int height, PlotCanvas plot) {
		Dialog.view(scale, toSvg(width, height, plot));
	}
	public static void viewSvg(int width, int height, PlotCanvas plot) {
		Dialog.view(toSvg(width, height, plot));
	}
	public static void viewSvg(int scale, PlotCanvas plot) {
		Dialog.view(scale, toSvg(scale * defaultWidth / 100, scale * defaultHeight / 100, plot));
	}
	public static void viewSvg(PlotCanvas plot) {
		Dialog.view(toSvg(plot));
	}
	/*
	 * Like above, but for PNG.
	 */
	public static void viewPng(int scale, int width, int height, JFreeChart chart) {
		Dialog.view(scale, toPng(width, height, chart));
	}
	public static void viewPng(int width, int height, JFreeChart chart) {
		Dialog.view(toPng(width, height, chart));
	}
	public static void viewPng(int scale, JFreeChart chart) {
		Dialog.view(scale, toPng(scale * defaultWidth / 100, scale * defaultHeight / 100, chart));
	}
	public static void viewPng(JFreeChart chart) {
		Dialog.view(toPng(chart));
	}
	public static void viewPng(int scale, int width, int height, PlotCanvas plot) {
		Dialog.view(scale, toPng(width, height, plot));
	}
	public static void viewPng(int width, int height, PlotCanvas plot) {
		Dialog.view(toPng(width, height, plot));
	}
	public static void viewPng(int scale, PlotCanvas plot) {
		Dialog.view(scale, toPng(scale * defaultWidth / 100, scale * defaultHeight / 100, plot));
	}
	public static void viewPng(PlotCanvas plot) {
		Dialog.view(toPng(plot));
	}
	/*
	 * By default, we will always use SVG format even if we know image dimensions.
	 * This ensures correct rendering on high DPI displays and scaling on small screens.
	 * We might wish to intelligently choose PNG in some cases in the future though.
	 */
	public static void view(int scale, int width, int height, JFreeChart chart) {
		viewSvg(scale, width, height, chart);
	}
	public static void view(int width, int height, JFreeChart chart) {
		viewSvg(width, height, chart);
	}
	public static void view(int scale, JFreeChart chart) {
		viewSvg(scale, chart);
	}
	public static void view(JFreeChart chart) {
		viewSvg(chart);
	}
	public static void view(int scale, int width, int height, PlotCanvas plot) {
		viewSvg(scale, width, height, plot);
	}
	public static void view(int width, int height, PlotCanvas plot) {
		viewSvg(width, height, plot);
	}
	public static void view(int scale, PlotCanvas plot) {
		viewSvg(scale, plot);
	}
	public static void view(PlotCanvas plot) {
		viewSvg(plot);
	}
	/*
	 * We will provide specialized classes to generate commonly used charts.
	 * We won't support every possible option and focus on common data visualization needs.
	 * Special charts can be still shown via view() methods.
	 * 
	 * Chart classes represent an empty canvas where many different things can be drawn.
	 * Methods of chart classes specify what and how will be drawn.
	 * Some methods specify features of the chart like axes or chart size.
	 * Every class has view() method to put the configured chart on the screen.
	 * 
	 * Since we often want to just throw some data on the screen,
	 * we will also provide static methods to quickly render single series data.
	 * We want to keep the number of parameters to the minimum.
	 *
	 * In order to support quickly throwing data on the screen, we will accept data in a variety of formats,
	 * specifically (primitive) arrays, (primitive) collections, and (primitive) streams.
	 * 
	 * When we have to choose between caption embedded in the chart and separate figcaption in HTML,
	 * we choose embedded caption, because it makes the charts more meaningful in image search.
	 * Even better if we can use axis name instead of the embedded caption.
	 */
	public static class Point {
		public final double x;
		public final double y;
		public Point(double x, double y) {
			this.x = x;
			this.y = y;
		}
	}
	/*
	 * Use default JFreeChart theme. This might need to be customized in the future.
	 * JFreeChart is designed for Swing. We might want settings that work better with SVG.
	 */
	private static final ChartTheme theme = new StandardChartTheme("JFree");
	private static class XYData {
		final String label;
		final Collection<Point> points;
		XYData(String label, Collection<Point> points) {
			this.label = label;
			this.points = points;
		}
		boolean lines = true;
		boolean dots = false;
		Function<List<Point>, List<Point>> transform;
	}
	public static class XY {
		public XY() {
		}
		private String title;
		public XY title(String title) {
			this.title = title;
			return this;
		}
		private String labelX;
		public XY labelX(String labelX) {
			this.labelX = labelX;
			return this;
		}
		private String labelY;
		public XY labelY(String labelY) {
			this.labelY = labelY;
			return this;
		}
		public XY(String title) {
			this.title = title;
		}
		public XY(String title, String labelX, String labelY) {
			this.title = title;
			this.labelX = labelX;
			this.labelY = labelY;
		}
		private int scale = -1;
		private int width = defaultWidth;
		private int height = defaultHeight;
		public XY size(int scale, int width, int height) {
			this.scale = scale;
			this.width = width;
			this.height = height;
			return this;
		}
		public XY size(int width, int height) {
			return size(-1, width, height);
		}
		public XY size(int scale) {
			return size(scale, scale * defaultWidth / 100, scale * defaultHeight / 100);
		}
		private boolean logX;
		private boolean logY;
		public XY logX() {
			logX = true;
			return this;
		}
		public XY logY() {
			logY = true;
			return this;
		}
		private boolean rangeX;
		private double minX;
		private double maxX;
		public XY rangeX(double min, double max) {
			rangeX = true;
			minX = min;
			maxX = max;
			return this;
		}
		private boolean rangeY;
		private double minY;
		private double maxY;
		public XY rangeY(double min, double max) {
			rangeY = true;
			minY = min;
			maxY = max;
			return this;
		}
		/*
		 * We won't see more than 4000 points on 4K display and we don't care about subpixel lines at that resolution.
		 */
		private int steps = 4000;
		public XY steps(int steps) {
			this.steps = steps;
			return this;
		}
		private List<XYData> sets = new ArrayList<>();
		private static List<Point> list(Stream<Point> stream) {
			return stream.collect(toList());
		}
		private static List<Point> list(Point[] array) {
			return Arrays.asList(array);
		}
		private boolean filter(Point point) {
			/*
			 * Infinite values cannot be shown on any axis.
			 */
			if (!Double.isFinite(point.x) || !Double.isFinite(point.y))
				return false;
			/*
			 * Zero and negative values cannot be shown on logarithmic axis.
			 */
			if (logX && point.x <= 0)
				return false;
			if (logY && point.y <= 0)
				return false;
			return true;
		}
		private XY add(XYData data) {
			sets.add(data);
			return this;
		}
		public XY line(String label, Collection<Point> points) {
			return add(new XYData(label, points));
		}
		public XY line(String label, Stream<Point> stream) {
			return line(label, list(stream));
		}
		public XY line(String label, Point[] array) {
			return line(label, list(array));
		}
		public XY dots(String label, Collection<Point> points) {
			var data = new XYData(label, points);
			data.lines = false;
			data.dots = true;
			return add(data);
		}
		public XY dots(String label, Stream<Point> stream) {
			return dots(label, list(stream));
		}
		public XY dots(String label, Point[] array) {
			return dots(label, list(array));
		}
		public XY dotted(String label, Collection<Point> points) {
			var data = new XYData(label, points);
			data.dots = true;
			return add(data);
		}
		public XY dotted(String label, Stream<Point> stream) {
			return dotted(label, list(stream));
		}
		public XY dotted(String label, Point[] array) {
			return dotted(label, list(array));
		}
		public XY stairsX(String label, Collection<Point> points) {
			var data = new XYData(label, points);
			data.transform = filtered -> {
				var transformed = new ArrayList<Point>();
				double lastY = Double.NEGATIVE_INFINITY;
				for (var point : filtered) {
					/*
					 * Draw "stairs" by first moving along X and then along Y axis.
					 */
					if (lastY > Double.NEGATIVE_INFINITY)
						transformed.add(new Point(point.x, lastY));
					transformed.add(point);
					lastY = point.y;
				}
				return transformed;
			};
			return add(data);
		}
		public XY stairsX(String label, Stream<Point> stream) {
			return stairsX(label, list(stream));
		}
		public XY stairsX(String label, Point[] array) {
			return stairsX(label, list(array));
		}
		public XY sample(String label, DoubleUnaryOperator function, double minX, double maxX, int samples) {
			return line(label, IntStream.range(0, samples).mapToObj(n -> {
				double x = n * (maxX - minX) / (samples - 1) + minX;
				return new Point(x, function.applyAsDouble(x));
			}));
		}
		private final DoubleList verticals = new DoubleArrayList();
		public XY vertical(double x) {
			verticals.add(x);
			return this;
		}
		private final DoubleList horizontals = new DoubleArrayList();
		public XY horizontal(double x) {
			horizontals.add(x);
			return this;
		}
		/*
		 * When removing too small steps from the data, we have to measure distance of log values on logarithmic axis.
		 */
		private double logX(double x) {
			return logX ? Math.log(x) : x;
		}
		private double logY(double y) {
			return logY ? Math.log(y) : y;
		}
		public void view() {
			var dataset = new XYSeriesCollection();
			var all = StreamEx.of(sets).toMap(data -> StreamEx.of(data.points).filter(this::filter).toList());
			if (!rangeX) {
				/*
				 * Use 1 as default for empty list to avoid Math.log() failure on 0.
				 */
				minX = all.values().stream().flatMapToDouble(l -> l.stream().mapToDouble(p -> p.x)).min().orElse(1);
				maxX = all.values().stream().flatMapToDouble(l -> l.stream().mapToDouble(p -> p.x)).max().orElse(1);
			}
			if (!rangeY) {
				minY = all.values().stream().flatMapToDouble(l -> l.stream().mapToDouble(p -> p.y)).min().orElse(1);
				maxY = all.values().stream().flatMapToDouble(l -> l.stream().mapToDouble(p -> p.y)).max().orElse(1);
			}
			double stepX = (logX(maxX) - logX(minX)) / steps;
			double stepY = (logY(maxY) - logY(minY)) / steps;
			for (var data : sets) {
				var filtered = all.get(data);
				var transformed = data.transform != null ? data.transform.apply(filtered) : filtered;
				List<Point> corners = new ArrayList<>();
				if (data.lines && !data.dots) {
					/*
					 * Chart may contain redundant points that just lie on a line between neighboring points.
					 * Such points add nothing visible to the chart, so we will try to filter them out.
					 * We cannot easily remove all of these points, but we can remove points on straight horizontal and vertical lines,
					 * which are easy to detect and they are very common in ROC and other "staircase" charts.
					 */
					for (int i = 0; i < transformed.size(); ++i) {
						var point = transformed.get(i);
						if (i == 0 || i == transformed.size() - 1)
							corners.add(point);
						else {
							var left = corners.get(corners.size() - 1);
							var right = transformed.get(i + 1);
							/*
							 * Corners are all points that are not on a straight horizontal or vertical line between their neighbors.
							 */
							if (!(point.x == left.x && point.x == right.x || point.y == left.y && point.y == right.y))
								corners.add(point);
						}
					}
				} else
					corners = transformed;
				/*
				 * Don't sort the data as that may damage the chart if the points are drawn in unexpected order.
				 */
				var series = new XYSeries(data.label, false);
				Point last = null;
				for (var point : corners) {
					/*
					 * Don't show small steps that just make the SVG big and slow.
					 */
					if (last == null || Math.abs(logX(last.x) - logX(point.x)) >= stepX || Math.abs(logY(last.y) - logY(point.y)) >= stepY) {
						series.add(point.x, point.y);
						last = point;
					}
				}
				dataset.addSeries(series);
			}
			var axisX = logX ? new LogarithmicAxis(labelX) : new NumberAxis(labelX);
			/*
			 * JFreeChart enables zero inclusion by default.
			 */
			axisX.setAutoRangeIncludesZero(false);
			if (rangeX)
				axisX.setRange(minX, maxX);
			var axisY = logY ? new LogarithmicAxis(labelY) : new NumberAxis(labelY);
			axisY.setAutoRangeIncludesZero(false);
			if (rangeY)
				axisY.setRange(minY, maxY);
			var renderer = new XYLineAndShapeRenderer(true, false);
			var plot = new XYPlot(dataset, axisX, axisY, renderer);
			for (var vertical : verticals)
				if (Double.isFinite(vertical) && (!logX || vertical > 0))
					plot.addDomainMarker(new ValueMarker(vertical));
			for (var horizontal : horizontals)
				if (Double.isFinite(horizontal) && (!logY || horizontal > 0))
					plot.addRangeMarker(new ValueMarker(horizontal));
			/*
			 * No point showing a legend if we are showing only one curve.
			 */
			var chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, sets.size() > 1 ? true : false);
			theme.apply(chart);
			for (int i = 0; i < sets.size(); ++i) {
				var data = sets.get(i);
				if (!data.lines)
					renderer.setSeriesLinesVisible(i, false);
				if (data.dots)
					renderer.setSeriesShapesVisible(i, true);
			}
			Dialog.view(scale, toSvg(width, height, chart));
		}
	}
	/*
	 * We will use automatic range and bin count for histograms,
	 * because static histogram configuration requires too much maintenance.
	 */
	public static void histogram(String title, double[] values) {
		/*
		 * Filter out values that cannot be displayed in the histogram.
		 */
		values = Arrays.stream(values).filter(Double::isFinite).toArray();
		if (values.length == 0)
			Dialog.notice("Cannot show empty histogram: %s", title);
		else {
			/*
			 * We are using JFreeChart's default histogram chart, which has quite a lot of disadvantages.
			 * There is no way to show cumulative histogram and no way to use an already computed histogram.
			 * We might want to use something better in the future or to manually configure JFreeChart chart.
			 * 
			 * Smile's Histogram.plot() looks better, but it wastes screen space
			 * and shows relative frequency instead of frequency.
			 */
			var dataset = new HistogramDataset();
			/*
			 * Frequency is better than relative frequency, because it shows more information.
			 * Relative frequency would provide us with percentages,
			 * but those are largely useless as they vary with bin count.
			 */
			dataset.setType(HistogramType.FREQUENCY);
			/*
			 * Square root rule for bin count is taken from Smile and it is reportedly used everywhere.
			 * We however wouldn't create more bins than there are unique samples to avoid oddly looking histograms.
			 * Minimum of 5 bins is enforced. This constant is taken from Smile and it is rather arbitrary.
			 */
			int bins = Math.max(5, Math.min((int)Math.sqrt(values.length), (int)Arrays.stream(values).distinct().count()));
			dataset.addSeries(title, values, bins);
			var chart = ChartFactory.createHistogram(null, title, "Frequency", dataset);
			/*
			 * We are showing only one set of data. No point showing a legend.
			 */
			chart.removeLegend();
			/*
			 * Histograms by nature do not have much detail in them, so limit their size.
			 */
			view(100, chart);
		}
	}
	public static void histogram(String title, int[] values) {
		histogram(title, IntStreamEx.of(values).asDoubleStream().toArray());
	}
	/*
	 * This is pure data dump heatmap without giving the user any axis labels or ticks.
	 * Color scale is automatic to minimize configuration.
	 * This is useful only to get a feel for the data.
	 */
	public static void heatmap(String title, double[][] values) {
		var plot = Heatmap.plot(values);
		plot.setTitle(title);
		/*
		 * Heatmap should be square.
		 */
		view(defaultWidth, defaultWidth, plot);
	}
	public static void roc(String title, String labelX, String labelY, List<Point> points) {
		/*
		 * In order to keep the ROC chart square, we will determine range automatically.
		 * Upper bound is always 1 as ROC values are in range 0..1.
		 * Lower bound however cannot be zero, because then the axis couldn't be logarithmic.
		 * We will instead pick the lowest non-zero X or Y value.
		 */
		double min = Math.min(0.1, DoubleStream.concat(points.stream().mapToDouble(p -> p.x), points.stream().mapToDouble(p -> p.y)).filter(v -> v > 0).min().orElse(1));
		new XY(title, labelX, labelY)
			/*
			 * ROC with logarithmic axes shows more detail.
			 */
			.logX().logY()
			.rangeX(min, 1)
			.rangeY(min, 1)
			/*
			 * ROC chart should be square.
			 */
			.size(defaultWidth, defaultWidth)
			.line(title, points)
			.view();
	}
}
