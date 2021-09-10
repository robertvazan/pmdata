// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;
import com.ning.compress.lzf.util.*;

record KryoCacheValue(Path path) implements ComputeCache<Object> {
	@Override
	public Object compute() {
		return Exceptions.sneak().get(() -> {
			try (var stream = new LZFFileInputStream(path.toFile())) {
				return ThreadLocalKryo.get().readClassAndObject(new Input(stream));
			}
		});
	}
}
