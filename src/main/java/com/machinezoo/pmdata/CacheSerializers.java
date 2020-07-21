// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import org.apache.commons.lang3.tuple.*;
import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.stagean.*;

@DraftApi
public class CacheSerializers {
	public static class PairSerializer extends Serializer<Pair<?, ?>> {
		public PairSerializer() {
			setImmutable(true);
		}
		@Override
		public void write(Kryo kryo, Output output, Pair<?, ?> object) {
			kryo.writeClassAndObject(output, object.getKey());
			kryo.writeClassAndObject(output, object.getValue());
		}
		@Override
		public Pair<?, ?> read(Kryo kryo, Input input, Class<Pair<?, ?>> type) {
			return Pair.of(kryo.readClassAndObject(input), kryo.readClassAndObject(input));
		}
	}
}
