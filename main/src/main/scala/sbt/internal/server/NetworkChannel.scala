/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.io.{ IOException, InputStream, OutputStream }
import java.net.{ Socket, SocketTimeoutException }
import java.util.concurrent.{
  ConcurrentHashMap,
  Executors,
  LinkedBlockingQueue,
  RejectedExecutionException,
  TimeUnit
}
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import sbt.BasicCommandStrings.{ Shutdown, TerminateAction }
import sbt.ProjectExtra.extract
import sbt.SlashSyntax0.given
import sbt.internal.langserver.{ CancelRequestParams, ErrorCodes, LogMessageParams, MessageType }
import sbt.internal.protocol.{
  JsonRpcNotificationMessage,
  JsonRpcRequestMessage,
  JsonRpcResponseError,
  JsonRpcResponseMessage
}

import sbt.internal.ui.{ UITask, UserThread }
import sbt.internal.util.{ Prompt, ReadJsonFromInputStream, Terminal, Util }
import sbt.internal.util.Terminal.TerminalImpl
import sbt.internal.util.complete.{ Parser, Parsers }
import sbt.util.Logger

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal
import sbt.protocol._
import sbt.protocol.Serialization.{ attach, cancelReadSystemIn, readSystemIn, promptChannel }

import sbt.protocol.codec.JsonProtocol._

import sjsonnew._
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import sbt.internal.util.ProgressState

final class NetworkChannel(
    val name: String,
    connection: Socket,
    auth: Set[ServerAuthentication],
    instance: ServerInstance,
    handlers: Seq[ServerHandler],
    mkUIThreadImpl: (State, CommandChannel) => UITask,
) extends CommandChannel { self =>
  def this(
      name: String,
      connection: Socket,
      structure: BuildStructure,
      auth: Set[ServerAuthentication],
      instance: ServerInstance,
      handlers: Seq[ServerHandler],
      log: Logger
  ) =
    this(
      name,
      connection,
      auth,
      instance,
      handlers,
      new UITask.AskUserTask(_, _),
    )

  def log: Logger = StandardMain.exchange.withState(_.log)

  private val running = new AtomicBoolean(true)
  private val delimiter: Byte = '\n'.toByte
  private val out = connection.getOutputStream
  private var initialized = false
  private val pendingRequests: mutable.Map[String, JsonRpcRequestMessage] = mutable.Map()

  private val inputBuffer = new LinkedBlockingQueue[Int]()
  private val pendingWrites = new LinkedBlockingQueue[(Array[Byte], Boolean)]()
  private val attached = new AtomicBoolean(false)
  private val alive = new AtomicBoolean(true)
  private[sbt] def isInteractive = interactive.get
  private val interactive = new AtomicBoolean(false)
  private[sbt] def setInteractive(id: String, value: Boolean) = {
    terminalHolder.getAndSet(new NetworkTerminal) match {
      case null =>
      case t    => t.close()
    }
    interactive.set(value)
    if (isInteractive) terminal.setPrompt(Prompt.Pending)
    attached.set(true)
    pendingRequests.remove(id)
    jsonRpcRespond("", id)
    addFastTrackTask(attach)
  }
  private[sbt] def prompt(): Unit = {
    interactive.set(true)
    jsonRpcNotify(promptChannel, "")
  }
  private[sbt] def write(byte: Byte) = inputBuffer.add(byte.toInt)

  private val terminalHolder = new AtomicReference[Terminal](Terminal.NullTerminal)
  override private[sbt] def terminal: Terminal = terminalHolder.get
  override val userThread: UserThread = new UserThread(this)

  private lazy val callback: ServerCallback = new ServerCallback {
    def jsonRpcRespond[A: JsonFormat](event: A, execId: Option[String]): Unit =
      self.respondResult(event, execId)

    def jsonRpcRespondError(execId: Option[String], code: Long, message: String): Unit =
      self.respondError(code, message, execId)

    def jsonRpcNotify[A: JsonFormat](method: String, params: A): Unit =
      self.jsonRpcNotify(method, params)

    def appendExec(commandLine: String, execId: Option[String]): Boolean =
      self.append(Exec(commandLine, execId, Some(CommandSource(name))))

    def appendExec(exec: Exec): Boolean = self.append(exec)

    def log: Logger = self.log
    def name: String = self.name
    private[sbt] def authOptions: Set[ServerAuthentication] = self.authOptions
    private[sbt] def authenticate(token: String): Boolean = self.authenticate(token)
    private[sbt] def setInitialized(value: Boolean): Unit = self.setInitialized(value)
    private[sbt] def onSettingQuery(execId: Option[String], req: SettingQuery): Unit =
      self.onSettingQuery(execId, req)
    private[sbt] def onCompletionRequest(execId: Option[String], cp: CompletionParams): Unit =
      self.onCompletionRequest(execId, cp)
    private[sbt] def onCancellationRequest(execId: Option[String], crp: CancelRequestParams): Unit =
      self.onCancellationRequest(execId, crp)
  }

  protected def authenticate(token: String): Boolean = instance.authenticate(token)

  protected def setInitialized(value: Boolean): Unit = initialized = value

  protected def authOptions: Set[ServerAuthentication] = auth

  override def mkUIThread: (State, CommandChannel) => UITask = (state, command) => {
    if (interactive.get || ContinuousCommands.isInWatch(state, this)) mkUIThreadImpl(state, command)
    else
      new UITask {
        override private[sbt] val channel = NetworkChannel.this
        override private[sbt] lazy val reader: UITask.Reader = () => {
          try {
            this.synchronized((this.wait()))
            Left(TerminateAction)
          } catch {
            case _: InterruptedException => Right("")
          }
        }
      }
  }

  val thread = new Thread(s"sbt-networkchannel-${connection.getPort}") {
    private val ct = "Content-Type: "
    private val x1 = "application/sbt-x1"
    override def run(): Unit = {
      try {
        connection.setSoTimeout(5000)

        val in = connection.getInputStream
        // keep going unless the socket has closed
        while (running.get) {
          try {
            val onHeader: String => Unit = line => {
              if (line.startsWith(ct) && line.contains(x1)) {
                logMessage("error", s"server protocol $x1 is no longer supported")
              }
            }
            val content = ReadJsonFromInputStream(in, running, Some(onHeader))
            if (content.nonEmpty) handleBody(content)
          } catch {
            case _: SocketTimeoutException                => // its ok
            case _: IOException | _: InterruptedException => running.set(false)
          }
        } // while
      } finally {
        shutdown(false)
      }
    }

    private lazy val intents = {
      handlers.toVector map { h =>
        h.handler(callback)
      }
    }

    lazy val onRequestMessage: PartialFunction[JsonRpcRequestMessage, Unit] =
      intents.foldLeft(PartialFunction.empty[JsonRpcRequestMessage, Unit]) { case (f, i) =>
        f orElse i.onRequest
      }
    lazy val onResponseMessage: PartialFunction[JsonRpcResponseMessage, Unit] =
      intents.foldLeft(PartialFunction.empty[JsonRpcResponseMessage, Unit]) { case (f, i) =>
        f orElse i.onResponse
      }

    lazy val onNotification: PartialFunction[JsonRpcNotificationMessage, Unit] =
      intents.foldLeft(PartialFunction.empty[JsonRpcNotificationMessage, Unit]) { case (f, i) =>
        f orElse i.onNotification
      }

    def handleBody(chunk: Seq[Byte]): Unit = {
      Serialization.deserializeJsonMessage(chunk) match {
        case Right(req: JsonRpcRequestMessage) =>
          try {
            registerRequest(req)
            onRequestMessage(req)
          } catch {
            case LangServerError(code, message) =>
              log.debug(s"sending error: $code: $message")
              respondError(code, message, Some(req.id))
          }
        case Right(res: JsonRpcResponseMessage) =>
          onResponseMessage(res)
        case Right(ntf: JsonRpcNotificationMessage) =>
          try {
            onNotification(ntf)
          } catch {
            case LangServerError(code, message) =>
              logMessage("error", s"error $code while handling notification: $message")
          }
        case Right(msg) =>
          log.debug(s"unhandled message: $msg")
        case Left(errorDesc) =>
          val msg =
            s"got invalid chunk from client (${new String(chunk.toArray, "UTF-8")}): $errorDesc"
          log.error(msg)
          logMessage("error", msg)
      }
    }
  }
  thread.start()

  private[sbt] def isLanguageServerProtocol: Boolean = true

  private def registerRequest(request: JsonRpcRequestMessage): Unit = {
    this.synchronized {
      pendingRequests += (request.id -> request)
      ()
    }
  }

  private[sbt] def respondError(
      err: JsonRpcResponseError,
      execId: Option[String]
  ): Unit = this.synchronized {
    getPendingRequest(execId) match {
      case Some(request) =>
        pendingRequests -= request.id
        jsonRpcRespondError(request.id, err)
      case _ =>
        import sbt.internal.protocol.codec.JsonRPCProtocol.given
        val msg =
          s"unmatched json error for requestId $execId: ${CompactPrinter(Converter.toJsonUnsafe(err))}"
        log.debug(msg)
    }
  }

  private[sbt] def getPendingRequest(execId: Option[String]): Option[JsonRpcRequestMessage] = {
    execId.flatMap {
      // This handles multi commands from the network that were remapped to a different
      // exec id for reporting purposes.
      case id if id.startsWith(BasicCommandStrings.networkExecPrefix) =>
        StandardMain.exchange.withState { s =>
          s.get(BasicCommands.execMap)
            .flatMap(_.collectFirst { case (k, `id`) => k })
            .flatMap(pendingRequests.get)
        }
      case id => pendingRequests.get(id)
    }
  }

  private[sbt] def respondError(
      code: Long,
      message: String,
      execId: Option[String]
  ): Unit = {
    respondError(JsonRpcResponseError(code, message), execId)
  }

  private[sbt] def respondResult[A: JsonFormat](
      event: A,
      execId: Option[String]
  ): Unit = this.synchronized {
    getPendingRequest(execId) match {
      case Some(request) =>
        pendingRequests -= request.id
        jsonRpcRespond(event, request.id)
      case _ =>
        val msg =
          s"unmatched json response for requestId $execId: ${CompactPrinter(Converter.toJsonUnsafe(event))}"
        log.debug(msg)
    }
  }

  private[sbt] def notifyEvent[A: JsonFormat](method: String, params: A): Unit = {
    jsonRpcNotify(method, params)
  }

  def respond[A: JsonFormat](event: A): Unit = respond(event, None)

  def respond[A: JsonFormat](event: A, execId: Option[String]): Unit = if (alive.get) {
    respondResult(event, execId)
  }

  def notifyEvent(event: EventMessage): Unit = {
    event match {
      case entry: LogEvent => logMessage(entry.level, entry.message)
      case entry: ExecStatusEvent =>
        getPendingRequest(entry.execId) match {
          case Some(request) =>
            logMessage("debug", s"${entry.status} ${request.method}")
          case None =>
            log.debug(
              s"unmatched ${entry.status} event for requestId ${entry.execId}: ${entry.message}"
            )
        }
      case _ => ()
    }
  }

  def publishBytes(event: Array[Byte]): Unit = publishBytes(event, false)

  /*
   * Do writes on a background thread because otherwise the client socket can get blocked.
   */
  private val writeThread = new Thread(
    () => {
      @tailrec def impl(): Unit = {
        val (event, delimit) =
          try pendingWrites.take
          catch {
            case _: InterruptedException =>
              alive.set(false)
              (Array.empty[Byte], false)
          }
        if (alive.get) {
          try {
            out.write(event)
            if (delimit) {
              out.write(delimiter.toInt)
            }
            out.flush()
          } catch {
            case _: IOException =>
              alive.set(false)
              shutdown(true)
            case _: InterruptedException =>
              alive.set(false)
          }
          impl()
        }
      }
      impl()
    },
    s"sbt-$name-write-thread"
  )
  writeThread.setDaemon(true)
  writeThread.start()

  def publishBytes(event: Array[Byte], delimit: Boolean): Unit =
    try pendingWrites.put(event -> delimit)
    catch { case _: InterruptedException => }

  def onCommand(command: CommandMessage): Unit = command match {
    case x: InitCommand  => onInitCommand(x)
    case x: ExecCommand  => onExecCommand(x)
    case x: SettingQuery => onSettingQuery(None, x)
  }

  private def onInitCommand(cmd: InitCommand): Unit = {
    if (auth(ServerAuthentication.Token)) {
      cmd.token match {
        case Some(x) =>
          authenticate(x) match {
            case true =>
              initialized = true
              notifyEvent(ChannelAcceptedEvent(name))
            case _ => sys.error("invalid token")
          }
        case None => sys.error("init command but without token.")
      }
    } else {
      initialized = true
    }
  }

  private def onExecCommand(cmd: ExecCommand) = {
    if (initialized) {
      append(
        Exec(cmd.commandLine, cmd.execId orElse Some(Exec.newExecId), Some(CommandSource(name)))
      )
      ()
    } else {
      log.warn(s"ignoring command $cmd before initialization")
    }
  }

  protected def onSettingQuery(execId: Option[String], req: SettingQuery) = {
    if (initialized) {
      StandardMain.exchange.withState { s =>
        val structure = Project.extract(s).structure
        sbt.internal.server.SettingQuery.handleSettingQueryEither(req, structure) match {
          case Right(x) => respondResult(x, execId)
          case Left(s)  => respondError(ErrorCodes.InvalidParams, s, execId)
        }
      }
    } else {
      log.warn(s"ignoring query $req before initialization")
    }
  }

  protected def onCompletionRequest(execId: Option[String], cp: CompletionParams) = {
    if (initialized) {
      try {
        StandardMain.exchange.withState { sstate =>
          def completionItems(s: State) = {
            Parser
              .completions(s.combinedParser, cp.query, cp.level.getOrElse(9))
              .get
              .flatMap { c =>
                if (!c.isEmpty) Some(c.append.replaceAll("\n", " "))
                else None
              }
              .map(c => cp.query + c)
          }
          val (items, cachedMainClassNames, cachedTestNames) = {
            val scopedKeyParser: Parser[Seq[Def.ScopedKey[?]]] =
              Act.aggregatedKeyParser(sstate) <~ Parsers.any.*
            Parser.parse(cp.query, scopedKeyParser) match {
              case Right(keys) =>
                val testKeys =
                  keys.filter(k => k.key.label == "testOnly" || k.key.label == "testQuick")
                val (testState, cachedTestNames) = testKeys.foldLeft((sstate, true)) {
                  case ((st, allCached), k) =>
                    SessionVar.loadAndSet(
                      (k.scope / Keys.definedTestNames).scopedKey,
                      st,
                      true
                    ) match {
                      case (nst, d) => (nst, allCached && d.isDefined)
                    }
                }
                val runKeys = keys.filter(_.key.label == "runMain")
                val (runState, cachedMainClassNames) = runKeys.foldLeft((testState, true)) {
                  case ((st, allCached), k) =>
                    SessionVar.loadAndSet(
                      (k.scope / Keys.discoveredMainClasses).scopedKey,
                      st,
                      true
                    ) match {
                      case (nst, d) => (nst, allCached && d.isDefined)
                    }
                }
                (completionItems(runState), cachedMainClassNames, cachedTestNames)
              case _ => (completionItems(sstate), true, true)
            }
          }
          respondResult(
            CompletionResponse(
              items = items.toVector,
              cachedMainClassNames = cachedMainClassNames,
              cachedTestNames = cachedTestNames
            ),
            execId
          )
        }
      } catch {
        case NonFatal(_) =>
          respondError(
            ErrorCodes.UnknownError,
            "Completions request failed",
            execId
          )
      }
    } else {
      log.warn(s"ignoring completion request $cp before initialization")
    }
  }

  protected def onCancellationRequest(execId: Option[String], crp: CancelRequestParams) = {
    if (initialized) {

      def errorRespond(msg: String) = respondError(
        ErrorCodes.RequestCancelled,
        msg,
        execId
      )

      try {
        Option(EvaluateTask.currentlyRunningTaskEngine.get) match {
          case Some(runningEngine) =>
            val runningExecId =
              StandardMain.exchange.withState(_.currentCommand.flatMap(_.execId).getOrElse(""))
            val expected = StandardMain.exchange.withState(
              _.get(BasicCommands.execMap)
                .flatMap(s => s.get(crp.id) orElse s.get("\u2668" + crp.id))
                .getOrElse(crp.id)
            )

            def checkId(): Boolean = {
              if (runningExecId.startsWith("\u2668")) {
                (
                  Try { crp.id.toLong }.toOption,
                  Try { runningExecId.substring(1).toLong }.toOption
                ) match {
                  case (Some(id), Some(eid)) => id == eid
                  case _                     => false
                }
              } else runningExecId == expected
            }

            // direct comparison on strings and
            // remove hotspring unicode added character for numbers
            if (
              checkId() || (crp.id == Serialization.CancelAll &&
                StandardMain.exchange.currentExec.exists(_.source.exists(_.channelName == name)))
            ) {
              runningEngine.cancelAndShutdown()

              respondResult(
                ExecStatusEvent(
                  "Task cancelled",
                  Some(name),
                  Some(runningExecId),
                  Vector(),
                  None,
                ),
                execId
              )
            } else {
              errorRespond("Task ID not matched")
            }

          case None =>
            errorRespond("No tasks under execution")
        }
      } catch {
        case NonFatal(_) =>
          errorRespond("Cancel request failed")
      }
    } else {
      log.warn(s"ignoring cancellation request $crp before initialization")
    }
  }

  @deprecated("Use variant that takes logShutdown parameter", "1.4.0")
  override def shutdown(): Unit = {
    shutdown(true)
  }

  override def shutdown(logShutdown: Boolean): Unit =
    shutdown(logShutdown, remainingCommands = None)

  /**
   * Closes down the channel. Before closing the socket, it sends a notification to
   * the client to shutdown. If the client initiated the shutdown, we don't want the
   * client to display an error or return a non-zero exit code so we send it a
   * notification that tells it whether or not to log the shutdown. This can't
   * easily be done client side because when the client is in interactive session,
   * it doesn't know commands it has sent to the server.
   */
  private[sbt] def shutdown(
      logShutdown: Boolean,
      remainingCommands: Option[(String, String)]
  ): Unit = {
    doFlush()
    terminal.close()
    StandardMain.exchange.removeChannel(this)
    super.shutdown(logShutdown)
    if (logShutdown) Terminal.consoleLog(s"shutting down client connection $name")
    VirtualTerminal.cancelRequests(name)
    try jsonRpcNotify(Shutdown, (logShutdown, remainingCommands))
    catch { case _: IOException => }
    running.set(false)
    out.close()
    outputStream.close()
    thread.interrupt()
    writeThread.interrupt()
  }

  /** Respond back to Language Server's client. */
  private[sbt] def jsonRpcRespond[A: JsonFormat](event: A, execId: String): Unit = {
    val m =
      JsonRpcResponseMessage("2.0", execId, Option(Converter.toJson[A](event).get), None)
    val bytes = Serialization.serializeResponseMessage(m)
    publishBytes(bytes)
  }

  /** Respond back to Language Server's client. */
  private[sbt] def jsonRpcRespondError[A: JsonFormat](
      execId: String,
      code: Long,
      message: String,
      data: A,
  ): Unit = {
    val err = JsonRpcResponseError(code, message, Converter.toJson[A](data).get)
    jsonRpcRespondError(execId, err)
  }

  private[sbt] def jsonRpcRespondError(
      execId: String,
      err: JsonRpcResponseError
  ): Unit = {
    forceFlush()
    val m = JsonRpcResponseMessage("2.0", execId, None, Option(err))
    val bytes = Serialization.serializeResponseMessage(m)
    publishBytes(bytes)
  }

  /** Notify to Language Server's client. */
  private[sbt] def jsonRpcNotify[A: JsonFormat](method: String, params: A): Unit = {
    val m =
      JsonRpcNotificationMessage("2.0", method, Option(Converter.toJson[A](params).get))
    if (method != Serialization.systemOut) {
      forceFlush()
      log.debug(s"jsonRpcNotify: $m")
    }
    val bytes = Serialization.serializeNotificationMessage(m)
    publishBytes(bytes)
  }

  /** Notify to Language Server's client. */
  private[sbt] def jsonRpcRequest[A: JsonFormat](id: String, method: String, params: A): Unit = {
    forceFlush()
    val m =
      JsonRpcRequestMessage("2.0", id, method, Option(Converter.toJson[A](params).get))
    log.debug(s"jsonRpcRequest: $m")
    val bytes = Serialization.serializeRequestMessage(m)
    publishBytes(bytes)
  }

  def logMessage(level: String, message: String): Unit = {
    import sbt.internal.langserver.codec.JsonProtocol._
    jsonRpcNotify(
      "build/logMessage",
      LogMessageParams(MessageType.fromLevelString(level), message)
    )
  }

  private lazy val inputStream: InputStream = new Terminal.SimpleInputStream {
    override def read(): Int = {
      import sjsonnew.BasicJsonProtocol._
      try {
        jsonRpcNotify(readSystemIn, "")
        inputBuffer.take
      } catch {
        case e: IOException =>
          try jsonRpcNotify(cancelReadSystemIn, "")
          catch { case _: IOException => }
          -1
      }
    }
    override def available(): Int = inputBuffer.size
  }
  private lazy val writeableInputStream: Terminal.WriteableInputStream =
    new Terminal.WriteableInputStream(inputStream, name)
  import sjsonnew.BasicJsonProtocol._

  import scala.jdk.CollectionConverters.*
  private val outputBuffer = new LinkedBlockingQueue[Byte]
  private val flushExecutor = Executors.newSingleThreadScheduledExecutor(r =>
    new Thread(r, s"$name-output-buffer-timer-thread")
  )

  private def forceFlush(): Unit =
    Util.ignoreResult(flushExecutor.shutdownNow())
    doFlush()

  private def doFlush() = {
    val list = new java.util.ArrayList[Byte]
    outputBuffer.synchronized(outputBuffer.drainTo(list))
    if (!list.isEmpty) jsonRpcNotify(Serialization.systemOut, list.asScala.toSeq)
  }

  private lazy val outputStream: OutputStream & AutoCloseable = new OutputStream
    with AutoCloseable {
    /*
     * We buffer calls to flush to the remote client so that it is called at most
     * once every 20 milliseconds. This is done because many terminals seem to flicker
     * and display ghost characters if we flush to the remote client too often. The
     * json protocol is a bit bulky so this will also reduce the total number of
     * bytes that are written to the named pipe or unix domain socket. The buffer
     * period of 20 milliseconds was arbitrarily chosen and could be tuned in the future.
     * The thinking is that writes tend to be bursty so a twenty millisecond window is
     * probably long enough to catch each burst but short enough to not introduce
     * noticeable latency.
     */
    private val flushFuture = new AtomicReference[java.util.concurrent.Future[?]]
    override def close(): Unit = {
      forceFlush()
    }
    override def write(b: Int): Unit = outputBuffer.synchronized {
      outputBuffer.put(b.toByte)
    }
    override def flush(): Unit = {
      flushFuture.get match {
        case null =>
          try {
            flushFuture.set(
              flushExecutor.schedule(
                (() => {
                  flushFuture.set(null)
                  doFlush()
                }): Runnable,
                20,
                TimeUnit.MILLISECONDS
              )
            )
          } catch { case _: RejectedExecutionException => doFlush() }
        case f =>
      }
    }
    override def write(b: Array[Byte]): Unit = outputBuffer.synchronized {
      b.foreach(outputBuffer.put)
    }
    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      write(java.util.Arrays.copyOfRange(b, off, off + len))
    }
  }
  private lazy val errorStream: OutputStream = new OutputStream {
    private val buffer = new LinkedBlockingQueue[Byte]
    override def write(b: Int): Unit = buffer.synchronized {
      buffer.put(b.toByte)
    }
    override def flush(): Unit = {
      val list = new java.util.ArrayList[Byte]
      buffer.synchronized(buffer.drainTo(list))
      if (!list.isEmpty) jsonRpcNotify(Serialization.systemErr, list.asScala.toSeq)
    }
    override def write(b: Array[Byte]): Unit = buffer.synchronized {
      b.foreach(buffer.put)
    }
    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      write(java.util.Arrays.copyOfRange(b, off, off + len))
    }
  }
  private class NetworkTerminal
      extends TerminalImpl(writeableInputStream, outputStream, errorStream, name) { term =>
    private val pending = new AtomicBoolean(false)
    private val closed = new AtomicBoolean(false)
    private val properties = new AtomicReference[TerminalPropertiesResponse]
    private val lastUpdate = new AtomicReference[Deadline]
    private def empty = TerminalPropertiesResponse(0, 0, false, false, false, false)
    def getProperties(block: Boolean): Unit = {
      if (alive.get) {
        if (!pending.get && Option(lastUpdate.get).fold(true)(d => (d + 1.second).isOverdue)) {
          pending.set(true)
          val queue = VirtualTerminal.sendTerminalPropertiesQuery(term.name, jsonRpcRequest)
          val update: Runnable = () => {
            queue.poll(5, java.util.concurrent.TimeUnit.SECONDS) match {
              case null =>
              case t    => properties.set(t)
            }
            pending.synchronized {
              lastUpdate.set(Deadline.now)
              pending.set(false)
              pending.notifyAll()
            }
          }
          new Thread(update, s"network-terminal-${term.name}-update") {
            setDaemon(true)
          }.start()
        }
        while (block && properties.get == null) pending.synchronized(pending.wait())
        ()
      } else throw new InterruptedException
    }
    private def withThread[R](f: => R, default: R): R = {
      val t = Thread.currentThread
      try {
        blockedThreads.synchronized(blockedThreads.add(t))
        f
      } catch { case _: InterruptedException => default }
      finally {
        Util.ignoreResult(blockedThreads.synchronized(blockedThreads.remove(t)))
      }
    }
    def getProperty[T](f: TerminalPropertiesResponse => T, default: T): Option[T] = {
      if (closed.get || !isAttached) None
      else
        withThread(
          {
            getProperties(true);
            Some(f(Option(properties.get).getOrElse(empty)))
          },
          None
        )
    }
    private def waitForPending(f: TerminalPropertiesResponse => Boolean): Boolean = {
      if (closed.get || !isAttached) false
      else
        withThread(
          {
            if (pending.get) pending.synchronized(pending.wait())
            Option(properties.get).map(f).getOrElse(false)
          },
          false
        )
    }
    private val blockedThreads = ConcurrentHashMap.newKeySet[Thread]
    override private[sbt] val progressState: ProgressState = new ProgressState(
      1,
      StandardMain.exchange
        .withState(_.get(Keys.superShellMaxTasks.key))
        .getOrElse(SysProp.supershellMaxTasks)
    )
    override def getWidth: Int = getProperty(_.width, 0).getOrElse(0)
    override def getHeight: Int = getProperty(_.height, 0).getOrElse(0)
    override def isAnsiSupported: Boolean = getProperty(_.isAnsiSupported, false).getOrElse(false)
    override def isEchoEnabled: Boolean = sbt.internal.util.JLine3.isEchoEnabled(getAttributes)
    override def isSuccessEnabled: Boolean =
      interactive.get ||
        StandardMain.exchange.withState(ContinuousCommands.isInWatch(_, NetworkChannel.this))
    override lazy val isColorEnabled: Boolean = waitForPending(_.isColorEnabled)
    override lazy val isSupershellEnabled: Boolean = waitForPending(_.isSupershellEnabled)
    getProperties(false)
    private def getCapability[T](
        query: TerminalCapabilitiesQuery,
        result: TerminalCapabilitiesResponse => T
    ): Option[T] = {
      if (closed.get) None
      else {
        val queue = VirtualTerminal.sendTerminalCapabilitiesQuery(
          term.name,
          jsonRpcRequest[TerminalCapabilitiesQuery],
          query
        )
        Some(result(queue.take))
      }
    }
    override def getBooleanCapability(capability: String): Boolean =
      getCapability(
        TerminalCapabilitiesQuery(boolean = Some(capability), numeric = None, string = None),
        _.boolean.getOrElse(false)
      ).getOrElse(false)
    override def getNumericCapability(capability: String): Integer =
      getCapability(
        TerminalCapabilitiesQuery(boolean = None, numeric = Some(capability), string = None),
        (_: TerminalCapabilitiesResponse).numeric.map(Integer.valueOf).getOrElse(-1: Integer)
      ).getOrElse(-1: Integer)
    override def getStringCapability(capability: String): String =
      getCapability(
        TerminalCapabilitiesQuery(boolean = None, numeric = None, string = Some(capability)),
        _.string
          .flatMap {
            case "null" => None
            case s      => Some(s)
          }
          .orNull
      ).getOrElse("")

    override private[sbt] def getAttributes: Map[String, String] =
      if (closed.get) Map.empty
      else {
        val queue = VirtualTerminal.sendTerminalAttributesQuery(
          term.name,
          jsonRpcRequest[TerminalAttributesQuery]
        )
        try {
          val a = queue.take
          Map(
            "iflag" -> a.iflag,
            "oflag" -> a.oflag,
            "cflag" -> a.cflag,
            "lflag" -> a.lflag,
            "cchars" -> a.cchars
          )
        } catch { case _: InterruptedException => Map.empty }
      }
    override private[sbt] def setAttributes(attributes: Map[String, String]): Unit =
      if (!closed.get) {
        val attrs = TerminalSetAttributesCommand(
          iflag = attributes.getOrElse("iflag", ""),
          oflag = attributes.getOrElse("oflag", ""),
          cflag = attributes.getOrElse("cflag", ""),
          lflag = attributes.getOrElse("lflag", ""),
          cchars = attributes.getOrElse("cchars", ""),
        )
        val queue = VirtualTerminal.setTerminalAttributesCommand(
          term.name,
          jsonRpcRequest[TerminalSetAttributesCommand],
          attrs
        )
        try queue.take
        catch { case _: InterruptedException => }
      }
    override private[sbt] def getSizeImpl: (Int, Int) =
      if (!closed.get) {
        val queue =
          VirtualTerminal.getTerminalSize(term.name, jsonRpcRequest[TerminalGetSizeQuery])
        val res =
          try queue.take
          catch { case _: InterruptedException => TerminalGetSizeResponse(1, 1) }
        (res.width, res.height)
      } else (1, 1)
    override def setSize(width: Int, height: Int): Unit =
      if (!closed.get) {
        val size = TerminalSetSizeCommand(width, height)
        val queue =
          VirtualTerminal.setTerminalSize(term.name, jsonRpcRequest[TerminalSetSizeCommand], size)
        try queue.take
        catch { case _: InterruptedException => }
      }
    private def setRawMode(toggle: Boolean): Unit = {
      if (!closed.get || false) {
        val raw = TerminalSetRawModeCommand(toggle)
        val queue = VirtualTerminal.setTerminalRawMode(
          term.name,
          jsonRpcRequest[TerminalSetRawModeCommand],
          raw
        )
        try queue.take
        catch { case _: InterruptedException => }
      }
    }
    override private[sbt] def enterRawMode(): Unit = setRawMode(true)
    override private[sbt] def exitRawMode(): Unit = setRawMode(false)
    override def setEchoEnabled(toggle: Boolean): Unit =
      if (!closed.get) {
        val echo = TerminalSetEchoCommand(toggle)
        val queue =
          VirtualTerminal.setTerminalEcho(term.name, jsonRpcRequest[TerminalSetEchoCommand], echo)
        try queue.take
        catch { case _: InterruptedException => () }
      }

    override def flush(): Unit = doFlush()
    override def toString: String = s"NetworkTerminal(${term.name})"
    override def close(): Unit = if (closed.compareAndSet(false, true)) {
      val threads = blockedThreads.synchronized {
        val t = blockedThreads.asScala.toVector
        blockedThreads.clear()
        t
      }
      threads.foreach(_.interrupt())
      super.close()
    }
  }
  private[sbt] def isAttached: Boolean = attached.get
}

object NetworkChannel {
  sealed trait ChannelState
  case object SingleLine extends ChannelState
  case object InHeader extends ChannelState
  case object InBody extends ChannelState
  private[sbt] def cancel(
      execID: Option[String],
      id: String,
      force: Boolean
  ): Either[String, String] = {

    Option(EvaluateTask.currentlyRunningTaskEngine.get) match {
      case Some(runningEngine) =>
        val runningExecId =
          StandardMain.exchange.withState(_.currentCommand.flatMap(_.execId).getOrElse(""))

        def checkId(): Boolean = {
          if (runningExecId.startsWith("\u2668")) {
            (
              Try { id.toLong }.toOption,
              Try { runningExecId.substring(1).toLong }.toOption
            ) match {
              case (Some(id), Some(eid)) => id == eid
              case _                     => false
            }
          } else runningExecId == id
        }

        // direct comparison on strings and
        // remove hotspring unicode added character for numbers
        if (checkId() || force) {
          runningEngine.cancelAndShutdown()
          Right(runningExecId)
        } else {
          Left("Task ID not matched")
        }

      case None =>
        Left("No tasks under execution")
    }
  }

  private[sbt] val disconnect: Command =
    Command.arb { s =>
      val dncParser: Parser[String] = BasicCommandStrings.DisconnectNetworkChannel
      dncParser.examples() ~> Parsers.Space.examples() ~> Parsers.any.*.examples()
    } { (st, channel) =>
      StandardMain.exchange.killChannel(channel.mkString)
      st
    }
}
