/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import java.net.Socket
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeoutException
import scala.annotation.tailrec
import scala.concurrent.duration.*
import sbt.internal.langserver.{ InitializeParams, InitializeResult, SbtExecParams }
import sbt.protocol.codec.JsonProtocol.given
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.internal.protocol.codec.JsonRPCProtocol.given
import sbt.internal.protocol.*
import sjsonnew.{ JsonReader, JsonWriter }
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }
import scala.util.{ Failure, Success, Try }

/**
 * High-level client session for communicating with a running sbt server.
 *
 * Extends [[ServerSession]] with an inbox queue, typed send/wait methods,
 * `initialize` handshake, and graceful `shutdown`.
 *
 * Obtain an instance via [[ServerSession.connect]]:
 * {{{
 *   val session = ServerSession.connect(portfile)
 *   session.initialize()
 *   val result = session.sendJsonRpcAwaitResult[CompletionResponse]("sbt/completion", CompletionParams(""))
 *   session.shutdown(process.isAlive, () => process.destroy())
 * }}}
 *
 * @note Thread-safe for sending, but only one thread should consume from `waitFor*` at a time.
 */
final class ServerSessionClient(socket: Socket)
    extends ServerSession(socket, "sbt-server-session-read-thread") {

  /** Incoming frames read by the background thread, consumed by [[pollUntil]]. */
  private val inbox = new LinkedBlockingQueue[Seq[Byte]]

  private val requestId = new AtomicInteger(1)

  override protected def onFrame(frame: Seq[Byte]): Unit = inbox.put(frame)

  /** Allocates the next sequential JSON-RPC request ID. */
  def nextId(): Int = requestId.getAndIncrement()

  /** Sends a raw JSON-RPC message string. */
  private[sbt] def sendJsonRpc(message: String): Try[Unit] = Try(JsonRpcWriter.write(out, message))

  /** Sends a pre-built [[JsonRpcRequestMessage]]. */
  def sendJsonRpc(message: JsonRpcRequestMessage): Try[Unit] =
    sendJsonRpc(CompactPrinter(Converter.toJson(message).get))

  /** Sends a JSON-RPC request with raw JSON `params` string. */
  private[sbt] def sendJsonRpc(id: Int, method: String, params: String): Try[Unit] =
    for {
      parsed <- Parser.parseFromString(params)
      _ <- sendJsonRpc(JsonRpcRequestMessage("2.0", id.toString, method, parsed))
    } yield ()

  /** Sends a JSON-RPC request, serializing `params` via its [[JsonWriter]]. */
  def sendJsonRpc[A: JsonWriter](id: Int, method: String, params: A): Try[Unit] =
    for {
      converted <- Converter.toJson(params)
      _ <- sendJsonRpc(JsonRpcRequestMessage("2.0", id.toString, method, converted))
    } yield ()

  /**
   * Sends a JSON-RPC request and waits for the typed result in a single call.
   *
   * The result type `R` must be specified explicitly; the params type `A` is
   * inferred from the argument:
   * {{{
   *   session.sendJsonRpcAwaitResult[CompletionResponse]("sbt/completion", CompletionParams(""))
   * }}}
   */
  def sendJsonRpcAwaitResult[R: JsonReader]: SendAwaitResult[R] = SendAwaitResult[R]()

  /**
   * Intermediate class enabling partial type application for [[sendJsonRpcAwaitResult]].
   * Captures the result type `R` and infers the params type `A` from the call site.
   */
  final class SendAwaitResult[R: JsonReader] {

    /** Sends a request and awaits the result using [[ServerSession.ResponseTimeout]]. */
    def apply[A: JsonWriter](method: String, params: A): Try[R] =
      apply(method, params, ServerSession.ResponseTimeout)

    /** Sends a request and awaits the result within the given `timeout`. */
    def apply[A: JsonWriter](method: String, params: A, timeout: FiniteDuration): Try[R] = {
      val id = nextId()
      for {
        _ <- sendJsonRpc(id, method, params)
        result <- waitForResultInResponseMsg[R](timeout, id)
      } yield result
    }
  }

  /** Initializes with default timeout and subscribes to all events. */
  def initialize(): Try[InitializeResult] =
    initialize(ServerSession.InitializeTimeout, subscribeToAll = true)

  /** Initializes with default timeout and the given subscription preference. */
  def initialize(subscribeToAll: Boolean): Try[InitializeResult] =
    initialize(ServerSession.InitializeTimeout, subscribeToAll)

  /**
   * Performs the LSP `initialize` handshake with the sbt server.
   *
   * Sends an `initialize` request and blocks until the server responds with
   * an [[InitializeResult]]. Should be called exactly once after connecting.
   *
   * @param timeout       maximum time to wait for the server response
   * @param subscribeToAll whether this client subscribes to all build events
   */
  def initialize(timeout: FiniteDuration, subscribeToAll: Boolean): Try[InitializeResult] = {
    for {
      options <- Converter
        .toJson(
          InitializeOption(
            token = None,
            skipAnalysis = Some(true),
            canWork = Some(true),
            subscribeToAll = Some(subscribeToAll)
          )
        )
      params = InitializeParams(
        processId = None,
        rootPath = None,
        rootUri = None,
        initializationOptions = Some(options),
        capabilities = None,
        trace = None
      )
      id = nextId()
      result <- sendJsonRpcAwaitResult[InitializeResult]("initialize", params, timeout)
    } yield result
  }

  /**
   * Polls [[inbox]] for a message matching `f`, discarding non-matching messages.
   *
   * This is the core blocking primitive — all public `waitFor*` methods delegate here.
   * Returns `Failure(TimeoutException)` if no match is found within `duration`.
   *
   * @note Non-matching messages are consumed and lost. Only one thread should
   *       poll at a time.
   */
  private def pollUntil[A](duration: FiniteDuration)(f: String => Option[A]): Try[A] = Try {
    val deadline = duration.fromNow
    @tailrec
    def impl(): A = {
      Option(inbox.poll(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)) match {
        case None =>
          throw new TimeoutException(s"Timeout waiting for response after $duration")
        case Some(frame) =>
          f(new String(frame.toArray, "UTF-8")) match {
            case Some(a) => a
            case None =>
              if (deadline.isOverdue)
                throw new TimeoutException(s"Timeout waiting for response after $duration")
              else impl()
          }
      }
    }
    impl()
  }

  /** Waits for a raw message string matching the predicate. */
  private[sbt] def waitForRawResponse(duration: FiniteDuration)(
      predicate: String => Boolean
  ): Try[String] =
    pollUntil(duration)(s => Option.when(predicate(s))(s))

  /** Waits for a [[JsonRpcResponseMessage]] matching the predicate. */
  def waitForResponseMsg(
      duration: FiniteDuration
  )(
      predicate: JsonRpcResponseMessage => Boolean
  ): Try[JsonRpcResponseMessage] =
    pollUntil(duration) { s =>
      Parser
        .parseFromString(s)
        .flatMap(Converter.fromJson[JsonRpcResponseMessage](_))
        .toOption
        .filter(predicate)
    }

  /** Waits for a [[JsonRpcResponseMessage]] with the given request `id`. */
  def waitForResponseMsg(
      duration: FiniteDuration,
      id: Int
  ): Try[JsonRpcResponseMessage] =
    waitForResponseMsg(duration)(_.id == id.toString)

  /**
   * Waits for a response whose `result` field deserializes to `T` and matches the predicate.
   * Responses without a `result` field or whose result doesn't deserialize are skipped.
   */
  def waitForResultInResponseMsg[T: JsonReader](
      duration: FiniteDuration
  )(
      predicate: T => Boolean
  ): Try[T] =
    pollUntil(duration) { s =>
      for {
        json <- Parser.parseFromString(s).toOption
        response <- Converter.fromJson[JsonRpcResponseMessage](json).toOption
        result <- response.result
        value <- Converter.fromJson[T](result).toOption
        if predicate(value)
      } yield value
    }

  /**
   * Waits for a response with the given request `id` and extracts its `result` as `T`.
   * Returns `Failure` if the response has no `result` field.
   */
  def waitForResultInResponseMsg[T: JsonReader](
      duration: FiniteDuration,
      id: Int
  ): Try[T] =
    waitForResponseMsg(duration, id).flatMap { response =>
      response.result match {
        case Some(r) => Converter.fromJson[T](r)
        case None    => Failure(new RuntimeException(s"Response has no result: $response"))
      }
    }

  /** Waits for a [[JsonRpcNotificationMessage]] matching the predicate. */
  def waitForNotificationMsg(
      duration: FiniteDuration
  )(
      predicate: JsonRpcNotificationMessage => Boolean
  ): Try[JsonRpcNotificationMessage] =
    pollUntil(duration) { s =>
      Parser
        .parseFromString(s)
        .flatMap(Converter.fromJson[JsonRpcNotificationMessage](_))
        .toOption
        .filter(predicate)
    }

  /**
   * Waits for a notification whose `params` field deserializes to `T` and matches the predicate.
   * Notifications without `params` or whose params don't deserialize are skipped.
   */
  def waitForParamsInNotificationMsg[T: JsonReader](
      duration: FiniteDuration
  )(
      predicate: T => Boolean
  ): Try[T] =
    pollUntil(duration) { s =>
      for {
        json <- Parser.parseFromString(s).toOption
        notification <- Converter.fromJson[JsonRpcNotificationMessage](json).toOption
        params <- notification.params
        value <- Converter.fromJson[T](params).toOption
        if predicate(value)
      } yield value
    }

  /**
   * Gracefully shuts down the sbt server and closes this session.
   *
   * Sends a `shutdown` command via `sbt/exec`, waits for the process to exit
   * within [[ServerSession.GracefulShutdownTimeout]], and calls `destroy()` if it
   * hasn't exited. Always calls [[close]] afterward (even on failure).
   *
   * @param isAlive check whether the server process is still running
   * @param destroy forcefully terminate the server process
   */
  def shutdown(isAlive: => Boolean, destroy: () => Unit): Try[Unit] = {
    def waitForExit(isAlive: => Boolean, timeout: FiniteDuration): Unit = {
      val deadline = timeout.fromNow
      while (!deadline.isOverdue && isAlive) Thread.sleep(10)
    }

    val result = for {
      _ <- sendJsonRpc(nextId(), "sbt/exec", SbtExecParams("shutdown"))
      _ = waitForExit(isAlive, ServerSession.GracefulShutdownTimeout)
      _ = if (isAlive) {
        destroy()
        waitForExit(isAlive, ServerSession.DestroyTimeout)
      }

      _ <-
        if (isAlive) {
          Failure(new IllegalStateException("sbt process failed to exit"))
        } else {
          Success(())
        }
    } yield ()

    close()

    result
  }
}
