/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.nio.file.{ Files, Path }
import java.util.concurrent.atomic.AtomicBoolean

import org.scalatest.{ FlatSpec, Matchers }
import sbt.Watched._
import sbt.WatchedSpec._
import sbt.io.FileEventMonitor.Event
import sbt.io.{ FileEventMonitor, IO, TypedPath }
import sbt.util.Logger

import scala.collection.mutable
import scala.concurrent.duration._

class WatchedSpec extends FlatSpec with Matchers {
  object Defaults {
    private val fileTreeViewConfig = FileTreeViewConfig.default(50.millis, 50.millis)
    def config(
        sources: Seq[WatchSource],
        fileEventMonitor: Option[FileEventMonitor[Path]] = None,
        logger: Logger = NullLogger,
        handleInput: () => Action = () => Ignore,
        shouldTerminate: Int => Boolean = _ => true,
        onWatchEvent: Event[Path] => Action = _ => Ignore,
        triggeredMessage: (TypedPath, Int) => Option[String] = (_, _) => None,
        watchingMessage: Int => Option[String] = _ => None
    ): WatchConfig = {
      val monitor = fileEventMonitor.getOrElse(
        fileTreeViewConfig.newMonitor(fileTreeViewConfig.newDataView(), sources, logger)
      )
      WatchConfig.default(
        logger = logger,
        monitor,
        handleInput,
        shouldTerminate,
        onWatchEvent,
        triggeredMessage,
        watchingMessage
      )
    }
  }
  "Watched.watch" should "stop" in IO.withTemporaryDirectory { dir =>
    val config = Defaults.config(sources = Seq(WatchSource(dir.toRealPath)))
    Watched.watch(() => Right(true), config) should be(())
  }
  it should "trigger" in IO.withTemporaryDirectory { dir =>
    val triggered = new AtomicBoolean(false)
    val config = Defaults.config(
      sources = Seq(WatchSource(dir.toRealPath)),
      shouldTerminate = count => count == 2,
      onWatchEvent = _ => { triggered.set(true); Trigger },
      watchingMessage = _ => {
        new File(dir, "file").createNewFile; None
      }
    )
    Watched.watch(() => Right(true), config) should be(())
    assert(triggered.get())
  }
  it should "filter events" in IO.withTemporaryDirectory { dir =>
    val realDir = dir.toRealPath
    val queue = new mutable.Queue[TypedPath]
    val foo = realDir.toPath.resolve("foo")
    val bar = realDir.toPath.resolve("bar")
    val config = Defaults.config(
      sources = Seq(WatchSource(realDir)),
      shouldTerminate = count => count == 2,
      onWatchEvent = e => if (e.entry.typedPath.getPath == foo) Trigger else Ignore,
      triggeredMessage = (tp, _) => { queue += tp; None },
      watchingMessage = _ => { Files.createFile(bar); Thread.sleep(5); Files.createFile(foo); None }
    )
    Watched.watch(() => Right(true), config) should be(())
    queue.toIndexedSeq.map(_.getPath) shouldBe Seq(foo)
  }
  it should "enforce anti-entropy" in IO.withTemporaryDirectory { dir =>
    val realDir = dir.toRealPath
    val queue = new mutable.Queue[TypedPath]
    val foo = realDir.toPath.resolve("foo")
    val bar = realDir.toPath.resolve("bar")
    val config = Defaults.config(
      sources = Seq(WatchSource(realDir)),
      shouldTerminate = count => count == 3,
      onWatchEvent = _ => Trigger,
      triggeredMessage = (tp, _) => { queue += tp; None },
      watchingMessage = count => {
        if (count == 1) Files.createFile(bar)
        else if (count == 2) {
          bar.toFile.setLastModified(5000)
          Files.createFile(foo)
        }
        None
      }
    )
    Watched.watch(() => Right(true), config) should be(())
    queue.toIndexedSeq.map(_.getPath) shouldBe Seq(bar, foo)
  }
}

object WatchedSpec {
  implicit class FileOps(val f: File) {
    def toRealPath: File = f.toPath.toRealPath().toFile
  }
}
