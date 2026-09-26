import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.{ Instant, ZoneOffset }
import java.time.format.DateTimeFormatter
import com.sun.net.httpserver.{ HttpExchange, HttpServer }

/** Serves a directory over HTTP, so coursier caches it like a remote repository. */
object RepoServer:
  private val handleKey = "global-plugin-update.repo-server"

  def start(baseDir: File, portFile: File): Unit =
    stop()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", (ex: HttpExchange) => serve(baseDir, ex))
    server.start()
    System.getProperties.put(handleKey, server)
    Files.writeString(portFile.toPath, server.getAddress.getPort.toString)

  /** The handle lives outside the meta-build classloader, which `reload` replaces. */
  def stop(): Unit =
    System.getProperties.remove(handleKey) match
      case server: HttpServer => server.stop(0)
      case _                  => ()

  private def serve(baseDir: File, ex: HttpExchange): Unit =
    val file = new File(baseDir, ex.getRequestURI.getPath.stripPrefix("/"))
    if file.isFile then
      val modified = Instant.ofEpochMilli(file.lastModified).atZone(ZoneOffset.UTC)
      ex.getResponseHeaders.add("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(modified))
      if ex.getRequestMethod == "HEAD" then ex.sendResponseHeaders(200, -1)
      else
        val bytes = Files.readAllBytes(file.toPath)
        ex.sendResponseHeaders(200, bytes.length)
        ex.getResponseBody.write(bytes)
    else ex.sendResponseHeaders(404, -1)
    ex.close()
end RepoServer
