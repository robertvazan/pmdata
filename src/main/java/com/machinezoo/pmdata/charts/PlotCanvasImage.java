// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.charts;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;
import javax.swing.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmdata.widgets.*;
import smile.plot.*;

public class PlotCanvasImage {
	private PlotCanvas plot;
	public PlotCanvasImage plot(PlotCanvas plot) {
		this.plot = plot;
		return this;
	}
	private int width = 800;
	private int height = 600;
	public PlotCanvasImage size(int width, int height) {
		this.width = width;
		this.height = height;
		return this;
	}
	public byte[] svg() {
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
		return SwingSvg.toSvg(width, height, g -> plot.paint(g));
	}
	public byte[] png() {
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
	private int scale = 100;
	public PlotCanvasImage scale(int scale) {
		this.scale = scale;
		return this;
	}
	public void view() {
		ImageViewer.view(scale, svg());
	}
}
