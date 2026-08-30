/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

/**
 * Base (Java 8) fallback. The Multi-Release variant under src/jdk17/java implements this using JDK
 * 16+ Unix domain socket APIs (StandardProtocolFamily.UNIX, UnixDomainSocketAddress); it's the one
 * actually loaded when the worker runs on Java 17+.
 */
public class JdkCompat {
  public static SocketChannel connectUnixSocket(Path socketPath) throws IOException {
    throw new UnsupportedOperationException(
        "Unix domain sockets require Java 16+; this worker JVM is running on an older version");
  }
}
