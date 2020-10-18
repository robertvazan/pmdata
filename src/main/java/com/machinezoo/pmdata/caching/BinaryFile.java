// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import com.machinezoo.noexception.*;

public class BinaryFile implements CacheData {
	private final Path path;
	@Override
	public Path path() {
		return path;
	}
	private boolean readonly;
	@Override
	public synchronized boolean readonly() {
		return readonly;
	}
	private BinaryFile(Path path, boolean readonly) {
		this.path = path;
		this.readonly = readonly;
	}
	public BinaryFile(Path path) {
		this.path = path;
		Exceptions.wrap().run(() -> Files.createDirectories(path.getParent()));
	}
	public BinaryFile() {
		this(CacheOutput.random());
	}
	public static CacheFormat<BinaryFile> format() {
		return new CacheFormat<>() {
			@Override
			public BinaryFile load(Path path) {
				return new BinaryFile(path, true);
			}
		};
	}
	public synchronized OutputStream writeStream() {
		if (readonly)
			throw new IllegalStateException();
		return Exceptions.wrap().get(() -> Files.newOutputStream(path));
	}
	public synchronized FileChannel writeChannel() {
		if (readonly)
			throw new IllegalStateException();
		return Exceptions.wrap().get(() -> FileChannel.open(path));
	}
	public synchronized BinaryFile write(byte[] bytes) {
		if (readonly)
			throw new IllegalStateException();
		Exceptions.wrap().run(() -> Files.write(path, bytes));
		return this;
	}
	public synchronized BinaryFile write(ByteBuffer buffer) {
		Exceptions.wrap().run(() -> {
			try (var channel = writeChannel()) {
				channel.write(buffer);
			}
		});
		return this;
	}
	@Override
	public synchronized void commit() {
		if (!readonly) {
			if (!Files.isRegularFile(path))
				throw new IllegalStateException("The file must be created first.");
			readonly = true;
		}
	}
	public synchronized byte[] read() {
		if (!Files.exists(path))
			return null;
		return Exceptions.wrap().get(() -> Files.readAllBytes(path));
	}
	public String text() {
		return new String(read(), StandardCharsets.UTF_8);
	}
	public synchronized InputStream stream() {
		return Exceptions.wrap().get(() -> Files.newInputStream(path));
	}
	private long size = -1;
	public synchronized long size() {
		if (readonly) {
			if (size < 0)
				size = Exceptions.wrap().get(() -> Files.size(path));
			return size;
		} else
			return Exceptions.wrap().get(() -> Files.size(path));
	}
	private FileChannel mmapChannel;
	private MappedByteBuffer mmapBuffer;
	public synchronized MappedByteBuffer mmap() {
		if (!readonly)
			throw new IllegalStateException();
		if (size() > Integer.MAX_VALUE)
			throw new IllegalStateException("Cannot mmap files larger than 2GB.");
		if (mmapChannel == null)
			mmapChannel = Exceptions.wrap().get(() -> FileChannel.open(path));
		if (mmapBuffer == null)
			mmapBuffer = Exceptions.wrap().get(() -> mmapChannel.map(FileChannel.MapMode.READ_ONLY, 0, size()));
		return mmapBuffer;
	}
}
