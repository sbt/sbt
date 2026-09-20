import java.io._
import java.net.InetSocketAddress
import com.sun.net.httpserver.{ HttpExchange, HttpHandler, HttpServer }

/**
 * Minimal HTTP repository that accepts PUT and answers HEAD from what it already holds, so a
 * publish with overwriting disabled can tell whether an artifact is already there.
 */
object HttpRepoServer {
  private var server: HttpServer = null

  def start(port: Int, baseDir: File): Unit = {
    if (server != null) stop()
    server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/", new HttpHandler {
      override def handle(ex: HttpExchange): Unit = {
        val path = ex.getRequestURI.getRawPath
        val relativePath = if (path.startsWith("/")) path.substring(1) else path
        val targetFile = new File(baseDir, relativePath.replace("/", File.separator))
        val status = ex.getRequestMethod.toUpperCase match {
          case "PUT" =>
            targetFile.getParentFile.mkdirs()
            val in = ex.getRequestBody
            val out = new FileOutputStream(targetFile)
            try in.transferTo(out)
            finally { out.close(); in.close() }
            200
          case "HEAD" => if (targetFile.isFile) 200 else 404
          case _      => 405
        }
        ex.sendResponseHeaders(status, -1)
        ex.close()
      }
    })
    server.setExecutor(null)
    server.start()
  }

  def stop(): Unit = {
    if (server != null) {
      server.stop(0)
      server = null
    }
  }
}
