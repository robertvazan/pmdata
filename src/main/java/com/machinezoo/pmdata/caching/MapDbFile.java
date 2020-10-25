// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata.caching;

import java.nio.file.*;
import java.util.*;
import org.mapdb.*;
import com.machinezoo.noexception.*;

public class MapDbFile implements CacheFile {
	private final Path path;
	@Override
	public Path path() {
		return path;
	}
	private boolean readonly;
	@Override
	public synchronized boolean readonly() {
		return readonly;
	}
	private MapDbFile(Path path, boolean readonly) {
		this.path = path;
		this.readonly = readonly;
	}
	public MapDbFile(Path path) {
		this.path = path;
		Exceptions.wrap().run(() -> Files.createDirectories(path.getParent()));
	}
	public MapDbFile() {
		this(CacheFiles.create());
	}
	public static CacheFormat<MapDbFile> format() {
		return new CacheFormat<>() {
			@Override
			public MapDbFile load(Path path) {
				return new MapDbFile(path, true);
			}
		};
	}
	private DB db;
	public synchronized DB db() {
		if (db == null) {
			var maker = DBMaker
				.fileDB(path.toFile());
			if (readonly) {
				maker
					.readOnly()
					/*
					 * Enable mmap for reading only. JVM apparently has some trouble reopening previously mmapped files.
					 * https://jankotek.gitbooks.io/mapdb/content/performance/
					 */
					.fileMmapEnable()
					/*
					 * Unknown optimization ("make mmap file faster"):
					 * https://jankotek.gitbooks.io/mapdb/content/performance/
					 */
					.fileMmapPreclearDisable()
					/*
					 * Read-only DB access does not require locks. Killed JVM would otherwise leave behind locks
					 * that would cause trouble next time the DB is opened.
					 */
					.fileLockDisable();
			} else {
				maker
					/*
					 * In absence of mmap, FileChannel is the next best choice for performance.
					 */
					.fileChannelEnable();
			}
			db = maker.make();
		}
		return db;
	}
	private final Map<String, HTreeMap<?, ?>> maps = new HashMap<>();
	@SuppressWarnings("unchecked")
	public synchronized <K, V> HTreeMap<K, V> map(String name, long capacity) {
		HTreeMap<?, ?> map = maps.get(name);
		if (map == null) {
			var maker = db().hashMap(name, new KryoMapDbSerializer<K>(), new KryoMapDbSerializer<V>());
			/*
			 * MapDb cannot dynamically adjust map layout. Default layout allows only 512K entries.
			 * Beyond that, collisions will kill performance. We cannot just set very high values,
			 * because then we will end up with 1 or more nodes per entry, wasting space and I/O.
			 * We will instead size the hash to have at least double the required capacity.
			 * 
			 * We will allow capacity to be unspecified when opening a map that already exists.
			 */
			if (!readonly && capacity >= 0) {
				if (capacity <= (1 << 3))
					maker.layout(1, 16, 1);
				else if (capacity <= (1 << 4))
					maker.layout(2, 16, 1);
				else if (capacity <= (1 << 5))
					maker.layout(4, 16, 1);
				else if (capacity <= (1 << 6))
					maker.layout(8, 16, 1);
				else if (capacity <= (1 << 10))
					maker.layout(8, 16, 2);
				else if (capacity <= (1 << 14))
					maker.layout(8, 16, 3);
				else if (capacity <= (1 << 18))
					maker.layout(8, 16, 4);
				else if (capacity <= (1 << 22))
					maker.layout(8, 16, 5);
				else if (capacity <= (1 << 26))
					maker.layout(8, 16, 6);
				else
					maker.layout(8, 16, 7);
			}
			maker.counterEnable();
			if (readonly || capacity < 0)
				map = maker.open();
			else
				map = maker.createOrOpen();
			maps.put(name, map);
		}
		return (HTreeMap<K, V>)map;
	}
	public <K, V> HTreeMap<K, V> map(String name) {
		return map(name, -1);
	}
	@Override
	public synchronized void commit() {
		if (!readonly) {
			for (var map : maps.values())
				Exceptions.log().run(map::close);
			maps.clear();
			if (db != null) {
				Exceptions.log().run(db::close);
				db = null;
			}
			readonly = true;
		}
	}
}
