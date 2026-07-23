/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.net.{ StandardProtocolFamily, UnixDomainSocketAddress }
import java.nio.channels.SocketChannel
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import sbt.internal.util.Util
import sbt.protocol.ClientSocket
import scala.util.Using
import scala.util.control.NonFatal

private[sbt] object BootServerSocketProbe:
  private val timeoutMillis = 2000L

  /**
   * True only if something answers on the boot socket at `location`. A live server answers
   * immediately, so the connect runs on a daemon thread bounded by [[timeoutMillis]]: the
   * underlying native connect has no timeout and blocks indefinitely against a bound socket whose
   * listen backlog is saturated, which must never hang startup.
   *
   * Primary: connects via JDK 17 SocketChannel (Unix domain socket, works on all platforms).
   * Windows fallback: if the primary fails, also tries [[ClientSocket.localSocket]] (named pipe)
   * to detect older sbt servers that pre-date Unix domain socket boot sockets.
   */
  def liveServerDetected(location: String, useJni: Boolean): Boolean =
    val answered = new AtomicBoolean(false)
    val done = new CountDownLatch(1)
    val t = new Thread(
      () =>
        try
          try
            Using.resource(SocketChannel.open(StandardProtocolFamily.UNIX)): ch =>
              ch.connect(UnixDomainSocketAddress.of(location))
              answered.set(true)
          catch case NonFatal(_) | (_: LinkageError) => ()
          if Util.isWindows && !answered.get() then
            try
              ClientSocket.localSocket(location, useJni).close()
              answered.set(true)
            catch case NonFatal(_) | (_: LinkageError) => ()
        finally done.countDown(),
      "sbt-boot-socket-probe"
    )
    t.setDaemon(true)
    t.start()
    done.await(timeoutMillis, TimeUnit.MILLISECONDS)
    answered.get()
end BootServerSocketProbe
