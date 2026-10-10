package sbt
package internal

import build.bazel.remote.execution.v2.ActionResult as XActionResult
import com.sun.net.httpserver.{ HttpExchange, HttpServer }
import java.net.{ InetSocketAddress, URI }
import java.security.MessageDigest
import java.util.concurrent.{ ConcurrentHashMap, ConcurrentLinkedQueue }
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * An in-process stand-in for the HTTP/1.1 REST API of bazel-remote, reproducing the
 * behavior observed with bazel-remote 2.6.2. bazel-remote itself accepts SHA-256 only;
 * `algorithm` stands in for a server configured with another digest function.
 */
class FakeBazelRemote(algorithm: String = "SHA-256") extends AutoCloseable:
  val ac: ConcurrentHashMap[String, Array[Byte]] = ConcurrentHashMap()
  val cas: ConcurrentHashMap[String, Array[Byte]] = ConcurrentHashMap()
  val requests: ConcurrentLinkedQueue[FakeBazelRemote.Recorded] = ConcurrentLinkedQueue()

  private val hexLength = MessageDigest.getInstance(algorithm).getDigestLength() * 2
  private val HexKey = s"(?:/[^/]+)*/(ac|cas)/([0-9a-f]{$hexLength})".r
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/", exchange => handle(exchange))
  server.start()

  def port: Int = server.getAddress().getPort()
  def uri: URI = URI(s"http://127.0.0.1:$port")
  def recorded: List[FakeBazelRemote.Recorded] = requests.asScala.toList

  override def close(): Unit = server.stop(0)

  private def handle(exchange: HttpExchange): Unit =
    try
      val method = exchange.getRequestMethod()
      val path = exchange.getRequestURI().getPath()
      val headers = exchange
        .getRequestHeaders()
        .asScala
        .map((k, v) => k.toLowerCase -> v.asScala.mkString(","))
        .toMap
      requests.add(FakeBazelRemote.Recorded(method, path, headers))
      val body = exchange.getRequestBody().readAllBytes()
      path match
        case HexKey(kind, key) =>
          val store = if kind == "ac" then ac else cas
          method match
            case "PUT" if !headers.contains("content-length")   => respond(exchange, 400)
            case "PUT" if kind == "ac" && !isActionResult(body) => respond(exchange, 400)
            case "PUT" if kind == "cas" && hash(body) != key    => respond(exchange, 500)
            case "PUT"                                          =>
              store.put(key, body)
              respond(exchange, 200)
            case "GET" | "HEAD" =>
              Option(store.get(key)) match
                case Some(bytes) if kind == "cas" || blobsPresent(bytes) =>
                  respond(exchange, 200, bytes, method == "HEAD")
                case _ => respond(exchange, 404)
            case _ => respond(exchange, 405)
        case _ => respond(exchange, 400)
    catch case NonFatal(_) => respond(exchange, 500)

  private def respond(
      exchange: HttpExchange,
      status: Int,
      body: Array[Byte] = Array.emptyByteArray,
      headOnly: Boolean = false,
  ): Unit =
    if headOnly then
      exchange.getResponseHeaders().set("Content-Length", body.length.toString)
      exchange.sendResponseHeaders(status, -1)
    else if body.isEmpty then exchange.sendResponseHeaders(status, -1)
    else
      exchange.sendResponseHeaders(status, body.length.toLong)
      exchange.getResponseBody().write(body)
    exchange.close()

  private def isActionResult(bytes: Array[Byte]): Boolean =
    try
      XActionResult.parseFrom(bytes)
      true
    catch case NonFatal(_) => false

  private def blobsPresent(bytes: Array[Byte]): Boolean =
    XActionResult
      .parseFrom(bytes)
      .getOutputFilesList
      .asScala
      .forall(out => cas.containsKey(out.getDigest().getHash()))

  private def hash(bytes: Array[Byte]): String =
    MessageDigest.getInstance(algorithm).digest(bytes).map("%02x".format(_)).mkString
end FakeBazelRemote

object FakeBazelRemote:
  case class Recorded(method: String, path: String, headers: Map[String, String])
end FakeBazelRemote
