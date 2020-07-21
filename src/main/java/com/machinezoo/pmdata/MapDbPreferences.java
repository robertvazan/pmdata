// Part of PMData: https://pmdata.machinezoo.com
package com.machinezoo.pmdata;

import java.util.*;
import org.mapdb.*;
import com.machinezoo.hookless.util.*;
import com.machinezoo.pmsite.*;
import com.machinezoo.pmsite.preferences.*;
import com.machinezoo.stagean.*;

@DraftApi
public class MapDbPreferences extends PreferenceStorage {
	private static final DB db = DBMaker
		.fileDB(SiteFiles.configOf(MapDbPreferences.class.getSimpleName()).resolve("preferences.db").toString())
		.checksumHeaderBypass()
		.transactionEnable()
		.make();
	private static final Map<String, String> map = ReactiveCollections.map(
		db.hashMap("preferences", Serializer.STRING, Serializer.STRING).createOrOpen(),
		new ReactiveCollections.Options()
			.perItem()
			.compareValues()
			.ignoreWriteStatus()
			.ignoreWriteExceptions());
	@Override
	public String get(String key) {
		return map.get(key);
	}
	@Override
	public void set(String key, String value) {
		map.put(key, value);
		db.commit();
	}
}
