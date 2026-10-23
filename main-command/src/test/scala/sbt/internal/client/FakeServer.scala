/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package client

import java.io.{ ByteArrayInputStream, File, PrintStream }
import java.net.Socket
import java.nio.file.{ Files, Paths }
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Try

import sbt.internal.server.{ Server, ServerConnection, ServerInstance }
import sbt.internal.util.Util
import sbt.protocol.{ JsonRpcReader, JsonRpcWriter }
import sbt.util.Level
import sjsonnew.shaded.scalajson.ast.unsafe.{ JBoolean, JObject, JString, JValue }
import sjsonnew.support.scalajson.unsafe.Parser

/**
 * A server that answers the thin client over a real socket the way a test scripts it. The client
 * connects through the portfile as it would to sbt, so the handshake, the token and the response
 * handlers are the real ones; only the far end is scripted.
 */
private[client] object FakeServer:
  /** What the fake sends back for one request. */
  enum Reply:
    case Answer(body: String)

    /** Answers and then closes the connection, the way a server that exits ends. */
    case AnswerAndClose(body: String)
    case Ignore

  /**
   * The script of the fake server: what it sends back for each request the client writes.
   *
   * The channel thread calls it once per JSON-RPC request, in the order the client sent them,
   * and sends whatever `Reply` it returns. Its arguments are the real `ServerInstance` that
   * `Server.start` created and the raw request as a JSON string. The instance is there for the
   * handshake: `instance.authenticate(token)` checks a token the client presented against the
   * token file, as the real server does, and spends it. The request string is what the client
   * wrote on the wire, so read it with `stringField` or `booleanField` by path, for example
   * `stringField(request, "method")` or `stringField(request, "params", "initializationOptions",
   * "token")`. Every request also lands in `Recorded.requests`.
   */
  type Responder = (ServerInstance, String) => Reply

  final class Recorded:
    val requests = new ConcurrentLinkedQueue[String]
    val logs = new ConcurrentLinkedQueue[(Level.Value, String)]
    def methods: List[String] = requests.asScala.toList.flatMap(stringField(_, "method"))
    def request(method: String): Option[String] =
      requests.asScala.find(stringField(_, "method").contains(method))
    def logged(level: Level.Value): List[String] =
      logs.asScala.toList.collect:
        case (`level`, m) => m

  private def recording(recorded: Recorded) = new ConsoleInterface:
    override def appendLog(level: Level.Value, message: => String): Unit =
      Util.ignoreResult(recorded.logs.add(level -> message))
    override def success(msg: String): Unit = ()

  private def at(json: String, path: Seq[String]): Option[JValue] =
    path.foldLeft(Parser.parseFromString(json).toOption):
      case (Some(JObject(fields)), name) => fields.find(_.field == name).map(_.value)
      case _                             => None

  def stringField(json: String, path: String*): Option[String] =
    at(json, path).collect:
      case JString(s) => s

  def booleanField(json: String, path: String*): Option[Boolean] =
    at(json, path).collect:
      case JBoolean(b) => b

  def stringFieldOrFail(json: String, path: String*): String =
    stringField(json, path*).getOrElse(sys.error(s"no ${path.mkString(".")} in $json"))

  def result(id: String, body: String): String =
    s"""{"jsonrpc":"2.0","id":"$id","result":$body}"""

  def error(id: String, message: String): String =
    s"""{"jsonrpc":"2.0","id":"$id","error":{"code":-32600,"message":"$message"}}"""

  private def channel(responder: Responder, recorded: Recorded)(
      socket: AtomicReference[Socket],
      instance: ServerInstance
  ): Unit =
    val client = socket.get
    val thread = new Thread("fake-server-channel"):
      setDaemon(true)
      override def run(): Unit =
        val running = new AtomicBoolean(true)
        val in = client.getInputStream
        val out = client.getOutputStream
        def answer(body: String): Unit =
          Try(JsonRpcWriter.write(out, body)).failed.foreach(_ => running.set(false))
        while running.get do
          val request = Try(JsonRpcReader.readAsString(in, running)).getOrElse("")
          if request.isEmpty then running.set(false)
          else
            recorded.requests.add(request)
            responder(instance, request) match
              case Reply.Answer(body)         => answer(body)
              case Reply.AnswerAndClose(body) =>
                answer(body)
                running.set(false)
                Util.ignoreTry(client.close())
              case Reply.Ignore => ()
        end while
      end run
    thread.start()
    AtomicCloseable.release(socket)
  end channel

  def withFakeServer(responder: Responder, commandArguments: Seq[String] = Nil)(
      f: (NetworkClient, File, Recorded) => Unit
  ): Unit =
    // the socket path has a length limit, so keep the directory short
    val base = Files.createTempDirectory(Paths.get("/tmp"), "sbtfake").toFile
    val portfile = new File(new File(new File(base, "project"), "target"), "active.json")
    sbt.io.IO.createDirectory(portfile.getParentFile)
    val recorded = new Recorded
    val connection = ServerConnection(
      connectionType = ConnectionType.Local,
      host = "127.0.0.1",
      port = 0,
      auth = Set(ServerAuthentication.Token),
      portfile = portfile,
      tokenfile = new File(base, "token.json"),
      socketfile = new File(base, "sock"),
      pipeName = "sbt-test-" + base.getName,
      appConfiguration = null, // only a bsp connection file reads it, and bsp is off here
      useJni = false,
      bspEnabled = false,
    )
    val instance = Server.start(connection, channel(responder, recorded), sbt.util.Logger.Null)
    Await.ready(instance.ready, 10.seconds)
    val devNull = new PrintStream(java.io.OutputStream.nullOutputStream)
    val arguments =
      new NetworkClient.Arguments(base, Nil, commandArguments, Nil, "sbt", false, None)
    val client = new NetworkClient(
      arguments,
      recording(recorded),
      new ByteArrayInputStream(Array.emptyByteArray),
      devNull,
      devNull,
      useJNI = false,
    )
    try f(client, portfile, recorded)
    finally
      Util.ignoreTry(client.close())
      instance.shutdown()
      sbt.io.IO.delete(base)
  end withFakeServer
end FakeServer
