import java.io._
import java.net.InetSocketAddress
import java.nio.file.{ Files, Paths }
import com.sun.net.httpserver.{ HttpExchange, HttpHandler, HttpServer }

/** Minimal HTTP server that accepts PUT and writes to a base directory (for ivyless publish scripted test). */
object HttpPutServer {
  private var server: HttpServer = null

  def start(port: Int, baseDir: java.io.File): Unit = {
    if (server != null) stop()
    server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/", new HttpHandler {
      override def handle(ex: HttpExchange): Unit = {
        val method = ex.getRequestMethod
        if ("PUT".equalsIgnoreCase(method)) {
          val path = ex.getRequestURI.getRawPath
          val relativePath = if (path.startsWith("/")) path.substring(1) else path
          val targetFile = new File(baseDir, relativePath.replace("/", File.separator))
          targetFile.getParentFile.mkdirs()
          val in = ex.getRequestBody
          val out = new FileOutputStream(targetFile)
          try {
            in.transferTo(out)
          } finally {
            out.close()
            in.close()
          }
          ex.sendResponseHeaders(200, -1)
          ex.close()
        } else {
          ex.sendResponseHeaders(405, -1)
          ex.close()
        }
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
