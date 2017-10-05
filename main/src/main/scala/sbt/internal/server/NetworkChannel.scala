/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package server

import java.net.{ Socket, SocketTimeoutException }
import java.util.concurrent.atomic.AtomicBoolean

import sjsonnew._
import scala.annotation.tailrec
import sbt.protocol._
import sbt.internal.langserver.ErrorCodes
import sbt.internal.util.ObjectEvent
import sbt.internal.util.codec.JValueFormats
import sbt.util.Logger

final class NetworkChannel(val name: String,
                           connection: Socket,
                           structure: BuildStructure,
                           auth: Set[ServerAuthentication],
                           instance: ServerInstance,
                           val log: Logger)
    extends CommandChannel
    with LanguageServerProtocol {
  import NetworkChannel._

  private val running = new AtomicBoolean(true)
  private val delimiter: Byte = '\n'.toByte
  private val RetByte = '\r'.toByte
  private val out = connection.getOutputStream
  private var initialized = false
  private val Curly = '{'.toByte
  private val ContentLength = """^Content\-Length\:\s*(\d+)""".r
  private val ContentType = """^Content\-Type\:\s*(.+)""".r
  private var _contentType: String = ""
  private val SbtX1Protocol = "application/sbt-x1"
  private val VsCode = sbt.protocol.Serialization.VsCode
  private val VsCodeOld = "application/vscode-jsonrpc; charset=utf8"
  private lazy val jsonFormat = new sjsonnew.BasicJsonProtocol with JValueFormats {}

  def setContentType(ct: String): Unit = synchronized {
    _contentType = ct
  }
  def contentType: String = _contentType

  protected def authenticate(token: String): Boolean = {
    instance.authenticate(token)
  }

  protected def setInitialized(value: Boolean): Unit = {
    initialized = value
  }

  protected def authOptions: Set[ServerAuthentication] = auth

  val thread = new Thread(s"sbt-networkchannel-${connection.getPort}") {
    var contentLength: Int = 0
    var state: ChannelState = SingleLine

    override def run(): Unit = {
      try {
        val readBuffer = new Array[Byte](4096)
        val in = connection.getInputStream
        connection.setSoTimeout(5000)
        var buffer: Vector[Byte] = Vector.empty
        var bytesRead = 0
        def resetChannelState(): Unit = {
          contentLength = 0
          // contentType = ""
          state = SingleLine
        }

        def tillEndOfLine: Option[Vector[Byte]] = {
          val delimPos = buffer.indexOf(delimiter)
          if (delimPos > 0) {
            val chunk0 = buffer.take(delimPos)
            buffer = buffer.drop(delimPos + 1)
            // remove \r at the end of line.
            if (chunk0.size > 0 && chunk0.indexOf(RetByte) == chunk0.size - 1)
              Some(chunk0.dropRight(1))
            else Some(chunk0)
          } else None // no EOL yet, so skip this turn.
        }

        def tillContentLength: Option[Vector[Byte]] = {
          if (contentLength <= buffer.size) {
            val chunk = buffer.take(contentLength)
            buffer = buffer.drop(contentLength)
            resetChannelState()
            Some(chunk)
          } else None // have not read enough yet, so skip this turn.
        }

        @tailrec def process(): Unit = {
          // handle un-framing
          state match {
            case SingleLine =>
              tillEndOfLine match {
                case Some(chunk) =>
                  chunk.headOption match {
                    case None        => // ignore blank line
                    case Some(Curly) =>
                      // When Content-Length header is not found, interpret the line as JSON message.
                      handleBody(chunk)
                      process()
                    case Some(_) =>
                      val str = (new String(chunk.toArray, "UTF-8")).trim
                      handleHeader(str) match {
                        case Some(_) =>
                          state = InHeader
                          process()
                        case _ => log.error("Got invalid chunk from client: " + str)
                      }
                  }
                case _ => ()
              }
            case InHeader =>
              tillEndOfLine match {
                case Some(chunk) =>
                  val str = (new String(chunk.toArray, "UTF-8")).trim
                  if (str == "") {
                    state = InBody
                    process()
                  } else
                    handleHeader(str) match {
                      case Some(_) => process()
                      case _ =>
                        log.error("Got invalid header from client: " + str)
                        resetChannelState()
                    }
                case _ => ()
              }
            case InBody =>
              tillContentLength match {
                case Some(chunk) =>
                  handleBody(chunk)
                  process()
                case _ => ()
              }
          }
        }

        // keep going unless the socket has closed
        while (bytesRead != -1 && running.get) {
          try {
            bytesRead = in.read(readBuffer)
            // log.debug(s"bytesRead: $bytesRead")
            if (bytesRead > 0) {
              buffer = buffer ++ readBuffer.toVector.take(bytesRead)
            }
            process()
          } catch {
            case _: SocketTimeoutException => // its ok
          }
        } // while
      } finally {
        shutdown()
      }
    }

    def handleBody(chunk: Vector[Byte]): Unit = {
      if (isLanguageServerProtocol) {
        Serialization.deserializeJsonRequest(chunk) match {
          case Right(req) =>
            try {
              onRequestMessage(req)
            } catch {
              case LangServerError(code, message) =>
                log.debug(s"sending error: $code: $message")
                langError(Option(req.id), code, message)
            }
          case Left(errorDesc) =>
            val msg = s"Got invalid chunk from client (${new String(chunk.toArray, "UTF-8")}): " + errorDesc
            langError(None, ErrorCodes.ParseError, msg)
        }
      } else {
        contentType match {
          case SbtX1Protocol =>
            Serialization
              .deserializeCommand(chunk)
              .fold(
                errorDesc =>
                  log.error(
                    s"Got invalid chunk from client (${new String(chunk.toArray, "UTF-8")}): " + errorDesc),
                onCommand
              )
          case _ =>
            log.error(s"Unknown Content-Type: $contentType")
        }
      } // if-else
    }

    def handleHeader(str: String): Option[Unit] = {
      str match {
        case ContentLength(len) =>
          contentLength = len.toInt
          Some(())
        case ContentType(ct) =>
          setContentType(ct)
          Some(())
        case _ => None
      }
    }
  }
  thread.start()

  private[sbt] def isLanguageServerProtocol: Boolean = {
    contentType match {
      case "" | VsCode | VsCodeOld => true
      case _                       => false
    }
  }

  private[sbt] def notifyEvent[A: JsonFormat](method: String, params: A): Unit = {
    if (isLanguageServerProtocol) {
      langNotify(method, params)
    } else {
      ()
    }
  }

  def publishEvent[A: JsonFormat](event: A, execId: Option[String]): Unit = {
    if (isLanguageServerProtocol) {
      langRespond(event, execId)
    } else {
      contentType match {
        case SbtX1Protocol =>
          val bytes = Serialization.serializeEvent(event)
          publishBytes(bytes, true)
        case _ =>
      }
    }
  }

  def publishEvent[A: JsonFormat](event: A): Unit = publishEvent(event, None)

  def publishEventMessage(event: EventMessage): Unit = {
    contentType match {
      case SbtX1Protocol =>
        val bytes = Serialization.serializeEventMessage(event)
        publishBytes(bytes, true)
      case _ =>
    }
  }

  /**
   * This publishes object events. The type information has been
   * erased because it went through logging.
   */
  private[sbt] def publishObjectEvent(event: ObjectEvent[_]): Unit = {
    import sjsonnew.shaded.scalajson.ast.unsafe._
    if (isLanguageServerProtocol) onObjectEvent(event)
    else {
      import jsonFormat._
      val json: JValue = JObject(
        JField("type", JString(event.contentType)),
        (Vector(JField("message", event.json), JField("level", JString(event.level.toString))) ++
          (event.channelName.toVector map { channelName =>
            JField("channelName", JString(channelName))
          }) ++
          (event.execId.toVector map { execId =>
            JField("execId", JString(execId))
          })): _*
      )
      publishEvent(json)
    }
  }

  def publishBytes(event: Array[Byte]): Unit = publishBytes(event, false)

  def publishBytes(event: Array[Byte], delimit: Boolean): Unit = {
    out.write(event)
    if (delimit) {
      out.write(delimiter.toInt)
    }
    out.flush()
  }

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
              publishEventMessage(ChannelAcceptedEvent(name))
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
        Exec(cmd.commandLine, cmd.execId orElse Some(Exec.newExecId), Some(CommandSource(name))))
    } else {
      log.warn(s"ignoring command $cmd before initialization")
    }
  }

  protected def onSettingQuery(execId: Option[String], req: SettingQuery) = {
    if (initialized) {
      import sbt.protocol.codec.JsonProtocol._
      SettingQuery.handleSettingQueryEither(req, structure) match {
        case Right(x) => langRespond(x, execId)
        case Left(s)  => langError(execId, ErrorCodes.InvalidParams, s)
      }
    } else {
      log.warn(s"ignoring query $req before initialization")
    }
  }

  def shutdown(): Unit = {
    log.info("Shutting down client connection")
    running.set(false)
    out.close()
  }
}

object NetworkChannel {
  sealed trait ChannelState
  case object SingleLine extends ChannelState
  case object InHeader extends ChannelState
  case object InBody extends ChannelState
}
