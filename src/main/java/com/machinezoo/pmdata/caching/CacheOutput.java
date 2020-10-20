// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import static java.util.stream.Collectors.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.slf4j.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmsite.utils.*;

public class CacheOutput {
	private static final Logger logger = LoggerFactory.getLogger(CacheState.class);
	public static final Path DEFAULT = SiteFiles.cacheOf(CacheOutput.class.getSimpleName());
	private static void clear(Path directory) {
		try {
			try (var listing = Files.list(directory)) {
				for (var item : listing.collect(toList())) {
					if (Files.isRegularFile(item))
						Files.delete(item);
					else if (Files.isDirectory(item)) {
						clear(item);
						Files.delete(item);
					}
				}
			}
		} catch (Throwable ex) {
			logger.warn("Unable to remove stale cache data in {}.", directory, ex);
		}
	}
	static {
		clear(DEFAULT);
	}
	private static final ThreadLocal<Path> current = new ThreadLocal<>();
	public static CloseableScope advertise(Path directory) {
		Exceptions.wrap().run(() -> Files.createDirectories(directory));
		var outer = current.get();
		current.set(directory);
		return () -> current.set(outer);
	}
	public static Path directory() {
		return Optional.ofNullable(current.get()).orElse(DEFAULT);
	}
	private static final SecureRandom random = new SecureRandom();
	public static Path random() {
		var bytes = new byte[32];
		random.nextBytes(bytes);
		return directory().resolve(Base64.getUrlEncoder().encodeToString(bytes).replace("=", ""));
	}
}
