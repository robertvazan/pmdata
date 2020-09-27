// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.cache;

import java.nio.file.*;

public interface CacheFormat<T extends CacheData> {
	T load(Path path);
}
