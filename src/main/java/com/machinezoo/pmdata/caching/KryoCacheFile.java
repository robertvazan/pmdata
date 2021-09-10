// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;

record KryoCacheFile(KryoCache<?> definition) implements BinaryCache {
	@Override
	public CachePolicy caching() {
		return definition.caching();
	}
	@Override
	public void link() {
		definition.link();
	}
	@Override
	public void compute(Path path) {
		var value = definition.compute();
		Exceptions.sneak().run(() -> {
			try (var stream = Files.newOutputStream(path)) {
				ThreadLocalKryo.get().writeClassAndObject(new Output(stream, 256), value);
			}
		});
	}
}
