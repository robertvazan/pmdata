// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.*;
import com.machinezoo.noexception.*;

class BinaryCacheMapping {
	private final Path path;
	BinaryCacheMapping(Path path) {
		this.path = path;
	}
	private FileChannel channel;
	private MappedByteBuffer buffer;
	synchronized MappedByteBuffer open() {
		long size = Exceptions.wrap().get(() -> Files.size(path));
		if (size > Integer.MAX_VALUE)
			throw new IllegalStateException("Cannot mmap files larger than 2GB.");
		if (channel == null)
			channel = Exceptions.wrap().get(() -> FileChannel.open(path));
		if (buffer == null)
			buffer = Exceptions.wrap().get(() -> channel.map(FileChannel.MapMode.READ_ONLY, 0, size));
		return buffer;
	}
	private static final ConcurrentHashMap<Path, BinaryCacheMapping> all = new ConcurrentHashMap<>();
	static BinaryCacheMapping of(Path path) {
		return all.computeIfAbsent(path, BinaryCacheMapping::new);
	}
}
