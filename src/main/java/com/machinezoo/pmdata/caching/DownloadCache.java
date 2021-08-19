// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.net.*;
import org.apache.commons.io.*;
import com.machinezoo.noexception.*;
import com.machinezoo.stagean.*;

@DraftCode("could use 304 Not Modified optimization (in conjunction with PersistentCache.update())")
public interface DownloadCache extends BinaryCache {
	URI uri();
	@Override
	default BinaryFile computeCache() {
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
