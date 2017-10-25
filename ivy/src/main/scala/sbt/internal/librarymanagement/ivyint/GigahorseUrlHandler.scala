package sbt.internal.librarymanagement
package ivyint

import java.net.{ HttpURLConnection, URL, UnknownHostException }
import java.io._

import scala.util.control.NonFatal

import okhttp3.{ MediaType, OkUrlFactory, Request, RequestBody }
import okhttp3.internal.http.HttpDate

import okhttp3.{ JavaNetAuthenticator => _, _ }
import okio._

import org.apache.ivy.util.{ CopyProgressEvent, CopyProgressListener, Message }
import org.apache.ivy.util.url.{ AbstractURLHandler, BasicURLHandler, IvyAuthenticator, URLHandler }
import org.apache.ivy.util.url.URLHandler._
import sbt.io.IO

// Copied from Ivy's BasicURLHandler.
class GigahorseUrlHandler extends AbstractURLHandler {

  import GigahorseUrlHandler._

  /**
   * Returns the URLInfo of the given url or a #UNAVAILABLE instance,
   * if the url is not reachable.
   */
  def getURLInfo(url: URL): URLInfo = getURLInfo(url, 0)

  /**
   * Returns the URLInfo of the given url or a #UNAVAILABLE instance,
   * if the url is not reachable.
   */
  def getURLInfo(url0: URL, timeout: Int): URLInfo = {
    // Install the ErrorMessageAuthenticator
    if ("http" == url0.getProtocol || "https" == url0.getProtocol) {
      IvyAuthenticator.install()
      ErrorMessageAuthenticator.install()
    }

    val url = normalizeToURL(url0)
    val request = new Request.Builder()
      .url(url)

    if (getRequestMethod == URLHandler.REQUEST_METHOD_HEAD) request.head() else request.get()

    val response = okHttpClient.newCall(request.build()).execute()
    try {
      val infoOption = try {

        if (checkStatusCode(url, response)) {
          val bodyCharset =
            BasicURLHandler.getCharSetFromContentType(response.body().contentType().toString)
          Some(
            new SbtUrlInfo(true,
                           response.body().contentLength(),
                           lastModifiedTimestamp(response),
                           bodyCharset))
        } else None
        //
        //      Commented out for now - can potentially be used for non HTTP urls
        //
        //      val contentLength: Long = con.getContentLengthLong
        //      if (contentLength <= 0) None
        //      else {
        //        // TODO: not HTTP... maybe we *don't* want to default to ISO-8559-1 here?
        //        val bodyCharset = BasicURLHandler.getCharSetFromContentType(con.getContentType)
        //        Some(new SbtUrlInfo(true, contentLength, con.getLastModified(), bodyCharset))
        //      }

      } catch {
        case e: UnknownHostException =>
          Message.warn("Host " + e.getMessage + " not found. url=" + url)
          Message.info(
            "You probably access the destination server through "
              + "a proxy server that is not well configured.")
          None
        case e: IOException =>
          Message.error("Server access Error: " + e.getMessage + " url=" + url)
          None
      }
      infoOption.getOrElse(UNAVAILABLE)
    } finally {
      response.close()
    }
  }

  //The caller of this *MUST* call Response.close()
  private def getUrl(url0: URL): okhttp3.Response = {
    // Install the ErrorMessageAuthenticator
    if ("http" == url0.getProtocol || "https" == url0.getProtocol) {
      IvyAuthenticator.install()
      ErrorMessageAuthenticator.install()
    }

    val url = normalizeToURL(url0)
    val request = new Request.Builder()
      .url(url)
      .get()
      .build()

    val response = okHttpClient.newCall(request).execute()
    try {
      if (!checkStatusCode(url, response)) {
        throw new IOException(
          "The HTTP response code for " + url + " did not indicate a success."
            + " See log for more detail.")
      }
      response
    } catch {
      case NonFatal(e) =>
        //ensure the response gets closed if there's an error
        response.close()
        throw e
    }

  }

  def openStream(url: URL): InputStream = {
    //It's assumed that the caller of this will call close() on the supplied inputstream,
    // thus closing the OkHTTP request
    getUrl(url).body().byteStream()
  }

  def download(url: URL, dest: File, l: CopyProgressListener): Unit = {

    val response = getUrl(url)
    try {

      if (l != null) {
        l.start(new CopyProgressEvent())
      }
      val sink = Okio.buffer(Okio.sink(dest))
      try {
        sink.writeAll(response.body().source())
        sink.flush()
      } finally {
        sink.close()
      }

      val contentLength = response.body().contentLength()
      if (contentLength != -1 && dest.length != contentLength) {
        IO.delete(dest)
        throw new IOException(
          "Downloaded file size doesn't match expected Content Length for " + url
            + ". Please retry.")
      }

      val lastModified = lastModifiedTimestamp(response)
      if (lastModified > 0) {
        dest.setLastModified(lastModified)
      }

      if (l != null) {
        l.end(new CopyProgressEvent(EmptyBuffer, contentLength))
      }

    } finally {
      response.close()
    }
  }

  def upload(source: File, dest0: URL, l: CopyProgressListener): Unit = {

    if (("http" != dest0.getProtocol) && ("https" != dest0.getProtocol)) {
      throw new UnsupportedOperationException("URL repository only support HTTP PUT at the moment")
    }

    IvyAuthenticator.install()
    ErrorMessageAuthenticator.install()

    val dest = normalizeToURL(dest0)

    val body = RequestBody.create(MediaType.parse("application/octet-stream"), source)

    val request = new Request.Builder()
      .url(dest)
      .put(body)
      .build()

    if (l != null) {
      l.start(new CopyProgressEvent())
    }
    val response = okHttpClient.newCall(request).execute()
    try {
      if (l != null) {
        l.end(new CopyProgressEvent(EmptyBuffer, source.length()))
      }
      validatePutStatusCode(dest, response.code(), response.message())
    } finally {
      response.close()
    }
  }

}

object GigahorseUrlHandler {
  import gigahorse.HttpClient
  import gigahorse.support.okhttp.Gigahorse
  import okhttp3.OkHttpClient

  // This is requires to access the constructor of URLInfo.
  private[sbt] class SbtUrlInfo(available: Boolean,
                                contentLength: Long,
                                lastModified: Long,
                                bodyCharset: String)
      extends URLInfo(available, contentLength, lastModified, bodyCharset) {
    def this(available: Boolean, contentLength: Long, lastModified: Long) = {
      this(available, contentLength, lastModified, null)
    }
  }

  private val EmptyBuffer: Array[Byte] = new Array[Byte](0)

  lazy val http: HttpClient = Gigahorse.http(Gigahorse.config)

  private lazy val okHttpClient: OkHttpClient = {
    http
      .underlying[OkHttpClient]
      .newBuilder()
      .authenticator(new sbt.internal.librarymanagement.JavaNetAuthenticator)
      .followRedirects(true)
      .followSslRedirects(true)
      .build
  }

  @deprecated("Use the Gigahorse HttpClient directly instead.", "librarymanagement-ivy 1.0.1")
  private[sbt] def urlFactory = {
    new OkUrlFactory(okHttpClient)
  }

  @deprecated("Use the Gigahorse HttpClient directly instead.", "librarymanagement-ivy 1.0.1")
  private[sbt] def open(url: URL): HttpURLConnection =
    urlFactory.open(url)

  private def checkStatusCode(url: URL, response: Response): Boolean =
    response.code() match {
      case 200                                          => true
      case 204 if "HEAD" == response.request().method() => true
      case status =>
        Message.debug("HTTP response status: " + status + " url=" + url)
        if (status == 407 /* PROXY_AUTHENTICATION_REQUIRED */ ) {
          Message.warn("Your proxy requires authentication.")
        } else if (String.valueOf(status).startsWith("4")) {
          Message.verbose("CLIENT ERROR: " + response.message() + " url=" + url)
        } else if (String.valueOf(status).startsWith("5")) {
          Message.error("SERVER ERROR: " + response.message() + " url=" + url)
        }
        false
    }

  private def lastModifiedTimestamp(response: Response): Long = {
    val lastModifiedDate =
      Option(response.headers().get("Last-Modified")).flatMap { headerValue =>
        Option(HttpDate.parse(headerValue))
      }

    lastModifiedDate.map(_.getTime).getOrElse(0)
  }

}
