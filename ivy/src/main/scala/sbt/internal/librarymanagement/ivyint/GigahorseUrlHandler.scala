package sbt.internal.librarymanagement
package ivyint

import java.net.{ URL, UnknownHostException }
import java.io._

import scala.util.control.NonFatal

import okhttp3.{ MediaType, Request, RequestBody }
import okhttp3.internal.http.HttpDate

import okhttp3.{ JavaNetAuthenticator => _, _ }
import okio._

import org.apache.ivy.util.{ CopyProgressEvent, CopyProgressListener, Message }
import org.apache.ivy.util.url.{ AbstractURLHandler, BasicURLHandler, IvyAuthenticator, URLHandler }
import org.apache.ivy.util.url.URLHandler._
import sbt.io.IO

// Copied from Ivy's BasicURLHandler.
class GigahorseUrlHandler(http: OkHttpClient) extends AbstractURLHandler {

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

    val response = http.newCall(request.build()).execute()
    try {
      val infoOption = try {

        if (checkStatusCode(url, response)) {
          val bodyCharset =
            BasicURLHandler.getCharSetFromContentType(
              Option(response.body().contentType()).map(_.toString).orNull
            )
          Some(
            new SbtUrlInfo(
              true,
              response.body().contentLength(),
              lastModifiedTimestamp(response),
              bodyCharset
            )
          )
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
              + "a proxy server that is not well configured."
          )
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

    val response = http.newCall(request).execute()
    try {
      if (!checkStatusCode(url, response)) {
        throw new IOException(
          "The HTTP response code for " + url + " did not indicate a success."
            + " See log for more detail."
        )
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
            + ". Please retry."
        )
      }

      val lastModified = lastModifiedTimestamp(response)
      if (lastModified > 0) {
        IO.setModifiedTimeOrFalse(dest, lastModified)
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
    val response = http.newCall(request).execute()
    try {
      if (l != null) {
        l.end(new CopyProgressEvent(EmptyBuffer, source.length()))
      }
      validatePutStatusCode(dest, response)
    } finally {
      response.close()
    }
  }

  private val ErrorBodyTruncateLen = 512 // in case some bad service returns files rather than messages in error bodies
  private val DefaultErrorCharset = java.nio.charset.StandardCharsets.UTF_8

  // this is perhaps overly cautious, but oh well
  private def readTruncated(is: InputStream): Option[(Array[Byte], Boolean)] = {
    val os = new ByteArrayOutputStream(ErrorBodyTruncateLen)
    var count = 0
    var b = is.read()
    var truncated = false
    while (!truncated && b >= 0) {
      if (count >= ErrorBodyTruncateLen) {
        truncated = true
      } else {
        os.write(b)
        count += 1
        b = is.read()
      }
    }
    if (count > 0) {
      Some((os.toByteArray, truncated))
    } else {
      None
    }
  }

  /*
   * Supplements the IOException emitted on a bad status code by our inherited validatePutStatusCode(...)
   * method with any message that might be present in an error response body.
   *
   * after calling this method, the object given as the response parameter must be reliably closed.
   */
  private def validatePutStatusCode(dest: URL, response: Response): Unit = {
    try {
      validatePutStatusCode(dest, response.code(), response.message())
    } catch {
      case ioe: IOException => {
        val mbBodyMessage = {
          for {
            body <- Option(response.body())
            is <- Option(body.byteStream)
            (bytes, truncated) <- readTruncated(is)
            charset <- Option(body.contentType()).map(_.charset(DefaultErrorCharset)) orElse Some(
              DefaultErrorCharset
            )
          } yield {
            val raw = new String(bytes, charset)
            if (truncated) raw + "..." else raw
          }
        }

        mbBodyMessage match {
          case Some(bodyMessage) => { // reconstruct the IOException
            val newMessage = ioe.getMessage() + s"; Response Body: ${bodyMessage}"
            val reconstructed = new IOException(newMessage, ioe.getCause())
            reconstructed.setStackTrace(ioe.getStackTrace())
            throw reconstructed
          }
          case None => {
            throw ioe
          }
        }
      }
    }
  }
}

object GigahorseUrlHandler {
  // This is requires to access the constructor of URLInfo.
  private[sbt] class SbtUrlInfo(
      available: Boolean,
      contentLength: Long,
      lastModified: Long,
      bodyCharset: String
  ) extends URLInfo(available, contentLength, lastModified, bodyCharset) {
    def this(available: Boolean, contentLength: Long, lastModified: Long) = {
      this(available, contentLength, lastModified, null)
    }
  }

  private val EmptyBuffer: Array[Byte] = new Array[Byte](0)

  private def checkStatusCode(url: URL, response: Response): Boolean =
    response.code() match {
      case 200                                          => true
      case 204 if "HEAD" == response.request().method() => true
      case status =>
        Message.debug("HTTP response status: " + status + " url=" + url)
        if (status == 407 /* PROXY_AUTHENTICATION_REQUIRED */ ) {
          Message.warn("Your proxy requires authentication.")
        } else if (status == 401) {
          Message.warn(
            "CLIENT ERROR: 401 Unauthorized. Check your resolvers username and password."
          )
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
