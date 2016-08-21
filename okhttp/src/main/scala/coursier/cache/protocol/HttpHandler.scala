package coursier.cache.protocol

import java.net.{URLStreamHandler, URLStreamHandlerFactory}

import com.squareup.okhttp.{OkHttpClient, OkUrlFactory}

object HttpHandler {
  lazy val okHttpClient = new OkHttpClient
  lazy val okHttpFactory = new OkUrlFactory(okHttpClient)
}

class HttpHandler extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler =
    HttpHandler.okHttpFactory.createURLStreamHandler(protocol)
}

class HttpsHandler extends HttpHandler
