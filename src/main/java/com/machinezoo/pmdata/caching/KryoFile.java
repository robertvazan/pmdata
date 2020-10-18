// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;
import java.util.concurrent.*;
import com.esotericsoftware.kryo.io.*;

public class KryoFile<T> implements CacheData {
	private final BinaryFile binary;
	private KryoFile(BinaryFile binary) {
		this.binary = binary;
	}
	public KryoFile(Path path) {
		binary = new BinaryFile(path);
	}
	public KryoFile() {
		binary = new BinaryFile();
	}
	public static <T> CacheFormat<KryoFile<T>> format() {
		return new CacheFormat<>() {
			@Override
			public KryoFile<T> load(Path path) {
				return new KryoFile<>(BinaryFile.format().load(path));
			}
		};
	}
	@Override
	public Path path() {
		return binary.path();
	}
	@Override
	public void commit() {
		binary.commit();
	}
	@Override
	public boolean readonly() {
		return binary.readonly();
	}
	public void write(T value) {
		var output = new Output(256, -1);
		ThreadLocalKryo.get().writeClassAndObject(output, value);
		binary.write(output.toBytes());
	}
	@SuppressWarnings("unchecked")
	private T reread() {
		var bytes = binary.read();
		if (bytes == null)
			return null;
		var input = new Input(bytes);
		return (T)ThreadLocalKryo.get().readClassAndObject(input);
	}
	private static class ParsedContent<T> implements ComputeCacheQuery<T> {
		final KryoFile<T> file;
		ParsedContent(KryoFile<T> file) {
			this.file = file;
		}
		@Override
		public T compute() {
			return file.reread();
		}
		@Override
		public boolean equals(Object obj) {
			return obj instanceof ParsedContent && ((ParsedContent<?>)obj).file == file;
		}
		@Override
		public int hashCode() {
			return file.hashCode();
		}
	}
	private final ParsedContent<T> parsed = new ParsedContent<T>(this);
	public T read() {
		/*
		 * Cache parsing after the file is committed.
		 */
		if (binary.readonly())
			return parsed.get();
		else
			return reread();
	}
	/*
	 * Optimization. Default hashCode() is slow.
	 */
	private final int hashCode = ThreadLocalRandom.current().nextInt();
	@Override
	public int hashCode() {
		return hashCode;
	}
	public static <T> KryoFile<T> of(T value) {
		var file = new KryoFile<T>();
		file.write(value);
		return file;
	}
}
