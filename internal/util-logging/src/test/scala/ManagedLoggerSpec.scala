package sbt.internal.util

import org.scalatest._
import sbt.util._
import java.io.{ File, PrintWriter }
import sbt.io.Using

class ManagedLoggerSpec extends FlatSpec with Matchers {
  "ManagedLogger" should "log to console" in {
    val log = LogExchange.logger("foo")
    LogExchange.bindLoggerAppenders("foo", List(LogExchange.asyncStdout -> Level.Info))
    log.info("test")
    log.debug("test")
  }

  "global logging" should "log immediately after initialization" in {
    // this is passed into State normally
    val global0 = initialGlobalLogging
    val full = global0.full
    (1 to 3).toList foreach { x => full.info(s"test$x") }
  }

  // This is done in Mainloop.scala
  it should "create a new backing with newAppender" in {
    val global0 = initialGlobalLogging
    val logBacking0 = global0.backing
    val global1 = Using.fileWriter(append = true)(logBacking0.file) { writer =>
      val out = new PrintWriter(writer)
      val g = global0.newAppender(global0.full, out, logBacking0)
      val full = g.full
      (1 to 3).toList foreach { x => full.info(s"newAppender $x") }
      assert(logBacking0.file.exists)
      g
    }
    val logBacking1 = global1.backing
    Using.fileWriter(append = true)(logBacking1.file) { writer =>
      val out = new PrintWriter(writer)
      val g = global1.newAppender(global1.full, out, logBacking1)
      val full = g.full
      (1 to 3).toList foreach { x => full.info(s"newAppender $x") }
      // println(logBacking.file)
      // print("Press enter to continue. ")
      // System.console.readLine
      assert(logBacking1.file.exists)
    }
  }

  val console = ConsoleOut.systemOut
  def initialGlobalLogging: GlobalLogging = GlobalLogging.initial(
    MainAppender.globalDefault(console), File.createTempFile("sbt", ".log"), console
  )
}
