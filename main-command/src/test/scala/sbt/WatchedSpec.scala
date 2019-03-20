/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, InputStream }
import java.nio.file.{ Files, Path }
import java.util.concurrent.atomic.AtomicBoolean

import org.scalatest.{ FlatSpec, Matchers }
import sbt.Watched._
import sbt.WatchedSpec._
import sbt.internal.FileCacheEntry
import sbt.io.FileEventMonitor.Event
import sbt.io._
import sbt.io.syntax._
import sbt.util.Logger

import scala.collection.mutable
import scala.concurrent.duration._

class WatchedSpec extends FlatSpec with Matchers {
  object Defaults {
    def config(
        globs: Seq[Glob],
        fileEventMonitor: Option[FileEventMonitor[FileCacheEntry]] = None,
        logger: Logger = NullLogger,
        handleInput: InputStream => Action = _ => Ignore,
        preWatch: (Int, Boolean) => Action = (_, _) => CancelWatch,
        onWatchEvent: Event[FileCacheEntry] => Action = _ => Ignore,
        triggeredMessage: (Path, Int) => Option[String] = (_, _) => None,
        watchingMessage: Int => Option[String] = _ => None
    ): WatchConfig = {
      val monitor = fileEventMonitor.getOrElse {
        val fileTreeRepository = FileTreeRepository.default(FileCacheEntry.default)
        globs.foreach(fileTreeRepository.register)
        FileEventMonitor.antiEntropy(
          fileTreeRepository,
          50.millis,
          m => logger.debug(m.toString),
          50.milliseconds,
          100.milliseconds
        )
      }
      WatchConfig.default(
        logger = logger,
        monitor,
        handleInput,
        preWatch,
        onWatchEvent,
        (_, _, state) => state,
        triggeredMessage,
        watchingMessage
      )
    }
  }
  object NullInputStream extends InputStream {
    override def available(): Int = 0
    override def read(): Int = -1
  }
  "Watched.watch" should "stop" in IO.withTemporaryDirectory { dir =>
    val config = Defaults.config(globs = Seq(dir.toRealPath.toGlob))
    Watched.watch(NullInputStream, () => Right(true), config) shouldBe CancelWatch
  }
  it should "trigger" in IO.withTemporaryDirectory { dir =>
    val triggered = new AtomicBoolean(false)
    val config = Defaults.config(
      globs = Seq(dir.toRealPath ** AllPassFilter),
      preWatch = (count, _) => if (count == 2) CancelWatch else Ignore,
      onWatchEvent = _ => { triggered.set(true); Trigger },
      watchingMessage = _ => {
        new File(dir, "file").createNewFile; None
      }
    )
    Watched.watch(NullInputStream, () => Right(true), config) shouldBe CancelWatch
    assert(triggered.get())
  }
  it should "filter events" in IO.withTemporaryDirectory { dir =>
    val realDir = dir.toRealPath
    val queue = new mutable.Queue[Path]
    val foo = realDir.toPath.resolve("foo")
    val bar = realDir.toPath.resolve("bar")
    val config = Defaults.config(
      globs = Seq(realDir ** AllPassFilter),
      preWatch = (count, _) => if (count == 2) CancelWatch else Ignore,
      onWatchEvent = e => if (e.entry.typedPath.toPath == foo) Trigger else Ignore,
      triggeredMessage = (tp, _) => { queue += tp; None },
      watchingMessage = _ => { Files.createFile(bar); Thread.sleep(5); Files.createFile(foo); None }
    )
    Watched.watch(NullInputStream, () => Right(true), config) shouldBe CancelWatch
    queue.toIndexedSeq shouldBe Seq(foo)
  }
  it should "enforce anti-entropy" in IO.withTemporaryDirectory { dir =>
    val realDir = dir.toRealPath
    val queue = new mutable.Queue[Path]
    val foo = realDir.toPath.resolve("foo")
    val bar = realDir.toPath.resolve("bar")
    val config = Defaults.config(
      globs = Seq(realDir ** AllPassFilter),
      preWatch = (count, _) => if (count == 3) CancelWatch else Ignore,
      onWatchEvent = _ => Trigger,
      triggeredMessage = (tp, _) => { queue += tp; None },
      watchingMessage = count => {
        count match {
          case 1 => Files.createFile(bar)
          case 2 =>
            bar.toFile.setLastModified(5000)
            Files.createFile(foo)
          case _ =>
        }
        None
      }
    )
    Watched.watch(NullInputStream, () => Right(true), config) shouldBe CancelWatch
    queue.toIndexedSeq shouldBe Seq(bar, foo)
  }
  it should "halt on error" in IO.withTemporaryDirectory { dir =>
    val halted = new AtomicBoolean(false)
    val config = Defaults.config(
      globs = Seq(dir.toRealPath ** AllPassFilter),
      preWatch = (_, lastStatus) => if (lastStatus) Ignore else { halted.set(true); HandleError }
    )
    Watched.watch(NullInputStream, () => Right(false), config) shouldBe HandleError
    assert(halted.get())
  }
  it should "reload" in IO.withTemporaryDirectory { dir =>
    val config = Defaults.config(
      globs = Seq(dir.toRealPath ** AllPassFilter),
      preWatch = (_, _) => Ignore,
      onWatchEvent = _ => Reload,
      watchingMessage = _ => { new File(dir, "file").createNewFile(); None }
    )
    Watched.watch(NullInputStream, () => Right(true), config) shouldBe Reload
  }
}

object WatchedSpec {
  implicit class FileOps(val f: File) {
    def toRealPath: File = f.toPath.toRealPath().toFile
  }
}
