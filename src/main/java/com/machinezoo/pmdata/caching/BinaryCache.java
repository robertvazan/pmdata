// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.io.*;
import java.nio.*;

public interface BinaryCache extends PersistentCache<BinaryFile> {
	@Override
	default CacheFormat<BinaryFile> cacheFormat() {
		return BinaryFile.format();
	}
	default BinaryFile get() {
		return getCache();
	}
	default byte[] read() {
		return get().read();
	}
	default String text() {
		return get().text();
	}
	default InputStream stream() {
		return get().stream();
	}
	default MappedByteBuffer mmap() {
		return get().mmap();
	}
}
