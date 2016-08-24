package coursier.cache.protocol

import java.net.{ URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory }

class TestprotocolHandler extends URLStreamHandlerFactory {

  def createURLStreamHandler(protocol: String): URLStreamHandler = new URLStreamHandler {
    protected def openConnection(url: URL): URLConnection = {
      val resPath = "/test-repo/http/abc.com" + url.getPath
      val resURLOpt = Option(getClass.getResource(resPath))

      resURLOpt match {
        case None =>
          new URLConnection(url) {
            def connect() = throw new NoSuchElementException(s"Resource $resPath")
          }
        case Some(resURL) =>
          resURL.openConnection()
      }
    }
  }
}

object TestprotocolHandler {
  val protocol = "testprotocol"

  // get this namespace via a macro?
  val expectedClassName = s"coursier.cache.protocol.${protocol.capitalize}Handler"
  assert(classOf[TestprotocolHandler].getName == expectedClassName)
}