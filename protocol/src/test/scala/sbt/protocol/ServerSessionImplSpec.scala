/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import java.net.{ StandardProtocolFamily, UnixDomainSocketAddress }
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import scala.util.Using
import sbt.io.IO
import verify.BasicTestSuite

object ServerSessionImplSpec extends BasicTestSuite:
  private val isWin = System.getProperty("os.name").toLowerCase.contains("win")
  test("close delivers EOF to the peer while the read thread is parked"):
    if isWin then ()
    else
      val dir = Files.createTempDirectory("session-eof")
      val path = dir.resolve("sock")
      try
        Using.resource(ServerSocketChannel.open(StandardProtocolFamily.UNIX)): server =>
          server.bind(UnixDomainSocketAddress.of(path))
          val peerResult = new LinkedBlockingQueue[Integer]
          val accepted = new Thread(() =>
            val conn = DuplexChannels.newSocket(server.accept())
            peerResult.put(conn.getInputStream.read())
          )
          accepted.setDaemon(true)
          accepted.start()
          val client = ClientSocket.unixSocket(path)
          val session = new ServerSessionImpl(client)
          Thread.sleep(500)
          session.close()
          assert(peerResult.poll(10, TimeUnit.SECONDS) == -1)
          assert(client.isClosed)
      finally IO.delete(dir.toFile)
end ServerSessionImplSpec
