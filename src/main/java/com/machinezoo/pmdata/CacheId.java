// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.util.*;
import com.machinezoo.stagean.*;

/*
 * Cache paths are occasionally dynamically constructed. That's easier to do when we have a class for it.
 * This class is designed to accommodate varying number of path components while remaining fast to construct and compare.
 */
@DraftApi
public class CacheId {
	/*
	 * We have special handling for classes, because they are often used to identify caches.
	 * We also want to allow supplying various identity objects without explicit stringification.
	 */
	private static String stringify(Object object) {
		if (object instanceof Class)
			return ((Class<?>)object).getSimpleName();
		return object.toString();
	}
	/*
	 * We want to preserve the individual parts, so that we can perform operations on cache subtrees.
	 * It is also faster to avoid serialization into single string most of the time.
	 */
	public final String[] parts;
	public CacheId(Object... path) {
		parts = new String[path.length];
		for (int i = 0; i < parts.length; ++i)
			parts[i] = stringify(path[i]);
	}
	/*
	 * String-only overload is mostly useful for deserialization.
	 */
	public CacheId(String... path) {
		parts = Arrays.copyOf(path, path.length);
	}
	/*
	 * We will allow paths with zero components and even offer precreated one as public field.
	 * This is useful when some caches are dynamically created within context but also without any context.
	 * Context is supplied as parent cache path. When there is no context, root path is provided.
	 */
	public static final CacheId ROOT = new CacheId(new String[0]);
	/*
	 * Convenient nesting is key to usability of the class.
	 */
	private CacheId(String[] parent, Object[] subpath) {
		parts = new String[parent.length + subpath.length];
		for (int i = 0; i < parent.length; ++i)
			parts[i] = parent[i];
		for (int i = 0; i < subpath.length; ++i)
			parts[parent.length + i] = stringify(subpath[i]);
	}
	public CacheId then(Object... subpath) {
		return new CacheId(parts, subpath);
	}
	/*
	 * It is common to extend class' main path with just single string (cache name), so provide faster overload for that case.
	 */
	private CacheId(String[] parent, Object child) {
		parts = new String[parent.length + 1];
		for (int i = 0; i < parent.length; ++i)
			parts[i] = parent[i];
		parts[parent.length] = stringify(child);
	}
	public CacheId then(Object child) {
		return new CacheId(parts, child);
	}
	@Override
	public boolean equals(Object obj) {
		return obj instanceof CacheId && Arrays.equals(parts, ((CacheId)obj).parts);
	}
	@Override
	public int hashCode() {
		int code = 0;
		for (var part : parts)
			code = 31 * code + part.hashCode();
		return code;
	}
	@Override
	public String toString() {
		return String.join(" / ", parts);
	}
}
