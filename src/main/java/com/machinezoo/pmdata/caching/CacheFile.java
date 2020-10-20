// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;

public interface CacheFile {
	Path path();
	void commit();
	boolean readonly();
}
