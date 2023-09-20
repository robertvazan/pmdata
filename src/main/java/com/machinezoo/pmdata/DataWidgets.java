// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.apache.commons.lang3.exception.*;
import org.slf4j.*;
import com.google.common.cache.*;
import com.machinezoo.hookless.*;
import com.machinezoo.meerkatwidgets.*;
import com.machinezoo.noexception.*;
import com.machinezoo.pmdata.caching.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pushmode.dom.*;
import com.machinezoo.stagean.*;

@DraftApi
public class DataWidgets {
	private static class PartialContent {
		SiteFragment fragment;
		Throwable exception;
	}
	private static final Logger logger = LoggerFactory.getLogger(DataWidgets.class);
	private static CacheDerivative<PartialContent> evaluate(SiteFragment.Key key, Runnable runnable) {
		return CacheDerivative.capture(() -> {
			var partial = new PartialContent();
			partial.fragment = SiteFragment.forKey(key);
			try (var scope = partial.fragment.open()) {
				try {
					/*
					 * This would be a great place to call SiteReload.watch().
					 * That would however cause all cached dialogs to refresh
					 * whenever XML template text changes, defeating the purpose of caching.
					 * We will therefore require refresh in the browser
					 * to force cached dialog refresh when code changes.
					 */
					runnable.run();
				} catch (Throwable ex) {
					if (!ExceptionUtils.getThrowableList(ex).stream().anyMatch(x -> x instanceof EmptyCacheException || x instanceof WidgetException)
						&& !CurrentReactiveScope.blocked()) {
						logger.error("DataWidget threw an exception.", ex);
					}
					try {
						for (var cause : ExceptionUtils.getThrowableList(ex))
							if (cause instanceof WidgetException)
								((WidgetException)cause).render();
					} catch (Throwable nex) {
						logger.error("WidgetException failed to render.", nex);
					}
					if (!ExceptionUtils.getThrowableList(ex).stream().anyMatch(x -> x instanceof WidgetException))
						partial.exception = ex;
				}
			}
			return partial;
		});
	}
	/*
	 * Caching requires a repeatable key. Unfortunately, this cache depends on Runnable.
	 * We have no way to compare/hash Runnable instances.
	 * We instead require fragment key to change whenever Runnable changes.
	 * In practice, the Runnable cannot really depend on anything. It must be a method reference.
	 * 
	 * We will use two cache layers, one for the content itself and one for everything including cache table.
	 * This arrangement will avoid content refresh when we just want to update cache refresh progress.
	 */
	private static final Cache<SiteFragment.Key, ReactiveLazy<CacheDerivative<PartialContent>>> innerCache = CacheBuilder.newBuilder()
		/*
		 * Ideally, we would like to keep entries referenced by outer cache,
		 * but since that's not possible, we will just duplicate outer cache configuration.
		 */
		.expireAfterAccess(1, TimeUnit.MINUTES)
		.maximumSize(100)
		.build();
	@DraftCode
	@CodeIssue("Configurable caching of rendered widget.")
	@CodeIssue("Cache report should be shown out of band in order to support inline widgets and inheriting attributes from XML (esp. class).")
	@CodeIssue("Cache ignores changes in XML attributes / method parameters.")
	private static SiteFragment assemble(SiteFragment.Key key, Runnable runnable) {
		var fragment = SiteFragment.forKey(key);
		try (var scope = fragment.open()) {
			var derivative = Exceptions.sneak().get(() -> innerCache.get(key, () -> new ReactiveLazy<>(() -> evaluate(key, runnable)))).get();
			var partial = derivative.value().get();
			partial.fragment.render();
			new CacheReport()
				.input(derivative.input())
				.exception(partial.exception)
				.show();
		}
		return fragment;
	}
	private static final Cache<SiteFragment.Key, ReactiveLazy<SiteFragment>> outerCache = CacheBuilder.newBuilder()
		/*
		 * This is a compute cache. Assuming the computations may be expensive, consuming up to a second of compute time,
		 * it is reasonable to cache output for a minute. We might want to make this configurable via properties.
		 */
		.expireAfterAccess(1, TimeUnit.MINUTES)
		/*
		 * Cached data may be large, perhaps containing embedded images. We should assume it might be up to 1MB large.
		 * In that case, it is reasonable to default to a fairly low size limit, for example 100.
		 * Reasonable size limit varies a lot with traffic and system configuration.
		 */
		.maximumSize(100)
		.build();
	@DraftCode("configurable cache")
	private static void execute(Runnable runnable) {
		var key = SiteFragment.get().key();
		Exceptions.sneak().get(() -> outerCache.get(key, () -> new ReactiveLazy<>(() -> assemble(key, runnable)))).get().render();
	}
	/*
	 * Code below is almost identical to SiteWidget code. The only difference is that the method call is wrapped to allow for caching.
	 */
	private static class WidgetMethod {
		final String name;
		final Method method;
		final boolean isStatic;
		volatile boolean accessible;
		final List<Supplier<Object>> suppliers = new ArrayList<>();
		final Consumer<Object> consumer;
		WidgetMethod(Method method, DataWidget annotation) {
			name = !annotation.name().isBlank() ? annotation.name() : !annotation.value().isBlank() ? annotation.value() : method.getName();
			this.method = method;
			isStatic = Modifier.isStatic(method.getModifiers());
			if (isStatic) {
				if (!method.canAccess(null))
					method.setAccessible(true);
				accessible = true;
			}
			for (var parameter : method.getParameters()) {
				if (parameter.getType() == String.class) {
					var pname = parameter.getName();
					suppliers.add(() -> SiteTemplate.consume(pname));
				} else if (parameter.getType() == DomElement.class)
					suppliers.add(() -> SiteTemplate.element());
				else if (DomContent.class.isAssignableFrom(parameter.getType())) {
					suppliers.add(() -> {
						var children = SiteTemplate.element().children();
						return children.isEmpty() ? null : new DomFragment().add(children);
					});
				} else
					throw new IllegalArgumentException("Unsupported parameter type: " + parameter.getParameterizedType());
			}
			if (DomContent.class.isAssignableFrom(method.getReturnType()))
				consumer = r -> SiteFragment.get().add((DomContent)r);
			else if (method.getReturnType() == void.class) {
				consumer = r -> {};
			} else
				throw new IllegalArgumentException("Unsupported return type: " + method.getReturnType());
		}
		Runnable runnable(Object object) {
			if (!isStatic && !accessible && !method.canAccess(object)) {
				method.setAccessible(true);
				accessible = true;
			}
			return () -> execute(Exceptions.wrap().runnable(() -> consumer.accept(method.invoke(object, suppliers.stream().map(p -> p.get()).toArray(Object[]::new)))));
		}
	}
	private static List<WidgetMethod> compile(Class<?> clazz) {
		if (clazz.getSuperclass() == null)
			return new ArrayList<>();
		var widgets = compile(clazz.getSuperclass());
		for (var method : clazz.getDeclaredMethods()) {
			var annotation = method.getAnnotation(DataWidget.class);
			if (annotation != null)
				widgets.add(new WidgetMethod(method, annotation));
		}
		return widgets;
	}
	private static Map<Class<?>, List<WidgetMethod>> cache = new HashMap<>();
	private static synchronized List<WidgetMethod> widgets(Class<?> clazz) {
		return cache.computeIfAbsent(clazz, DataWidgets::compile);
	}
	public static void register(Class<?> clazz, SiteTemplate template) {
		for (var widget : widgets(clazz))
			if (widget.isStatic)
				template.register(widget.name, widget.runnable(null));
	}
	public static void register(Object object, SiteTemplate template) {
		register(object.getClass(), template);
		for (var widget : widgets(object.getClass()))
			if (!widget.isStatic)
				template.register(widget.name, widget.runnable(object));
	}
}
