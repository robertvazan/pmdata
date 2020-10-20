// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;

public interface CacheFormat<T extends CacheFile> {
	T load(Path path);
}
