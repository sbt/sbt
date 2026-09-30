/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.server

import java.io.{ File, OutputStream }
import java.net.{ InetAddress, ServerSocket, Socket }
import sbt.{ State, StandardMain }
import sbt.internal.util.{ AttributeMap, ConsoleOut, GlobalLogging, MainAppender, Util }
import sbt.protocol.Serialization
import scala.jdk.CollectionConverters.*
import scala.util.Using
import verify.BasicTestSuite

object NetworkChannelSpec extends BasicTestSuite:

  test("only systemOut and systemErr are dropped while canceling") {
    assert(NetworkChannel.isCanceledOutput(Serialization.systemOut))
    assert(NetworkChannel.isCanceledOutput(Serialization.systemErr))
  }

  test("control-plane and flush methods are never dropped while canceling") {
    val kept = Seq(
      Serialization.systemOutFlush,
      Serialization.systemErrFlush,
      Serialization.readSystemIn,
      Serialization.promptChannel,
      "build/logMessage",
      "sbt/exec",
      "window/logMessage",
      sbt.BasicCommandStrings.Shutdown,
    )
    kept.foreach(m => assert(!NetworkChannel.isCanceledOutput(m), s"must not drop: $m"))
  }

  test("an interrupted thread can print to the client's STDOUT and stays interrupted"):
    withAttachedClient: channel =>
      val outcome = whileInterrupted(printLine(channel.terminal.outputStream, "out"))
      assertSucceededAndStillInterrupted(outcome)

  test("an interrupted thread can print to the client's STDERR and stays interrupted"):
    withAttachedClient: channel =>
      val outcome = whileInterrupted(printLine(channel.terminal.errorStream, "err"))
      assertSucceededAndStillInterrupted(outcome)

  test("an interrupted thread can publish bytes to the client and stays interrupted"):
    withAttachedClient: channel =>
      val outcome = whileInterrupted(channel.publishBytes("bytes".getBytes, delimit = true))
      assertSucceededAndStillInterrupted(outcome)

  private type Outcome = (thrown: Option[Exception], stillInterrupted: Boolean)

  private given Using.Releasable[NetworkChannel] = _.shutdown(false)

  private def whileInterrupted(action: => Unit): Outcome =
    Thread.currentThread().interrupt()
    try
      val thrown =
        try
          action
          None
        catch case e: Exception => Some(e)
      (thrown = thrown, stillInterrupted = Thread.interrupted())
    finally Util.ignoreResult(Thread.interrupted())

  private def assertSucceededAndStillInterrupted(outcome: Outcome): Unit =
    assert(outcome.thrown.isEmpty, s"expected no exception, but got ${outcome.thrown.orNull}")
    assert(outcome.stillInterrupted, "expected the interrupt flag to be kept, but it was cleared")

  private def printLine(stream: OutputStream, text: String): Unit =
    stream.write(s"$text\n".getBytes)
    stream.flush()

  private def withAttachedClient[A](test: NetworkChannel => A): A =
    withServerState:
      withChannelThreadsJoined:
        withLoopbackConnection: connection =>
          Using.resource(attachedChannel(connection))(test)

  private def withChannelThreadsJoined[A](f: => A): A =
    val before = liveThreads
    try f
    finally (liveThreads -- before).filter(isChannelThread).foreach(_.join(5000))

  private def liveThreads: Set[Thread] = Thread.getAllStackTraces.keySet.asScala.toSet

  private val channelName = "interrupt-test"
  
  private def isChannelThread(thread: Thread): Boolean =
    thread.getName.startsWith("sbt-networkchannel-") ||
      thread.getName.startsWith(s"sbt-$channelName-")

  private def attachedChannel(connection: Socket): NetworkChannel =
    val channel = new NetworkChannel(
      name = channelName,
      connection = connection,
      auth = Set.empty,
      instance = null,
      handlers = Nil,
      mkUIThreadImpl = (_, _) => null,
    )
    val attachRequestId = "attached-id"
    channel.setInteractive(attachRequestId, value = false)
    channel

  private def withLoopbackConnection[A](f: Socket => A): A =
    val loopback = InetAddress.getLoopbackAddress
    val anyFreePort = 0
    val backlog = 1
    Using.resource(new ServerSocket(anyFreePort, backlog, loopback)): server =>
      Using.resource(new Socket(loopback, server.getLocalPort)): _ =>
        Using.resource(server.accept())(f)

  private def withServerState[A](f: => A): A =
    val previous = StandardMain.exchange.withState(Option(_))
    if previous.isEmpty then StandardMain.exchange.setState(minimalState)
    try f
    finally StandardMain.exchange.setState(previous.orNull)

  private def minimalState: State =
    val logFile = File.createTempFile("network-channel-spec", ".log")
    logFile.deleteOnExit()
    State(
      configuration = null,
      definedCommands = Nil,
      exitHooks = Set.empty,
      onFailure = None,
      remainingCommands = Nil,
      history = State.newHistory,
      attributes = AttributeMap.empty,
      globalLogging = GlobalLogging.initial(
        MainAppender.globalDefault(ConsoleOut.globalProxy),
        logFile,
        ConsoleOut.globalProxy
      ),
      currentCommand = None,
      next = State.Continue,
    )

end NetworkChannelSpec
