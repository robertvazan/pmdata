// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import static java.util.stream.Collectors.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;
import org.apache.commons.io.*;
import com.machinezoo.noexception.*;

public class ZipCache {
	private static final Map<Path, ZipFile> all = new HashMap<>();
	private static synchronized ZipFile lookup(Path path) {
		return all.computeIfAbsent(path, p -> Exceptions.wrap().get(() -> new ZipFile(p.toFile())));
	}
	private final Supplier<Path> supplier;
	private ZipCache(Supplier<Path> supplier) {
		this.supplier = supplier;
	}
	public static ZipCache of(Path path) {
		return new ZipCache(() -> path);
	}
	public static ZipCache of(BinaryFile file) {
		return new ZipCache(() -> file.path());
	}
	public static ZipCache of(CacheState<BinaryFile> cache) {
		return new ZipCache(() -> cache.get().path());
	}
	public ZipFile zip() {
		return lookup(supplier.get());
	}
	public List<String> list() {
		return zip().stream().map(ZipEntry::getName).sorted().collect(toList());
	}
	public byte[] read(String path) {
		var zip = zip();
		var entry = zip.getEntry(path);
		if (entry == null)
			throw new NoSuchElementException();
		return Exceptions.wrap().get(() -> {
			try (var stream = zip.getInputStream(entry)) {
				return IOUtils.toByteArray(stream);
			}
		});
	}
}
