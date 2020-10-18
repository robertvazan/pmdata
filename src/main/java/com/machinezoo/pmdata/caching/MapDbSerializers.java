// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.util.*;
import java.util.function.*;
import org.mapdb.*;
import com.esotericsoftware.kryo.io.*;
import com.machinezoo.noexception.*;

public class MapDbSerializers {
	public static class KryoSerializer<T> implements Serializer<T> {
		private final Class<T> clazz;
		private final boolean isFinal;
		private final boolean isComparable;
		private final boolean hasEquals;
		private final boolean hasHashCode;
		public KryoSerializer(Class<T> clazz) {
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
		public KryoSerializer() {
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
	private static class Registration<T> {
		Class<T> clazz;
		Function<Class<T>, Serializer<T>> mapping;
	}
	private static final List<Registration<?>> registrations = new ArrayList<>();
	public static synchronized <T> void register(Class<T> clazz, Function<Class<T>, Serializer<T>> mapping) {
		var registration = new Registration<T>();
		registration.clazz = clazz;
		registration.mapping = mapping;
		for (int i = 0; i < registrations.size(); ++i) {
			var item = registrations.get(i);
			if (clazz.isAssignableFrom(item.clazz)) {
				if (clazz != item.clazz)
					registrations.add(i, registration);
				else
					registrations.set(i, registration);
			}
		}
		registrations.add(registration);
	}
	public static <T> void register(Class<T> clazz, Serializer<T> serializer) {
		register(clazz, c -> serializer);
	}
	static {
		register(String.class, Serializer.STRING);
		register(byte[].class, Serializer.BYTE_ARRAY_NOSIZE);
		register(Boolean.class, Serializer.BOOLEAN);
		register(Integer.class, Serializer.INTEGER);
		register(Long.class, Serializer.LONG);
		register(Short.class, Serializer.SHORT);
		register(Byte.class, Serializer.BYTE);
		register(Double.class, Serializer.DOUBLE);
		register(Float.class, Serializer.FLOAT);
		register(int[].class, Serializer.INT_ARRAY);
		register(long[].class, Serializer.LONG_ARRAY);
		register(short[].class, Serializer.SHORT_ARRAY);
		register(double[].class, Serializer.DOUBLE_ARRAY);
		register(float[].class, Serializer.FLOAT_ARRAY);
		register(BigInteger.class, Serializer.BIG_INTEGER);
		register(BigDecimal.class, Serializer.BIG_DECIMAL);
		register(Character.class, Serializer.CHAR);
		register(char[].class, Serializer.CHAR_ARRAY);
		register(UUID.class, Serializer.UUID);
		register(Date.class, Serializer.DATE);
	}
	@SuppressWarnings("unchecked")
	public static synchronized <T> Serializer<T> get(Class<T> clazz) {
		if (clazz == null)
			return new KryoSerializer<>();
		for (int i = registrations.size() - 1; i >= 0; --i) {
			var registration = registrations.get(i);
			if (registration.clazz.isAssignableFrom(clazz))
				return ((Registration<T>)registration).mapping.apply(clazz);
		}
		return new KryoSerializer<>(clazz);
	}
}
