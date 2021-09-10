// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;
import com.ning.compress.lzf.util.*;

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
	public void link() {
		definition.link();
	}
	@Override
	public void compute(Path path) {
		var value = definition.compute();
		Exceptions.sneak().run(() -> {
			try (var stream = new LZFFileOutputStream(path.toFile())) {
				ThreadLocalKryo.get().writeClassAndObject(new Output(stream), value);
			}
		});
	}
}
