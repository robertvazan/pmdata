package com.machinezoo.pmdata.caching;

public interface CanonicalPersistentSource<T> extends PersistentSource<T> {
	default PersistentSource<T> canonicalize() {
		return this;
	}
}
