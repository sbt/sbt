/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import org.scalasbt.shadedgson.com.google.gson.JsonObject
import sbt.internal.inc.CompileFailed
import sbt.util.Logger
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt
import scala.sys.process.Process
import scala.util.{ Failure, Success, Try }

object ProcessReactSpec extends verify.BasicTestSuite:
  private class FakeProcess(alive: Boolean, exit: Int) extends Process:
    def exitValue(): Int = exit
    def destroy(): Unit = ()
    override def isAlive(): Boolean = alive

  private class TestReact(id: Long, process: Process, failResponse: Boolean = false)
      extends ProcessReact[String](id, Logger.Null, process):
    def processResponse(o: JsonObject): Unit =
      if failResponse then throw IllegalStateException("boom")
      else promise.success(o.getAsJsonPrimitive("result").getAsString())
    def processNotification(o: JsonObject): Unit = ()
    def future: Future[String] = promise.future

  private def outcome[A1](f: Future[A1]): Try[A1] =
    Try(Await.result(f, 10.seconds))

  test("completes the promise from a matching response") {
    val react = TestReact(7, FakeProcess(alive = true, exit = 0))
    react("""{ "jsonrpc": "2.0", "result": "ok", "id": 7 }""")
    assert(outcome(react.future) == Success("ok"))
  }

  test("ignores responses for other request ids") {
    val react = TestReact(7, FakeProcess(alive = true, exit = 0))
    react("""{ "jsonrpc": "2.0", "result": "ok", "id": 8 }""")
    assert(!react.future.isCompleted)
  }

  test("fails with CompileFailed on error code 1009") {
    val react = TestReact(7, FakeProcess(alive = true, exit = 0))
    react("""{ "jsonrpc": "2.0", "error": { "code": 1009, "message": "bad" }, "id": 7 }""")
    assert(outcome(react.future) match
      case Failure(e: CompileFailed) => e.toString.contains("bad")
      case _                         => false)
  }

  test("survives an error object without a message field") {
    val react = TestReact(7, FakeProcess(alive = true, exit = 0))
    react("""{ "jsonrpc": "2.0", "error": { "code": 5 }, "id": 7 }""")
    assert(outcome(react.future) match
      case Failure(e) => e.getMessage.contains("code 5")
      case _          => false)
  }

  test("fails the promise when processResponse throws") {
    val react = TestReact(7, FakeProcess(alive = true, exit = 0), failResponse = true)
    react("""{ "jsonrpc": "2.0", "result": "ok", "id": 7 }""")
    assert(outcome(react.future) match
      case Failure(e: IllegalStateException) => e.getMessage == "boom"
      case _                                 => false)
  }

  test("notifyExit ignores other processes") {
    val react = TestReact(7, FakeProcess(alive = true, exit = 0))
    react.notifyExit(FakeProcess(alive = false, exit = 1))
    assert(!react.future.isCompleted)
  }

  test("notifyExit fails immediately on a nonzero exit code") {
    val process = FakeProcess(alive = false, exit = 2)
    val react = TestReact(7, process)
    react.notifyExit(process)
    assert(outcome(react.future) match
      case Failure(e) => e.getMessage.contains("exited with code 2")
      case _          => false)
  }

  test("notifyExit with exit code 0 waits for a late response") {
    val process = FakeProcess(alive = false, exit = 0)
    val react = TestReact(7, process)
    val t = Thread(() => {
      Thread.sleep(200)
      react("""{ "jsonrpc": "2.0", "result": "late", "id": 7 }""")
    })
    t.start()
    react.notifyExit(process)
    t.join()
    assert(outcome(react.future) == Success("late"))
  }

  test("notifyExit with exit code 0 eventually fails without a response") {
    val process = FakeProcess(alive = false, exit = 0)
    val react = TestReact(7, process)
    react.notifyExit(process)
    assert(outcome(react.future) match
      case Failure(e) => e.getMessage.contains("exited with code 0")
      case _          => false)
  }
end ProcessReactSpec
