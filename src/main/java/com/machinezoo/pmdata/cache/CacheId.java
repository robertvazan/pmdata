// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.cache;

import java.nio.charset.*;
import java.security.*;
import java.util.*;
import com.machinezoo.noexception.*;
import one.util.streamex.*;

/*
 * Immutable. All methods create new instances.
 */
public class CacheId implements Cloneable, Comparable<CacheId> {
	private final List<String> path;
	public List<String> path() {
		return path;
	}
	private final Map<String, String> parameters;
	public Map<String, String> parameters() {
		return parameters;
	}
	private CacheId() {
		path = Collections.emptyList();
		parameters = Collections.emptyMap();
	}
	public static final CacheId ROOT = new CacheId();
	private void validatePath(String... path) {
		for (var component : path)
			if (component.isBlank())
				throw new IllegalArgumentException();
	}
	public CacheId(String... path) {
		validatePath(path);
		this.path = StreamEx.of(path).toImmutableList();
		parameters = Collections.emptyMap();
	}
	/*
	 * Cache ID will include the package. This should be made relative to SiteConfiguration for compact display.
	 */
	public CacheId(Class<?> clazz, String... relative) {
		validatePath(relative);
		path = StreamEx.of(clazz.getName().split("\\.")).append(relative).toImmutableList();
		parameters = Collections.emptyMap();
	}
	private CacheId(List<String> path, Map<String, String> parameters) {
		this.path = path;
		this.parameters = parameters;
	}
	public CacheId nest(String... relative) {
		validatePath(relative);
		return new CacheId(StreamEx.of(path).append(relative).toImmutableList(), parameters);
	}
	private CacheId parameter(String name, String value) {
		return new CacheId(path, EntryStream.of(parameters).append(name, value).toImmutableMap());
	}
	/*
	 * Many parameters will be complex data source identifiers implemented as a hierarchy of lightweight objects.
	 * We cannot just serialize them, because UI requires human-readable parameter values.
	 * We will require all parameters passed here to have working, reproducible toString() implementation.
	 * The easiest way to accomplish that is to use lombok's @ToString. Alternatively, there's commons ToStringBuilder.
	 * Null values and empty Optionals are represented as absent parameters as that seems logical from user's perspective.
	 */
	public CacheId parameter(String name, Object value) {
		if (name.isBlank())
			throw new IllegalArgumentException();
		if (value == null)
			return this;
		if (value instanceof String)
			return parameter(name, (String)value);
		if (value instanceof Optional)
			return parameter(name, ((Optional<?>)value).orElse(null));
		if (value instanceof OptionalInt) {
			var optional = (OptionalInt)value;
			if (optional.isPresent())
				return parameter(name, optional.getAsInt());
			else
				return this;
		}
		if (value instanceof OptionalLong) {
			var optional = (OptionalLong)value;
			if (optional.isPresent())
				return parameter(name, optional.getAsLong());
			else
				return this;
		}
		if (value instanceof OptionalDouble) {
			var optional = (OptionalDouble)value;
			if (optional.isPresent())
				return parameter(name, optional.getAsDouble());
			else
				return this;
		}
		return parameter(name, value.toString());
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof CacheId))
			return false;
		var other = (CacheId)obj;
		return path.equals(other.path) && parameters.equals(other.parameters);
	}
	@Override
	public int hashCode() {
		return Objects.hash(path, parameters);
	}
	private int compareLists(List<String> left, List<String> right) {
		for (int i = 0; i < Math.min(left.size(), right.size()); ++i) {
			var diff = left.get(i).compareTo(right.get(i));
			if (diff != 0)
				return diff;
		}
		if (left.size() < right.size())
			return -1;
		if (left.size() > right.size())
			return 1;
		return 0;
	}
	@Override
	public int compareTo(CacheId other) {
		int diff = compareLists(path, other.path);
		if (diff != 0)
			return diff;
		var keys1 = StreamEx.of(parameters.keySet()).sorted().toList();
		var keys2 = StreamEx.of(other.parameters.keySet()).sorted().toList();
		diff = compareLists(keys1, keys2);
		if (diff != 0)
			return diff;
		for (var key : keys1) {
			diff = parameters.get(key).compareTo(other.parameters.get(key));
			if (diff != 0)
				return diff;
		}
		return 0;
	}
	@Override
	public String toString() {
		var name = String.join(".", path);
		if (parameters.isEmpty())
			return name;
		return name + "(" + StreamEx.of(parameters.keySet()).sorted().map(k -> k + "=" + parameters.get(k)).joining(",") + ")";
	}
	public String hash() {
		return Base64.getUrlEncoder().encodeToString(Exceptions.sneak().get(() -> MessageDigest.getInstance("SHA-256")).digest(toString().getBytes(StandardCharsets.UTF_8)));
	}
}
