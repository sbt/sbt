/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sbt.util._
import sbt.internal.util.appmacro.StringTypeTag
import java.io.{ File, PrintWriter }
import sbt.io.Using

class ManagedLoggerSpec extends AnyFlatSpec with Matchers {
  val context = LoggerContext()
  // TODO create a new appender for testing purposes - 3/12/21
  val asyncStdout = ConsoleAppender()
  def newLogger(name: String): ManagedLogger = context.logger(name, None, None)
  "ManagedLogger" should "log to console" in {
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    log.info("test_info")
    log.debug("test_debug")
  }

  it should "support event logging" in {
    import sjsonnew.BasicJsonProtocol._
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    log.infoEvent(1)
  }

  it should "validate performance improvement of disabling location calculation for async loggers" in {
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    val before = System.currentTimeMillis()
    1 to 10000 foreach { _ =>
      log.debug("test")
    }
    val after = System.currentTimeMillis()

    log.info(s"Peformance test took: ${after - before}ms")
  }

  it should "support logging Throwable out of the box" in {
    import sbt.internal.util.codec.JsonProtocol._
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    log.infoEvent(SuccessEvent("yes"))
  }

  it should "allow registering Show[Int]" in {
    import sjsonnew.BasicJsonProtocol._
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    given ShowLines[Int] =
      ShowLines((x: Int) => Vector(s"String representation of $x"))
    log.registerStringCodec[Int]
    log.infoEvent(1)
  }

  it should "allow registering Show[Array[Int]]" in {
    import sjsonnew.BasicJsonProtocol._
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    given ShowLines[Array[Int]] =
      ShowLines((x: Array[Int]) => Vector(s"String representation of ${x.mkString}"))
    log.registerStringCodec[Array[Int]]
    log.infoEvent(Array(1, 2, 3))
  }

  it should "allow registering Show[Vector[Vector[Int]]]" in {
    import sjsonnew.BasicJsonProtocol._
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    given ShowLines[Vector[Vector[Int]]] =
      ShowLines((xss: Vector[Vector[Int]]) => Vector(s"String representation of $xss"))
    log.registerStringCodec[Vector[Vector[Int]]]
    log.infoEvent(Vector(Vector(1, 2, 3)))
  }

  it should "be thread safe" in {
    import java.util.concurrent.{ Executors, TimeUnit }
    val pool = Executors.newFixedThreadPool(100)
    for {
      i <- 1 to 10000
    } {
      pool.submit(new Runnable {
        def run(): Unit = {
          val stringTypeTag = implicitly[StringTypeTag[List[Int]]]
          val log = newLogger(s"foo$i")
          context.addAppender(s"foo$i", asyncStdout -> Level.Info)
          if (i % 100 == 0) {
            log.info(s"foo$i test $stringTypeTag")
          }
          Thread.sleep(1)
        }
      })
    }
    pool.shutdown
    pool.awaitTermination(30, TimeUnit.SECONDS)
  }

  "global logging" should "log immediately after initialization" in {
    // this is passed into State normally
    val global0 = initialGlobalLogging
    val full = global0.full
    (1 to 3).toList foreach { x =>
      full.info(s"test$x")
    }
  }

  // This is done in Mainloop.scala
  it should "create a new backing with newAppender" in {
    val global0 = initialGlobalLogging
    val logBacking0 = global0.backing
    val global1 = Using.fileWriter(append = true)(logBacking0.file) { writer =>
      val out = new PrintWriter(writer)
      val g = global0.newAppender(global0.full, out, logBacking0, context)
      val full = g.full
      (1 to 3).toList foreach (x => full.info(s"newAppender $x"))
      assert(logBacking0.file.exists)
      g
    }
    val logBacking1 = global1.backing
    Using.fileWriter(append = true)(logBacking1.file) { writer =>
      val out = new PrintWriter(writer)
      val g = global1.newAppender(global1.full, out, logBacking1, context)
      val full = g.full
      (1 to 3).toList foreach (x => full.info(s"newAppender $x"))
      // println(logBacking.file)
      // print("Press enter to continue. ")
      // System.console.readLine
      assert(logBacking1.file.exists)
    }
  }

  val console = ConsoleOut.systemOut
  def initialGlobalLogging: GlobalLogging = GlobalLogging.initial(
    MainAppender.globalDefault(console),
    File.createTempFile("sbt", ".log"),
    console
  )
}
