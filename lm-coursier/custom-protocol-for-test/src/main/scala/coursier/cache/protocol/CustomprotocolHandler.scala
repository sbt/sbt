package coursier.cache.protocol

import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}

class CustomprotocolHandler extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler = new URLStreamHandler {
    protected def openConnection(url: URL): URLConnection =
      new URL("https://repo1.maven.org/maven2" + url.getPath()).openConnection()
  }
}
