// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.charts;

import java.awt.*;
import java.io.*;
import java.util.*;
import org.jfree.chart.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmdata.widgets.*;

public class JFreeChartImage {
	private JFreeChart chart;
	public JFreeChartImage chart(JFreeChart chart) {
		this.chart = chart;
		return this;
	}
	/*
	 * The actual size (800x600) is not that important in SVG, because it is scalable,
	 * but we choose width equal to paragraph width (800px),
	 * because this is the typical and almost always minimal size for charts,
	 * so rendering at this size ensures readability in all chart sizes.
	 * Height is chosen to be 600, because most charts are designed to look good in 4:3 aspect ratio.
	 */
	private int width = 800;
	private int height = 600;
	public JFreeChartImage size(int width, int height) {
		this.width = width;
		this.height = height;
		return this;
	}
	public byte[] svg() {
		Objects.requireNonNull(chart);
		/*
		 * The Rectangle parameter here determines how big the SVG shapes will be,
		 * but it does not automatically generate width/height nor viewBox attributes.
		 */
		return SwingSvg.toSvg(width, height, g -> chart.draw(g, new Rectangle(width, height)));
	}
	/*
	 * While SVG is usually preferable, sometimes we might be interested in a PNG image,
	 * perhaps because SVG image doesn't always render well or it is too large or slow.
	 */
	public byte[] png() {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Exceptions.sneak().run(() -> ChartUtils.writeChartAsPNG(buffer, chart, width, height));
		return buffer.toByteArray();
	}
	/*
	 * Contrary to ImageViewer, we default to 100% size (800px), because charts rarely contain enough detail to warrant larger image size.
	 */
	private int scale = 100;
	public JFreeChartImage scale(int scale) {
		this.scale = scale;
		return this;
	}
	/*
	 * By default, we will always use SVG format even if we know image dimensions.
	 * This ensures correct rendering on high DPI displays and scaling on small screens.
	 */
	public void view(boolean rasterize) {
		ImageViewer.view(scale, rasterize ? png() : svg());
	}
	public void view() {
		view(false);
	}
}
