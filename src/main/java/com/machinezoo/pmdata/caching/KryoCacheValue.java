// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;

record KryoCacheValue(KryoCache<?> definition) implements ComputeCache<Object> {
	@Override
	public Object compute() {
		return Exceptions.sneak().get(() -> {
			try (var stream = new KryoCacheFile(definition).stream()) {
				return ThreadLocalKryo.get().readClassAndObject(new Input(stream));
			}
		});
	}
}
