/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import java.io.IOException
import java.net.{ Socket, SocketTimeoutException }
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeoutException
import scala.annotation.tailrec
import scala.concurrent.duration.*
import sbt.internal.langserver.{ InitializeParams, InitializeResult, SbtExecParams }
import sbt.protocol.codec.JsonProtocol.given
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.internal.protocol.codec.JsonRPCProtocol.given
import sbt.internal.protocol.*
import sbt.internal.util.JoinThread.*
import sjsonnew.{ JsonReader, JsonWriter }
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }
import scala.util.{ Failure, Success, Try }
import java.util.UUID

/**
 * Internal implementation of the [[ServerSession]] trait.
 *
 * Manages a background read thread that deserializes incoming JSON-RPC
 * frames and dispatches them to [[onRequest]], [[onResponse]],
 * [[onNotification]], or [[onInvalidFrame]] callbacks. Responses and
 * notifications are enqueued into typed queues for the blocking
 * `waitFor*` methods.
 *
 * Instances are created exclusively via [[ServerSession.connect]].
 *
 * @param socket     the connected socket
 * @param threadName name for the background read thread
 *
 * @note Thread-safe for sending, but only one thread should consume
 *       from `waitFor*` methods at a time, since non-matching messages
 *       are discarded during polling.
 */
private[sbt] class ServerSessionImpl(
    socket: Socket,
    threadName: String = "sbt-server-session-read-thread"
) extends ServerSession {

  /** Controls the read loop; set to `false` to stop reading from the socket. */
  private val running = new AtomicBoolean(true)

  /** Guards [[close]] idempotency — ensures cleanup runs exactly once. */
  private val closed = new AtomicBoolean(false)

  /** Output stream for sending messages. Protected for subclass access. */
  protected final val out = socket.getOutputStream

  private val responses = new LinkedBlockingQueue[JsonRpcResponseMessage]
  private val notifications = new LinkedBlockingQueue[JsonRpcNotificationMessage]

  /** Called when the read thread receives a [[JsonRpcRequestMessage]]. */
  protected def onRequest(msg: JsonRpcRequestMessage): Unit = ()

  /**
   * Called when the read thread receives a [[JsonRpcResponseMessage]].
   * Default enqueues into the responses queue for `waitForResponseMsg`.
   */
  protected def onResponse(msg: JsonRpcResponseMessage): Unit = responses.put(msg)

  /**
   * Called when the read thread receives a [[JsonRpcNotificationMessage]].
   * Default enqueues into the notifications queue for `waitForNotificationMsg`.
   */
  protected def onNotification(msg: JsonRpcNotificationMessage): Unit = notifications.put(msg)

  /** Called when the read thread receives a frame that cannot be deserialized. */
  protected def onInvalidFrame(frame: Seq[Byte], errorDesc: String): Unit = ()

  /**
   * Called exactly once during [[close]], after the socket and output stream
   * have been closed but before the read thread is joined.
   */
  protected def onClose(): Unit = ()

  /**
   * Background thread that continuously reads JSON-RPC frames from the socket.
   * Each frame is deserialized and dispatched to [[onRequest]], [[onResponse]],
   * [[onNotification]], or [[onInvalidFrame]].
   */
  private val readThread = new Thread(threadName) {
    setDaemon(true)
    override def run(): Unit = {
      try {
        val in = socket.getInputStream
        socket.setSoTimeout(ServerSessionImpl.ReadIoTimeout)
        while (running.get) {
          try {
            val frame = JsonRpcReader.read(in, running, onHeader = None)
            if (running.get) {
              Serialization
                .deserializeJsonMessage(frame)
                .fold(
                  errorDesc => onInvalidFrame(frame, errorDesc),
                  _ match {
                    case msg: JsonRpcRequestMessage      => onRequest(msg)
                    case msg: JsonRpcResponseMessage     => onResponse(msg)
                    case msg: JsonRpcNotificationMessage => onNotification(msg)
                  }
                )
            }
          } catch {
            case _: SocketTimeoutException => // re-check running
            case _: IOException            => running.set(false)
          }
        }
      } finally {
        close()
      }
    }
  }
  readThread.start()

  /** Returns `true` if the session is still actively reading from the socket. */
  final override def isRunning: Boolean = running.get

  /**
   * Closes the socket connection and stops the read thread.
   *
   * Idempotent — safe to call multiple times. Calls [[onClose]] after closing
   * I/O and before joining the read thread. When called from the read thread
   * itself (via the `finally` block), skips the thread join to avoid deadlock.
   */
  override def close(): Unit = if (closed.compareAndSet(false, true)) {
    running.set(false)
    try
      out.close()
      socket.close()
    catch case _: IOException => ()
    onClose()
    if Thread.currentThread() != readThread then
      try readThread.joinFor(ServerSessionImpl.ReadThreadDestroyTimeout)
      catch case _: TimeoutException => ()
  }

  override def nextId(): String = UUID.randomUUID.toString

  override def sendJsonRpc[A: JsonWriter](id: String, method: String, params: A): Try[Unit] =
    for {
      converted <- Converter.toJson(params)
      _ <- sendJsonRpc(JsonRpcRequestMessage("2.0", id, method, converted))
    } yield ()

  override def sendJsonRpc(message: JsonRpcRequestMessage): Try[Unit] =
    for {
      converted <- Converter.toJson(message)
      _ <- sendJsonRpcRaw(CompactPrinter(converted))
    } yield ()

  override def sendJsonRpcNotification[A: JsonWriter](method: String, params: A): Try[Unit] =
    for {
      converted <- Converter.toJson(params)
      _ <- sendJsonRpcRaw(
        CompactPrinter(
          Converter.toJson(JsonRpcNotificationMessage("2.0", method, converted)).get
        )
      )
    } yield ()

  override def sendJsonRpcResponse[A: JsonWriter](id: String, result: A): Try[Unit] =
    for {
      convertedResult <- Converter.toJson(result)
      convertedResponse <- Converter.toJson(
        JsonRpcResponseMessage("2.0", id, Some(convertedResult), None)
      )
      _ <- sendJsonRpcRaw(CompactPrinter(convertedResponse))
    } yield ()

  override def sendJsonRpcRaw(id: String, method: String, params: String): Try[Unit] =
    sendJsonRpcRaw(Serialization.serializeJsonRpcRequest(id, method, params))

  override def sendJsonRpcNotificationRaw(method: String, params: String): Try[Unit] =
    sendJsonRpcRaw(Serialization.serializeJsonRpcNotification(method, params))

  override def sendCommand(command: CommandMessage): Try[Unit] =
    sendJsonRpcRaw(Serialization.serializeCommandAsJsonMessage(command))

  /** Sends a raw JSON-RPC message string over the wire. */
  private def sendJsonRpcRaw(message: String): Try[Unit] =
    Try(JsonRpcWriter.write(out, message))

  override def sendJsonRpcAwaitResult[R: JsonReader]: ServerSession.SendAwaitResult[R] =
    new ServerSession.SendAwaitResult[R] {
      override def apply[A: JsonWriter](method: String, params: A): Try[R] =
        apply(method, params, ServerSessionImpl.ResponseTimeout)

      override def apply[A: JsonWriter](
          method: String,
          params: A,
          timeout: FiniteDuration
      ): Try[R] = {
        val id = nextId()
        for {
          _ <- sendJsonRpc(id, method, params)
          result <- waitForResultInResponseMsg[R](timeout, id)
        } yield result
      }
    }

  override def initialize(
      timeout: FiniteDuration,
      subscribeToAll: Boolean
  ): Try[InitializeResult] =
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

  /**
   * Polls a typed queue for a message that `f` maps to `Some`, discarding
   * messages where `f` returns `None`. Returns `Failure(TimeoutException)`
   * if no match is found within `duration`.
   *
   * @note Non-matching messages are consumed and lost. Only one thread
   *       should poll at a time.
   */
  private def pollUntil[A, B](
      queue: LinkedBlockingQueue[A],
      duration: FiniteDuration
  )(f: A => Option[B]): Try[B] = Try {
    val deadline = duration.fromNow
    @tailrec
    def impl(): B =
      Option(queue.poll(deadline.timeLeft.toMillis, TimeUnit.MILLISECONDS)) match {
        case None =>
          throw new TimeoutException(s"Timeout waiting for response after $duration")
        case Some(msg) =>
          f(msg) match {
            case Some(result) => result
            case None =>
              if (deadline.isOverdue())
                throw new TimeoutException(s"Timeout waiting for response after $duration")
              else impl()
          }
      }
    impl()
  }

  override def waitForResponseMsg(
      duration: FiniteDuration
  )(
      predicate: JsonRpcResponseMessage => Boolean
  ): Try[JsonRpcResponseMessage] =
    pollUntil(responses, duration)(msg => Option.when(predicate(msg))(msg))

  override def waitForResponseMsg(
      duration: FiniteDuration,
      id: String
  ): Try[JsonRpcResponseMessage] =
    waitForResponseMsg(duration)(_.id == id)

  override def waitForResultInResponseMsg[T: JsonReader](
      duration: FiniteDuration
  )(
      predicate: T => Boolean
  ): Try[T] =
    pollUntil(responses, duration) { msg =>
      for {
        result <- msg.result
        value <- Converter.fromJson[T](result).toOption
        if predicate(value)
      } yield value
    }

  override def waitForResultInResponseMsg[T: JsonReader](
      duration: FiniteDuration,
      id: String
  ): Try[T] =
    waitForResponseMsg(duration, id).flatMap { response =>
      response.result match {
        case Some(r) => Converter.fromJson[T](r)
        case None    => Failure(new RuntimeException(s"Response has no result: $response"))
      }
    }

  override def waitForNotificationMsg(
      duration: FiniteDuration
  )(
      predicate: JsonRpcNotificationMessage => Boolean
  ): Try[JsonRpcNotificationMessage] =
    pollUntil(notifications, duration)(msg => Option.when(predicate(msg))(msg))

  override def waitForParamsInNotificationMsg[T: JsonReader](
      duration: FiniteDuration
  )(
      predicate: T => Boolean
  ): Try[T] =
    pollUntil(notifications, duration) { msg =>
      for {
        params <- msg.params
        value <- Converter.fromJson[T](params).toOption
        if predicate(value)
      } yield value
    }

  override def shutdown(isAlive: => Boolean, destroy: () => Unit): Try[Unit] = {
    def waitForExit(isAlive: => Boolean, timeout: FiniteDuration): Unit = {
      val deadline = timeout.fromNow
      while (!deadline.isOverdue() && isAlive) Thread.sleep(10)
    }

    val result = for {
      _ <- sendJsonRpc(nextId(), "sbt/exec", SbtExecParams("shutdown"))
      _ = waitForExit(isAlive, ServerSessionImpl.GracefulShutdownTimeout)
      _ = if (isAlive) {
        destroy()
        waitForExit(isAlive, ServerSessionImpl.DestroyTimeout)
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

private[sbt] object ServerSessionImpl {
  val ReadIoTimeout: Int = 5000 // ms
  val ReadThreadDestroyTimeout: FiniteDuration = 1.seconds
  val ResponseTimeout: FiniteDuration = 1.minutes
  val GracefulShutdownTimeout: FiniteDuration = 5.seconds
  val DestroyTimeout: FiniteDuration = 10.seconds
}
