/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbt

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

class IPCSpec extends AnyFlatSpec {
  "server" should "allow same number of connections as determined in socket backlog" in {
    noException should be thrownBy {
      val server = IPC.unmanagedServer
      (1 until IPC.socketBacklog + 1).foreach { _ =>
        IPC.client(server.port)(identity)
      }
      server.close()
    }
  }
}
