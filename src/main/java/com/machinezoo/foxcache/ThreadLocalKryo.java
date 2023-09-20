// Part of Fox Cache: https://foxcache.machinezoo.com
package com.machinezoo.foxcache;

import java.util.*;
import java.util.function.*;
import org.objenesis.strategy.*;
import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.util.*;

/*
 * Kryo is not thread-safe. We will give every thread its own instance.
 * We don't want to recreate the instance during every serialization, because kryo caches a lot of type information.
 */
public class ThreadLocalKryo {
	/*
	 * Make the serializers globally configurable. In the future, we might want to use the ServiceLoader API.
	 */
	private static final List<Consumer<Kryo>> serializers = new ArrayList<>();
	private static volatile long version;
	public static synchronized void register(Consumer<Kryo> serializer) {
		serializers.add(serializer);
		++version;
	}
	public static void register(Class<?> type, Class<? extends Serializer<?>> serializer) {
		register(kryo -> kryo.addDefaultSerializer(type, serializer));
	}
	private static class VersionedKryo {
		long version;
		Kryo kryo;
	}
	private static final ThreadLocal<VersionedKryo> kryos = new ThreadLocal<>();
	public static Kryo get() {
		var versioned = kryos.get();
		if (versioned == null || versioned.version < version) {
			versioned = new VersionedKryo();
			versioned.version = version;
			versioned.kryo = new Kryo();
			/*
			 * Kryo requires class registration by default for security reasons, which are irrelevant for local cache.
			 */
			versioned.kryo.setRegistrationRequired(false);
			versioned.kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
			synchronized (ThreadLocalKryo.class) {
				/*
				 * Use addDefaultSerializer() instead of register(), because register() would assign some random class ID
				 * and then rely on that class ID during deserialization. That's too unreliable.
				 * We will force kryo to serialize full class names instead.
				 */
				for (var serializer : serializers)
					serializer.accept(versioned.kryo);
			}
			kryos.set(versioned);
		}
		return versioned.kryo;
	}
}
