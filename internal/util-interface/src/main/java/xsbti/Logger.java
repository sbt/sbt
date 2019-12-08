/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
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
