/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

public class JdkCompat {
  public static SocketChannel connectUnixSocket(Path socketPath) throws IOException {
    SocketChannel client = SocketChannel.open(StandardProtocolFamily.UNIX);
    client.connect(UnixDomainSocketAddress.of(socketPath));
    return client;
  }
}
