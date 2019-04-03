/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, InputStream }
import java.nio.file.{ Files, Path }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import org.scalatest.{ FlatSpec, Matchers }
import sbt.Watch.{ NullLogger, _ }
import sbt.WatchSpec._
import sbt.internal.nio.{ FileEvent, FileEventMonitor, FileTreeRepository }
import sbt.io._
import sbt.io.syntax._
import sbt.nio.file.{ FileAttributes, Glob }
import sbt.util.Logger

import scala.collection.mutable
import scala.concurrent.duration._

class WatchSpec extends FlatSpec with Matchers {
  private type NextAction = () => Watch.Action
  private def watch(task: Task, callbacks: (NextAction, NextAction)): Watch.Action =
    Watch(task, callbacks._1, callbacks._2)
  object TestDefaults {
    def callbacks(
        inputs: Seq[Glob],
        fileEventMonitor: Option[FileEventMonitor[FileEvent[FileAttributes]]] = None,
        logger: Logger = NullLogger,
        parseEvent: () => Watch.Action = () => Ignore,
        onStartWatch: () => Watch.Action = () => CancelWatch: Watch.Action,
        onWatchEvent: FileEvent[FileAttributes] => Watch.Action = _ => Ignore,
        triggeredMessage: FileEvent[FileAttributes] => Option[String] = _ => None,
        watchingMessage: () => Option[String] = () => None
    ): (NextAction, NextAction) = {
      val monitor: FileEventMonitor[FileEvent[FileAttributes]] = fileEventMonitor.getOrElse {
        val fileTreeRepository = FileTreeRepository.default
        inputs.foreach(fileTreeRepository.register)
        FileEventMonitor.antiEntropy(
          fileTreeRepository,
          50.millis,
          m => logger.debug(m.toString),
          50.millis,
          10.minutes
        )
      }
      val onTrigger: FileEvent[FileAttributes] => Unit = event => {
        triggeredMessage(event).foreach(logger.info(_))
      }
      val onStart: () => Watch.Action = () => {
        watchingMessage().foreach(logger.info(_))
        onStartWatch()
      }
      val nextAction: NextAction = () => {
        val inputAction = parseEvent()
        val fileActions = monitor.poll(10.millis).map { e: FileEvent[FileAttributes] =>
          onWatchEvent(e) match {
            case Trigger => onTrigger(e); Trigger
            case action  => action
          }
        }
        (inputAction +: fileActions).min
      }
      (onStart, nextAction)
    }
  }
  object NullInputStream extends InputStream {
    override def available(): Int = 0
    override def read(): Int = -1
  }
  private class Task extends (() => Unit) {
    private val count = new AtomicInteger(0)
    override def apply(): Unit = {
      count.incrementAndGet()
      ()
    }
    def getCount: Int = count.get()
  }
  "Watch" should "stop" in IO.withTemporaryDirectory { dir =>
    val task = new Task
    watch(task, TestDefaults.callbacks(inputs = Seq(dir.toRealPath ** AllPassFilter))) shouldBe CancelWatch
  }
  it should "trigger" in IO.withTemporaryDirectory { dir =>
    val triggered = new AtomicBoolean(false)
    val task = new Task
    val callbacks = TestDefaults.callbacks(
      inputs = Seq(dir.toRealPath ** AllPassFilter),
      onStartWatch = () => if (task.getCount == 2) CancelWatch else Ignore,
      onWatchEvent = _ => { triggered.set(true); Trigger },
      watchingMessage = () => {
        new File(dir, "file").createNewFile; None
      }
    )
    watch(task, callbacks) shouldBe CancelWatch
    assert(triggered.get())
  }
  it should "filter events" in IO.withTemporaryDirectory { dir =>
    val realDir = dir.toRealPath
    val queue = new mutable.Queue[Path]
    val foo = realDir.toPath.resolve("foo")
    val bar = realDir.toPath.resolve("bar")
    val task = new Task
    val callbacks = TestDefaults.callbacks(
      inputs = Seq(realDir ** AllPassFilter),
      onStartWatch = () => if (task.getCount == 2) CancelWatch else Ignore,
      onWatchEvent = e => if (e.path == foo) Trigger else Ignore,
      triggeredMessage = e => { queue += e.path; None },
      watchingMessage = () => {
        IO.touch(bar.toFile); Thread.sleep(5); IO.touch(foo.toFile)
        None
      }
    )
    watch(task, callbacks) shouldBe CancelWatch
    queue.toIndexedSeq shouldBe Seq(foo)
  }
  it should "enforce anti-entropy" in IO.withTemporaryDirectory { dir =>
    val realDir = dir.toRealPath
    val queue = new mutable.Queue[Path]
    val foo = realDir.toPath.resolve("foo")
    val bar = realDir.toPath.resolve("bar")
    val task = new Task
    val callbacks = TestDefaults.callbacks(
      inputs = Seq(realDir ** AllPassFilter),
      onStartWatch = () => if (task.getCount == 3) CancelWatch else Ignore,
      onWatchEvent = e => if (e.path != realDir.toPath) Trigger else Ignore,
      triggeredMessage = e => { queue += e.path; None },
      watchingMessage = () => {
        task.getCount match {
          case 1 => Files.createFile(bar)
          case 2 =>
            bar.toFile.setLastModified(5000)
            Files.createFile(foo)
          case _ =>
        }
        None
      }
    )
    watch(task, callbacks) shouldBe CancelWatch
    queue.toIndexedSeq shouldBe Seq(bar, foo)
  }
  it should "halt on error" in IO.withTemporaryDirectory { dir =>
    val exception = new IllegalStateException("halt")
    val task = new Task { override def apply(): Unit = throw exception }
    val callbacks = TestDefaults.callbacks(
      Seq(dir.toRealPath ** AllPassFilter),
    )
    watch(task, callbacks) shouldBe new HandleError(exception)
  }
  it should "reload" in IO.withTemporaryDirectory { dir =>
    val task = new Task
    val callbacks = TestDefaults.callbacks(
      inputs = Seq(dir.toRealPath ** AllPassFilter),
      onStartWatch = () => Ignore,
      onWatchEvent = _ => Reload,
      watchingMessage = () => { new File(dir, "file").createNewFile(); None }
    )
    watch(task, callbacks) shouldBe Reload
  }
}

object WatchSpec {
  implicit class FileOps(val f: File) {
    def toRealPath: File = f.toPath.toRealPath().toFile
  }
}
