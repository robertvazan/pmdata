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
	/*
	 * Force delayed initialization, so that we don't open the file just because some code mentions this class.
	 */
	private static class PreferencesFile {
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
	}
	@Override
	public String get(String key) {
		return PreferencesFile.map.get(key);
	}
	@Override
	public void set(String key, String value) {
		PreferencesFile.map.put(key, value);
		PreferencesFile.db.commit();
	}
}
