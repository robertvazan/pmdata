// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.charts;

import java.io.*;
import java.util.*;
import javax.imageio.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmdata.widgets.*;
import smile.plot.swing.*;

public class SmileCanvasImage {
	private final Canvas canvas;
	public SmileCanvasImage(Canvas canvas) {
		Objects.requireNonNull(canvas);
		this.canvas = canvas;
	}
	public SmileCanvasImage(Plot plot) {
		this(plot.canvas());
	}
	private int width = 800;
	private int height = 600;
	public SmileCanvasImage size(int width, int height) {
		this.width = width;
		this.height = height;
		return this;
	}
	public byte[] svg() {
		return SwingSvg.toSvg(width, height, g -> canvas.paint(g, width, height));
	}
	public byte[] png() {
		var image = canvas.toBufferedImage(width, height);
		var buffer = new ByteArrayOutputStream();
		Exceptions.sneak().run(() -> ImageIO.write(image, "PNG", buffer));
		return buffer.toByteArray();
	}
	private int scale = 100;
	public SmileCanvasImage scale(int scale) {
		this.scale = scale;
		return this;
	}
	public void view() {
		ImageViewer.view(scale, svg());
	}
}
