// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import static java.util.stream.Collectors.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import com.google.common.hash.*;
import com.machinezoo.closeablescope.CloseableScope;
import com.machinezoo.noexception.*;
import com.machinezoo.noexception.slf4j.*;
import com.machinezoo.pmsite.utils.*;
import com.machinezoo.stagean.*;

public class CacheFiles {
	@DraftCode("support data directories besides data files")
	public static String hash(Path path) {
		if (Files.isRegularFile(path)) {
			return Exceptions.wrap().get(() -> {
				var hasher = MessageDigest.getInstance("SHA-256");
				try (var stream = Files.newInputStream(path)) {
					byte[] buffer = new byte[4096];
					while (true) {
						int amount = stream.read(buffer);
						if (amount <= 0)
							break;
						hasher.update(buffer, 0, amount);
					}
				}
				return Base64.getMimeEncoder().encodeToString(hasher.digest());
			});
		} else
			throw new IllegalStateException("Cannot find persistent data file.");
	}
	@DraftCode("support data directories besides data files")
	public static long size(Path path) {
		if (Files.isRegularFile(path))
			return Exceptions.wrap().getAsLong(() -> Files.size(path));
		else
			throw new IllegalStateException("Cannot find persistent data file.");
	}
	public static void remove(Path path) {
		Exceptions.wrap().run(() -> {
			if (Files.isDirectory(path)) {
				try (var listing = Files.list(path)) {
					for (var item : listing.collect(toList()))
						remove(item);
				}
			}
			if (Files.exists(path))
				Files.delete(path);
		});
	}
	private static String hashId(String text) {
		var hash = Hashing.sha256().hashString(text, StandardCharsets.UTF_8).asBytes();
		return Base64.getUrlEncoder().encodeToString(hash).replace("=", "");
	}
	public static Path directory(BinaryCache cache) {
		var definition = cache.unwrap();
		return SiteFiles.cacheOf(CacheFiles.class.getSimpleName())
			.resolve(definition.getClass().getSimpleName())
			.resolve(hashId(definition.toString()));
	}
	public static final Path DEFAULT_DESTINATION = SiteFiles.cacheOf(CacheFiles.class.getSimpleName()).resolve("default");
	static {
		/*
		 * Cache files in default directory are transient. Delete them when app restarts.
		 */
		ExceptionLogging.log().run(() -> remove(DEFAULT_DESTINATION));
	}
	private static final ThreadLocal<Path> current = new ThreadLocal<>();
	public static Path destination() {
		return Optional.ofNullable(current.get()).orElse(DEFAULT_DESTINATION);
	}
	public static CloseableScope redirect(Path directory) {
		Exceptions.wrap().run(() -> Files.createDirectories(directory));
		var outer = current.get();
		current.set(directory);
		return () -> current.set(outer);
	}
	private static final SecureRandom random = new SecureRandom();
	public static Path next() {
		var bytes = new byte[32];
		random.nextBytes(bytes);
		return destination().resolve(Base64.getUrlEncoder().encodeToString(bytes).replace("=", ""));
	}
}
