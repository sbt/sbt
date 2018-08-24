/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.util.concurrent.CountDownLatch

import org.scalatest.{ FlatSpec, Matchers }
import sbt.Watched.{ NullLogger, WatchSource }
import sbt.internal.io.WatchState
import sbt.io.IO

import scala.concurrent.duration._
import WatchedSpec._

class WatchedSpec extends FlatSpec with Matchers {
  "Watched" should "stop" in IO.withTemporaryDirectory { dir =>
    val latch = new CountDownLatch(1)
    val config = WatchConfig.default(
      NullLogger,
      () => latch.getCount == 0,
      triggeredMessage = _ => { latch.countDown(); None },
      watchingMessage = _ => { new File(dir, "foo").createNewFile(); None },
      watchState =
        WatchState.empty(Watched.createWatchService(), WatchSource(dir.toRealPath) :: Nil),
      pollInterval = 5.millis,
      antiEntropy = 5.millis
    )
    Watched.watch(() => Right(true), config)
    assert(latch.getCount == 0)
  }
}

object WatchedSpec {
  implicit class FileOps(val f: File) {
    def toRealPath: File = f.toPath.toRealPath().toFile
  }
}
