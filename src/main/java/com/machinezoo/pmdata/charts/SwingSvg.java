// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.charts;

import java.awt.*;
import java.io.*;
import java.util.function.*;
import org.apache.batik.dom.*;
import org.apache.batik.svggen.*;
import org.w3c.dom.*;
import com.machinezoo.noexception.*;

class SwingSvg {
	static byte[] toSvg(int width, int height, Consumer<Graphics2D> painter) {
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
}
