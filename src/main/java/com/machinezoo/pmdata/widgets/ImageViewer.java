// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.widgets;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.*;
import javax.imageio.plugins.jpeg.*;
import javax.imageio.stream.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
public class ImageViewer {
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
	private String title;
	public ImageViewer title(String title) {
		this.title = title;
		return this;
	}
	private byte[] image;
	public ImageViewer image(byte[] image) {
		this.image = image;
		return this;
	}
	/*
	 * We have no way to determine reasonable maximum image width and we cannot ask the user as we don't know the display context.
	 * If the code that creates the image knows what size is appropriate, it should specify it.
	 * Otherwise we default to 100%, which looks consistent across display sizes.
	 * 
	 * Our CSS really supports only a few scale factors while we allow setting arbitrary integer factor here.
	 * Exposing specialized methods like scale125() would make the score hard to use when scaling is dynamically determined.
	 * Enum would be more type-safe, but it would be also more verbose and it would complicate dynamic size calculations.
	 */
	private int scale = 100;
	public ImageViewer scale(int scale) {
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
		try (var figure = Figure.define(title)) {
			SiteFragment.get()
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
	/*
	 * Images are common and all parameters may be used often, so provide overloads for all combinations.
	 */
	public static void view(byte[] image) {
		new ImageViewer().image(image).render();
	}
	public static void view(int scale, byte[] image) {
		new ImageViewer().scale(scale).image(image).render();
	}
	public static void view(String title, byte[] image) {
		new ImageViewer().title(title).image(image).render();
	}
	public static void view(String title, int scale, byte[] image) {
		new ImageViewer().title(title).scale(scale).image(image).render();
	}
}