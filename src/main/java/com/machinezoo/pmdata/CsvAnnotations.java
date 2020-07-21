// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.commons.lang3.*;
import org.supercsv.cellprocessor.*;
import org.supercsv.cellprocessor.ift.*;
import org.supercsv.exception.*;
import org.supercsv.io.*;
import org.supercsv.prefs.*;
import org.supercsv.util.*;
import com.machinezoo.noexception.*;
import com.machinezoo.stagean.*;
import one.util.streamex.*;

/*
 * I cannot find CSV library that would be as neat as Gson, so here comes custom wrapper around SuperCSV.
 */
@DraftApi
@DraftCode
public class CsvAnnotations {
	private static StreamEx<Field> fields(Class<?> clazz) {
		return StreamEx.of(clazz.getDeclaredFields());
	}
	/*
	 * SuperCSV does not automatically discover bean properties (and ignores fields and fluent accessors entirely).
	 */
	private static String[] names(Class<?> clazz) {
		return fields(clazz)
			.map(f -> StringUtils.capitalize(f.getName()))
			.toArray(String[]::new);
	}
	/*
	 * SuperCSV will treat everything as a string unless explicitly told otherwise.
	 */
	@SuppressWarnings("unchecked")
	private static CellProcessor processor(Class<?> type) {
		/*
		 * More types will likely have to be added here in the future.
		 */
		if (type.isEnum())
			return new ParseEnum((Class<Enum<?>>)type);
		if (type == Double.TYPE)
			return new ParseDouble();
		if (type == Integer.TYPE)
			return new ParseInt();
		if (type == Long.TYPE)
			return new ParseLong();
		if (type == Instant.class)
			return new ParseInstant();
		return null;
	}
	private static class ParseInstant extends CellProcessorAdaptor implements StringCellProcessor {
		@SuppressWarnings("unchecked")
		public Object execute(Object value, CsvContext context) {
			validateInputNotNull(value, context);
			Instant result;
			if (value instanceof Instant)
				result = (Instant)value;
			else if (value instanceof String)
				result = Instant.parse((String)value);
			else
				throw new SuperCsvCellProcessorException(value.getClass().getName(), context, this);
			return next.execute(result, context);
		}
	}
	private static CellProcessor[] processors(Class<?> clazz) {
		return fields(clazz)
			.map(f -> processor(f.getType()))
			.toArray(CellProcessor[]::new);
	}
	public static <T> byte[] encode(Class<T> clazz, Iterable<T> items) {
		var names = names(clazz);
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Writer writer = new OutputStreamWriter(buffer);
		Exceptions.sneak().run(() -> {
			try (CsvBeanWriter csv = new CsvBeanWriter(writer, CsvPreference.STANDARD_PREFERENCE)) {
				csv.writeHeader(names);
				for (T item : items)
					csv.write(item, names);
			}
		});
		return buffer.toByteArray();
	}
	public static <T> List<T> decode(Class<T> clazz, byte[] bytes) {
		var names = names(clazz);
		var processors = processors(clazz);
		List<T> list = new ArrayList<>();
		ByteArrayInputStream buffer = new ByteArrayInputStream(bytes);
		Exceptions.sneak().run(() -> {
			try (CsvBeanReader csv = new CsvBeanReader(new InputStreamReader(buffer), CsvPreference.STANDARD_PREFERENCE)) {
				/*
				 * Remove the header as otherwise SuperCSV would try to parse it into a record and fail.
				 */
				csv.getHeader(true);
				while (true) {
					T item = csv.read(clazz, names, processors);
					if (item == null)
						break;
					list.add(item);
				}
			}
		});
		return list;
	}
	public static <T> void write(Path path, Class<T> clazz, Iterable<T> items) {
		byte[] bytes = encode(clazz, items);
		Exceptions.sneak().run(() -> {
			Path temporary = Files.createTempFile(path.getParent(), "tmp-", ".csv");
			Files.write(temporary, bytes);
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
		});
	}
	public static <T> List<T> read(Path path, Class<T> clazz) {
		if (!Files.exists(path))
			return Collections.emptyList();
		return decode(clazz, Exceptions.sneak().get(() -> Files.readAllBytes(path)));
	}
	/*
	 * Since this is used for long-lived data that will be stored as resources,
	 * we provide convenience methods to store annotations in local resource files.
	 */
	private static Path resolveResource(Class<?> owner, String relative) {
		Path path = Paths.get("src/main/resources");
		if (!relative.startsWith("/"))
			path = path.resolve(owner.getPackageName().replace('.', '/'));
		path = path.resolve(relative);
		return path;
	}
	public static <T> void writeResource(String path, Class<T> clazz, Iterable<T> items) {
		write(resolveResource(clazz, path), clazz, items);
	}
	/*
	 * TODO: Resource reader should probably use resource APIs,
	 * so that CSV resources work in read-only mode when the app is deployed.
	 */
	public static <T> List<T> readResource(String path, Class<T> clazz) {
		return read(resolveResource(clazz, path), clazz);
	}
}
