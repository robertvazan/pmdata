// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.net.*;
import org.apache.commons.io.*;
import com.machinezoo.noexception.*;
import com.machinezoo.stagean.*;

@DraftCode("could use 304 Not Modified optimization (in conjunction with PersistentCache.update())")
public abstract class DownloadCache extends BinaryCache {
	public abstract URI uri();
	@Override
	public BinaryFile supply() {
		/*
		 * Streamed to allow download of large files.
		 */
		var file = new BinaryFile();
		Exceptions.wrap().run(() -> {
			try (	var output = file.writeStream();
					var input = uri().toURL().openStream()) {
				IOUtils.copy(input, output);
			}
		});
		return file;
	}
}
