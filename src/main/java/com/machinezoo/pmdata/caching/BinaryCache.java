// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.io.*;
import java.nio.*;

public abstract class BinaryCache extends PersistentCache<BinaryFile> {
	@Override
	public CacheFormat<BinaryFile> format() {
		return BinaryFile.format();
	}
	public BinaryFile get() {
		return file();
	}
	public byte[] read() {
		return get().read();
	}
	public String text() {
		return get().text();
	}
	public InputStream stream() {
		return get().stream();
	}
	public MappedByteBuffer mmap() {
		return get().mmap();
	}
}
