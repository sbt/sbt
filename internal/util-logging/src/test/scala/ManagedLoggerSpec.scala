/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import verify.BasicTestSuite
import sbt.util.*
import sbt.internal.util.appmacro.StringTypeTag
import java.io.{ File, PrintWriter }
import sbt.io.Using

object ManagedLoggerSpec extends BasicTestSuite:
  val context: LoggerContext = LoggerContext()
  // TODO create a new appender for testing purposes - 3/12/21
  val asyncStdout: Appender = ConsoleAppender()
  def newLogger(name: String): ManagedLogger = context.logger(name, None, None)

  test("ManagedLogger should log to console"):
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    log.info("test_info")
    log.debug("test_debug")

  test("ManagedLogger should support event logging"):
    import sjsonnew.BasicJsonProtocol.*
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    log.infoEvent(1)

  test(
    "ManagedLogger should validate performance improvement of disabling location calculation for async loggers"
  ):
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    val before = System.currentTimeMillis()
    1 to 10000 foreach { _ =>
      log.debug("test")
    }
    val after = System.currentTimeMillis()
    log.info(s"Performance test took: ${after - before}ms")

  test("ManagedLogger should support logging Throwable out of the box"):
    import sbt.internal.util.codec.JsonProtocol.given
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    log.infoEvent(SuccessEvent("yes"))

  test("ManagedLogger should allow registering Show[Int]"):
    import sjsonnew.BasicJsonProtocol.given
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    given ShowLines[Int] =
      ShowLines((x: Int) => Vector(s"String representation of $x"))
    log.registerStringCodec[Int]
    log.infoEvent(1)

  test("ManagedLogger should allow registering Show[Array[Int]]"):
    import sjsonnew.BasicJsonProtocol.given
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    given ShowLines[Array[Int]] =
      ShowLines((x: Array[Int]) => Vector(s"String representation of ${x.mkString}"))
    log.registerStringCodec[Array[Int]]
    log.infoEvent(Array(1, 2, 3))

  test("ManagedLogger should allow registering Show[Vector[Vector[Int]]]"):
    import sjsonnew.BasicJsonProtocol.given
    val log = newLogger("foo")
    context.addAppender("foo", asyncStdout -> Level.Info)
    given ShowLines[Vector[Vector[Int]]] =
      ShowLines((xss: Vector[Vector[Int]]) => Vector(s"String representation of $xss"))
    log.registerStringCodec[Vector[Vector[Int]]]
    log.infoEvent(Vector(Vector(1, 2, 3)))

  test("ManagedLogger should be thread safe"):
    import java.util.concurrent.{ Executors, TimeUnit }
    val pool = Executors.newFixedThreadPool(100)
    for i <- 1 to 10000 do
      pool.submit((() =>
        val stringTypeTag = implicitly[StringTypeTag[List[Int]]]
        val log = newLogger(s"foo$i")
        context.addAppender(s"foo$i", asyncStdout -> Level.Info)
        if i % 100 == 0 then log.info(s"foo$i test $stringTypeTag")
        Thread.sleep(1)
      ): Runnable)
    pool.shutdown
    pool.awaitTermination(30, TimeUnit.SECONDS)
    ()

  test("global logging should log immediately after initialization"):
    // this is passed into State normally
    val global0 = initialGlobalLogging
    val full = global0.full
    (1 to 3).toList foreach { x =>
      full.info(s"test$x")
    }

  // This is done in Mainloop.scala
  test("global logging should create a new backing with newAppender"):
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

  val console: ConsoleOut = ConsoleOut.systemOut
  def initialGlobalLogging: GlobalLogging = GlobalLogging.initial(
    MainAppender.globalDefault(console),
    File.createTempFile("sbt", ".log"),
    console
  )
end ManagedLoggerSpec
