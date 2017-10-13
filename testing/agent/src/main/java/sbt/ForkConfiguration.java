/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt;

import java.io.Serializable;

public final class ForkConfiguration implements Serializable {
	private final boolean ansiCodesSupported;
	private final boolean parallel;

	public ForkConfiguration(final boolean ansiCodesSupported, final boolean parallel) {
		this.ansiCodesSupported = ansiCodesSupported;
		this.parallel = parallel;
	}

	public boolean isAnsiCodesSupported() {
		return ansiCodesSupported;
	}

	public boolean isParallel() {
		return parallel;
	}
}
