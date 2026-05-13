import java.io._
import java.net.InetSocketAddress
import com.sun.net.httpserver.{ HttpExchange, HttpHandler, HttpServer }

object HttpPutServer {
  private var server: HttpServer = null

  def start(port: Int, baseDir: java.io.File): Unit = {
    if (server != null) stop()
    server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/", new HttpHandler {
      override def handle(ex: HttpExchange): Unit = {
        val method = ex.getRequestMethod
        val path = ex.getRequestURI.getRawPath
        val relativePath = if (path.startsWith("/")) path.substring(1) else path
        val targetFile = new File(baseDir, relativePath.replace("/", File.separator))

        if ("PUT".equalsIgnoreCase(method)) {
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
        } else if ("GET".equalsIgnoreCase(method)) {
          val baseCanon = baseDir.getCanonicalPath + File.separator
          val fileCanon = targetFile.getCanonicalPath
          if (!fileCanon.startsWith(baseCanon)) {
            ex.sendResponseHeaders(403, -1)
            ex.close()
          } else if (targetFile.isFile) {
            val bytes = java.nio.file.Files.readAllBytes(targetFile.toPath)
            ex.sendResponseHeaders(200, bytes.length)
            val out = ex.getResponseBody
            try out.write(bytes)
            finally out.close()
            ex.close()
          } else {
            ex.sendResponseHeaders(404, -1)
            ex.close()
          }
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
