// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.cache;

import java.net.*;
import java.time.*;
import java.util.*;
import org.apache.commons.io.*;
import com.machinezoo.noexception.*;
import com.machinezoo.stagean.*;

@DraftCode("could use 304 Not Modified optimization (in conjunction with PersistentCache.update())")
public class DownloadCache {
	/*
	 * Streamed to allow download of large files.
	 */
	public static BinaryFile fetch(URI uri) {
		var file = new BinaryFile();
		Exceptions.wrap().run(() -> {
			try (	var output = file.writeStream();
					var input = uri.toURL().openStream()) {
				IOUtils.copy(input, output);
			}
		});
		return file;
	}
	/*
	 * We can either create new cache that can be customized by the caller
	 * or we can return per-URI unique cache instance that is fully configured.
	 */
	public static PersistentCache<BinaryFile> create(URI uri) {
		return new PersistentCache<>(BinaryFile.format())
			.id(DownloadCache.class)
			.parameter("uri", uri)
			.link(() -> {
			})
			.supply(() -> fetch(uri));
	}
	private static final Map<URI, PersistentCache<BinaryFile>> caches = new HashMap<>();
	public static synchronized PersistentCache<BinaryFile> of(URI uri, Duration period) {
		return caches.computeIfAbsent(uri, u -> create(u).period(period).define());
	}
	/*
	 * Without refresh interval, we will just cache the file forever.
	 * In the future, we might want to respect the cache headers in HTTP response.
	 */
	public static PersistentCache<BinaryFile> of(URI uri) {
		return caches.computeIfAbsent(uri, u -> create(u).define());
	}
}
