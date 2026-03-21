/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import java.io.{ File, IOException }
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.util.Try
import sbt.io.IO
import sbt.internal.langserver.InitializeResult
import sbt.internal.protocol.*
import sjsonnew.{ JsonReader, JsonWriter }

/**
 * Public API for a client session with a running sbt server.
 *
 * Obtain an instance via [[ServerSession.connect]]:
 * {{{
 *   val session = ServerSession.connect(portfile)
 *   session.initialize(10.seconds, subscribeToAll = false)
 *   val result = session.sendJsonRpcAwaitResult[CompletionResponse]("sbt/completion", CompletionParams(""))
 *   session.shutdown(process.isAlive, () => process.destroy())
 * }}}
 */
trait ServerSession extends AutoCloseable {

  /** Allocates the next sequential JSON-RPC request ID (as a string). */
  def nextId(): String

  /** Returns `true` if the session is still actively reading from the socket. */
  def isRunning: Boolean

  /** Sends a JSON-RPC request, serializing `params` via its [[JsonWriter]]. */
  def sendJsonRpc[A: JsonWriter](id: String, method: String, params: A): Try[Unit]

  /** Sends a pre-built [[JsonRpcRequestMessage]]. */
  def sendJsonRpc(message: JsonRpcRequestMessage): Try[Unit]

  /** Sends a JSON-RPC notification serializing `params` via its [[JsonWriter]]. */
  def sendJsonRpcNotification[A: JsonWriter](method: String, params: A): Try[Unit]

  /** Sends a JSON-RPC response with the given `id` and `result`. */
  def sendJsonRpcResponse[A: JsonWriter](id: String, result: A): Try[Unit]

  /** Sends a JSON-RPC notification with raw JSON `params` string. */
  private[sbt] def sendJsonRpcNotificationRaw(method: String, params: String): Try[Unit]

  /** Sends a JSON-RPC request with raw JSON `params` string. */
  private[sbt] def sendJsonRpcRaw(id: String, method: String, params: String): Try[Unit]

  /** Sends a [[CommandMessage]] as a JSON-RPC request. */
  // TODO: probably should be refactored to fit JsonRpcRequest, but that's not so easy
  private[sbt] def sendCommand(command: CommandMessage): Try[Unit]

  /**
   * Sends a JSON-RPC request and waits for the typed result in a single call.
   *
   * The result type `R` must be specified explicitly; the params type `A` is
   * inferred from the argument:
   * {{{
   *   session.sendJsonRpcAwaitResult[CompletionResponse]("sbt/completion", CompletionParams(""))
   * }}}
   */
  def sendJsonRpcAwaitResult[R: JsonReader]: ServerSession.SendAwaitResult[R]

  /**
   * Performs the LSP `initialize` handshake with the sbt server.
   *
   * Sends an `initialize` request and blocks until the server responds with
   * an [[InitializeResult]]. Should be called exactly once after connecting.
   *
   * @param timeout        maximum time to wait for the server response
   * @param subscribeToAll whether this client subscribes to all build events
   */
  def initialize(timeout: FiniteDuration, subscribeToAll: Boolean): Try[InitializeResult]

  /** Waits for a [[JsonRpcResponseMessage]] matching the predicate. */
  def waitForResponseMsg(
      duration: FiniteDuration
  )(
      predicate: JsonRpcResponseMessage => Boolean
  ): Try[JsonRpcResponseMessage]

  /** Waits for a [[JsonRpcResponseMessage]] with the given request `id`. */
  def waitForResponseMsg(duration: FiniteDuration, id: String): Try[JsonRpcResponseMessage]

  /**
   * Waits for a response whose `result` field deserializes to `T` and matches
   * the predicate. Responses without a `result` field or whose result doesn't
   * deserialize are skipped.
   */
  def waitForResultInResponseMsg[T: JsonReader](
      duration: FiniteDuration
  )(
      predicate: T => Boolean
  ): Try[T]

  /**
   * Waits for a response with the given request `id` and extracts its
   * `result` as `T`. Returns `Failure` if the response has no `result` field.
   */
  def waitForResultInResponseMsg[T: JsonReader](duration: FiniteDuration, id: String): Try[T]

  /** Waits for a [[JsonRpcNotificationMessage]] matching the predicate. */
  def waitForNotificationMsg(
      duration: FiniteDuration
  )(
      predicate: JsonRpcNotificationMessage => Boolean
  ): Try[JsonRpcNotificationMessage]

  /**
   * Waits for a notification whose `params` field deserializes to `T` and
   * matches the predicate. Notifications without `params` or whose params
   * don't deserialize are skipped.
   */
  def waitForParamsInNotificationMsg[T: JsonReader](
      duration: FiniteDuration
  )(
      predicate: T => Boolean
  ): Try[T]

  /**
   * Gracefully shuts down the sbt server and closes this session.
   *
   * @param isAlive check whether the server process is still running
   * @param destroy forcefully terminate the server process
   */
  def shutdown(isAlive: => Boolean, destroy: () => Unit): Try[Unit]
}

object ServerSession {

  trait SendAwaitResult[R] {

    /** Sends a request and awaits the result using the default timeout. */
    def apply[A: JsonWriter](method: String, params: A): Try[R]

    /** Sends a request and awaits the result within the given `timeout`. */
    def apply[A: JsonWriter](method: String, params: A, timeout: FiniteDuration): Try[R]
  }

  private val PortfileTimeout: FiniteDuration = 1.minute
  private val PortfileLogInterval: FiniteDuration = 10.seconds

  /**
   * Connects to a running sbt server using the given portfile.
   *
   * @param portfile the `active.json` portfile created by the sbt server
   * @return a connected [[ServerSession]] ready for `initialize`
   */
  def connect(portfile: File): ServerSession = {
    val (socket, _) = ClientSocket.socket(portfile, false)
    new ServerSessionImpl(socket)
  }

  /** Waits for the portfile using the default timeout and no logging. */
  def waitForPortfile(portfile: File, isAlive: => Boolean): Unit =
    waitForPortfile(portfile, isAlive, PortfileTimeout, _ => ())

  /** Waits for the portfile with the given duration and no logging. */
  def waitForPortfile(portfile: File, isAlive: => Boolean, duration: FiniteDuration): Unit =
    waitForPortfile(portfile, isAlive, duration, _ => ())

  /**
   * Blocks until the sbt server portfile is created and non-empty.
   *
   * @param portfile the expected portfile path
   * @param isAlive  check whether the server process is still running
   * @param duration maximum time to wait
   * @param log      callback for progress messages
   * @throws TimeoutException if the portfile is not created within `duration`
   * @throws RuntimeException if the server process exits before the portfile appears
   */
  def waitForPortfile(
      portfile: File,
      isAlive: => Boolean,
      duration: FiniteDuration,
      log: String => Unit
  ): Unit = {
    def portfileIsEmpty(): Boolean =
      try IO.read(portfile).isEmpty
      catch { case _: IOException => true }

    log(s"Waiting up to $duration for sbt to be ready ...")

    val deadline = duration.fromNow
    var nextLog = PortfileLogInterval.fromNow

    while (portfileIsEmpty() && !deadline.isOverdue && isAlive) {
      if (nextLog.isOverdue) {
        log("Still waiting for sbt ...")
        nextLog = PortfileLogInterval.fromNow
      }
      Thread.sleep(10)
    }

    if (deadline.isOverdue)
      throw new TimeoutException(
        s"${portfile.getAbsolutePath} was not created within $duration"
      )

    if (!isAlive)
      throw new RuntimeException("sbt process unexpectedly terminated")
  }

}
