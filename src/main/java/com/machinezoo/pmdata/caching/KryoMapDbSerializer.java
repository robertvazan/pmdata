// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.mapdb.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;

public class KryoMapDbSerializer<T> implements Serializer<T> {
	private final Class<T> clazz;
	private final boolean isFinal;
	private final boolean isComparable;
	private final boolean hasEquals;
	private final boolean hasHashCode;
	public KryoMapDbSerializer(Class<T> clazz) {
		this.clazz = clazz;
		if (clazz != null) {
			isFinal = Modifier.isFinal(clazz.getModifiers());
			isComparable = Comparable.class.isAssignableFrom(clazz);
			/*
			 * Here we assume that every equatable class implements equals/hashCode directly rather than inheriting them.
			 */
			var equals = Exceptions.silence().get(Exceptions.sneak().supplier(() -> clazz.getMethod("equals", Object.class))).orElse(null);
			hasEquals = equals != null && equals.getDeclaringClass() == clazz && clazz != Object.class;
			var hashCode = Exceptions.silence().get(Exceptions.sneak().supplier(() -> clazz.getMethod("hashCode"))).orElse(null);
			hasHashCode = hashCode != null && hashCode.getDeclaringClass() == clazz && clazz != Object.class;
		} else {
			isFinal = false;
			isComparable = false;
			hasEquals = false;
			hasHashCode = false;
		}
	}
	public KryoMapDbSerializer() {
		this(null);
	}
	private byte[] encode(Object object) {
		Output output = new Output(64, -1);
		/*
		 * If the class is final, we will omit serialization of class name.
		 * We however wouldn't allow serialization of nulls (writeObject vs. writeObjectOrNull),
		 * because null does not make any sense as a key and null values are better represented as absent values.
		 * Predefined serializers don't allow nulls either.
		 * User can force null serializability by instantiating the serializer for Object.class.
		 */
		if (isFinal)
			ThreadLocalKryo.get().writeObject(output, object);
		else
			ThreadLocalKryo.get().writeClassAndObject(output, object);
		return output.toBytes();
	}
	private Object decode(byte[] bytes) {
		Input input = new Input(bytes);
		if (isFinal)
			return ThreadLocalKryo.get().readObject(input, clazz);
		else
			return ThreadLocalKryo.get().readClassAndObject(input);
	}
	@Override
	public void serialize(DataOutput2 output, T value) throws IOException {
		var bytes = encode(value);
		output.writeInt(bytes.length);
		output.write(bytes);
	}
	@SuppressWarnings("unchecked")
	@Override
	public T deserialize(DataInput2 input, int available) throws IOException {
		var bytes = new byte[input.readInt()];
		input.readFully(bytes);
		return (T)decode(bytes);
	}
	@Override
	public boolean isTrusted() {
		/*
		 * We do our own buffer copying, so we will never read past the stored value.
		 */
		return true;
	}
	@Override
	public boolean equals(T first, T second) {
		if (hasEquals)
			return Objects.equals(first, second);
		else {
			if (first == null && second == null)
				return true;
			if (first == null || second == null)
				return false;
			return Arrays.equals(encode(first), encode(second));
		}
	}
	@Override
	public int hashCode(T object, int seed) {
		if (hasHashCode)
			return object.hashCode();
		else
			return DataIO.intHash(Arrays.hashCode(encode(object)) + seed);
	}
	@SuppressWarnings("unchecked")
	@Override
	public int compare(T first, T second) {
		if (isComparable)
			return ((Comparable<T>)first).compareTo(second);
		else
			return Arrays.compare(encode(first), encode(second));
	}
}