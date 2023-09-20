// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import com.machinezoo.hookless.*;
import com.machinezoo.noexception.*;

public interface BinaryCache extends PersistentSource<byte[]> {
	void link();
	void compute(Path path);
	default int version() {
		return 0;
	}
	default CachingOptions caching() {
		return CachingOptions.DEFAULT;
	}
	/*
	 * If this cache is a generic wrapper around higher level cache definition (e.g. KryoCache),
	 * this method provides access to that high-level definition. Used for visualization and file naming.
	 */
	default Object unwrap() {
		return this;
	}
	/*
	 * This has side effects like get() but without returning cached value or throwing.
	 * It is intended to be used in link(). It might be faster than file().
	 */
	default void touch() {
		CacheInput.get().snapshot(this);
	}
	default Path path() {
		var snapshot = CacheInput.get().snapshot(this);
		if (snapshot == null) {
			if (caching().blocking())
				CurrentReactiveScope.block();
			throw new EmptyCacheException();
		}
		return snapshot.get();
	}
	@Override
	default byte[] get() {
		return Exceptions.wrap().get(() -> Files.readAllBytes(path()));
	}
	default InputStream stream() {
		return Exceptions.wrap().get(() -> Files.newInputStream(path()));
	}
	default MappedByteBuffer mmap() {
		return BinaryCacheMapping.of(path()).open();
	}
}
