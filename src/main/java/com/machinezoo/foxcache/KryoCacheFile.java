// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import java.nio.file.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;

record KryoCacheFile(KryoCache<?> definition) implements BinaryCache {
	@Override
	public CachingOptions caching() {
		return definition.caching();
	}
	@Override
	public Object unwrap() {
		return definition;
	}
	@Override
	public int version() {
		return definition.version();
	}
	@Override
	public void link() {
		definition.link();
	}
	@Override
	public void compute(Path path) {
		var value = definition.compute();
		Exceptions.sneak().run(() -> {
			try (	var stream = Files.newOutputStream(path);
					var output = new Output(stream)) {
				ThreadLocalKryo.get().writeClassAndObject(output, value);
			}
		});
	}
}
