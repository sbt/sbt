/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import java.nio.file.Files
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import org.scalasbt.ipcsocket.{ UnixDomainServerSocket, UnixDomainSocket }
import verify.BasicTestSuite

object ServerSessionImplSpec extends BasicTestSuite:
  private val isWin = System.getProperty("os.name").toLowerCase.contains("win")
  test("close delivers EOF to the peer while the read thread is parked"):
    // named pipes have different close semantics; the swallowed-close mechanism is unix-specific
    if isWin then ()
    else
      val path = Files.createTempDirectory("session-eof").resolve("sock")
      val server = UnixDomainServerSocket(path.toString, false)
      val peerResult = new LinkedBlockingQueue[Integer]
      val accepted = new Thread(() => {
        val conn = server.accept()
        peerResult.put(conn.getInputStream.read())
      })
      accepted.setDaemon(true)
      accepted.start()
      val session = new ServerSessionImpl(UnixDomainSocket(path.toString, false))
      // let the session's read thread park in its native read
      Thread.sleep(500)
      session.close()
      assert(peerResult.poll(10, TimeUnit.SECONDS) == -1)
end ServerSessionImplSpec
