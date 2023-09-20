// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import java.net.*;
import java.nio.file.*;
import org.apache.commons.io.*;
import com.machinezoo.noexception.*;
import com.machinezoo.stagean.*;

@DraftCode("could use 304 Not Modified optimization")
public interface DownloadCache extends BinaryCache {
	URI uri();
	@Override
	default void compute(Path path) {
		/*
		 * Streamed to allow download of large files.
		 */
		Exceptions.wrap().run(() -> {
			try (	var output = Files.newOutputStream(path);
					var input = uri().toURL().openStream()) {
				IOUtils.copy(input, output);
			}
		});
	}
}
