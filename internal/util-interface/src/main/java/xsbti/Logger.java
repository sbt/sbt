/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

import java.util.function.Supplier;

public interface Logger {
	void error(Supplier<String> msg);
	void warn(Supplier<String> msg);
	void info(Supplier<String> msg);
	void debug(Supplier<String> msg);
	void trace(Supplier<Throwable> exception);
}
