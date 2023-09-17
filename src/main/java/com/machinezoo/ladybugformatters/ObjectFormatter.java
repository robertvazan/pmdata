// Part of Ladybug Formatters: https://ladybugformatters.machinezoo.com/
package com.machinezoo.ladybugformatters;

import java.time.*;
import java.util.*;
import com.machinezoo.hookless.time.*;
import com.machinezoo.pushmode.dom.*;

public class ObjectFormatter implements ObjectValueFormatter {
	private static ObjectValueFormatter from(DoubleValueFormatter typed) {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return typed.plain(((Number)value).doubleValue());
			}
			@Override
			public String detail(Object value) {
				return typed.detail(((Number)value).doubleValue());
			}
			@Override
			public DomContent format(Object value) {
				return typed.format(((Number)value).doubleValue());
			}
		};
	}
	private static ObjectValueFormatter from(InstantValueFormatter typed) {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return typed.plain((Instant)value);
			}
			@Override
			public String detail(Object value) {
				return typed.detail((Instant)value);
			}
			@Override
			public DomContent format(Object value) {
				return typed.format((Instant)value);
			}
		};
	}
	private static ObjectValueFormatter from(DurationValueFormatter typed) {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return typed.plain((Duration)value);
			}
			@Override
			public String detail(Object value) {
				return typed.detail((Duration)value);
			}
			@Override
			public DomContent format(Object value) {
				return typed.format((Duration)value);
			}
		};
	}
	private static ObjectValueFormatter from(ReactiveDurationValueFormatter typed) {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return typed.plain((ReactiveDuration)value);
			}
			@Override
			public String detail(Object value) {
				return typed.detail((ReactiveDuration)value);
			}
			@Override
			public DomContent format(Object value) {
				return typed.format((ReactiveDuration)value);
			}
		};
	}
	private static ObjectValueFormatter nil() {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return null;
			}
			@Override
			public String detail(Object value) {
				return null;
			}
			@Override
			public DomContent format(Object value) {
				return null;
			}
		};
	}
	private static ObjectValueFormatter optional(ObjectValueFormatter inner) {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return inner.plain(((Optional<?>)value).get());
			}
			@Override
			public String detail(Object value) {
				return inner.detail(((Optional<?>)value).get());
			}
			@Override
			public DomContent format(Object value) {
				return inner.format(((Optional<?>)value).get());
			}
		};
	}
	private static ObjectValueFormatter optionalInt(DoubleValueFormatter inner) {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return inner.plain(((OptionalInt)value).getAsInt());
			}
			@Override
			public String detail(Object value) {
				return inner.detail(((OptionalInt)value).getAsInt());
			}
			@Override
			public DomContent format(Object value) {
				return inner.format(((OptionalInt)value).getAsInt());
			}
		};
	}
	private static ObjectValueFormatter optionalLong(DoubleValueFormatter inner) {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return inner.plain(((OptionalLong)value).getAsLong());
			}
			@Override
			public String detail(Object value) {
				return inner.detail(((OptionalLong)value).getAsLong());
			}
			@Override
			public DomContent format(Object value) {
				return inner.format(((OptionalLong)value).getAsLong());
			}
		};
	}
	private static ObjectValueFormatter optionalDouble(DoubleValueFormatter inner) {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return inner.plain(((OptionalDouble)value).getAsDouble());
			}
			@Override
			public String detail(Object value) {
				return inner.detail(((OptionalDouble)value).getAsDouble());
			}
			@Override
			public DomContent format(Object value) {
				return inner.format(((OptionalDouble)value).getAsDouble());
			}
		};
	}
	private static ObjectValueFormatter stringifier() {
		return new ObjectValueFormatter() {
			@Override
			public String plain(Object value) {
				return value.toString();
			}
			@Override
			public String detail(Object value) {
				return null;
			}
		};
	}
	private static ObjectValueFormatter identify(Object value) {
		if (value instanceof Number)
			return from(new NumberFormatter());
		if (value instanceof Instant)
			return from(new AgoFormatter());
		if (value instanceof Duration)
			return from((DurationValueFormatter)new DurationFormatter());
		if (value instanceof ReactiveDuration)
			return from((ReactiveDurationValueFormatter)new DurationFormatter());
		if (value instanceof Optional) {
			var optional = (Optional<?>)value;
			return optional.isPresent() ? optional(identify(optional.get())) : nil();
		}
		if (value instanceof OptionalInt)
			return optionalInt(new NumberFormatter());
		if (value instanceof OptionalLong)
			return optionalLong(new NumberFormatter());
		if (value instanceof OptionalDouble)
			return optionalDouble(new NumberFormatter());
		return stringifier();
	}
	@Override
	public String plain(Object value) {
		if (value == null)
			return null;
		return identify(value).plain(value);
	}
	@Override
	public String detail(Object value) {
		if (value == null)
			return null;
		return identify(value).detail(value);
	}
	@Override
	public DomContent format(Object value) {
		if (value == null)
			return null;
		if (value instanceof DomContent)
			return (DomContent)value;
		return identify(value).format(value);
	}
}
