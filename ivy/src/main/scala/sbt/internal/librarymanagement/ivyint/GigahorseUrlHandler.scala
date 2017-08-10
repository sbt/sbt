package sbt.internal.librarymanagement
package ivyint

import java.net.{ URL, UnknownHostException, HttpURLConnection }
import java.io.{ File, IOException, InputStream, ByteArrayOutputStream, ByteArrayInputStream }
import org.apache.ivy.util.{ CopyProgressListener, Message, FileUtil }
import org.apache.ivy.util.url.{ URLHandler, AbstractURLHandler, BasicURLHandler, IvyAuthenticator }
import org.apache.ivy.util.url.URLHandler._
import sbt.io.{ IO, Using }

// Copied from Ivy's BasicURLHandler.
class GigahorseUrlHandler extends AbstractURLHandler {
  private val BUFFER_SIZE = 64 * 1024

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
    if ("http" == url0.getProtocol() || "https" == url0.getProtocol()) {
      IvyAuthenticator.install()
      ErrorMessageAuthenticator.install()
    }

    val url = normalizeToURL(url0)
    val con = GigahorseUrlHandler.open(url)
    val infoOption = try {
      con match {
        case httpCon: HttpURLConnection =>
          if (getRequestMethod == URLHandler.REQUEST_METHOD_HEAD) {
            httpCon.setRequestMethod("HEAD")
          }
          if (checkStatusCode(url, httpCon)) {
            val bodyCharset = BasicURLHandler.getCharSetFromContentType(con.getContentType)
            Some(
              new SbtUrlInfo(true,
                             httpCon.getContentLength.toLong,
                             con.getLastModified(),
                             bodyCharset))
          } else None

        case _ =>
          val contentLength = con.getContentLength
          if (contentLength <= 0) None
          else {
            // TODO: not HTTP... maybe we *don't* want to default to ISO-8559-1 here?
            val bodyCharset = BasicURLHandler.getCharSetFromContentType(con.getContentType)
            Some(new SbtUrlInfo(true, contentLength.toLong, con.getLastModified(), bodyCharset))
          }
      }
    } catch {
      case e: UnknownHostException =>
        Message.warn("Host " + e.getMessage() + " not found. url=" + url)
        Message.info(
          "You probably access the destination server through "
            + "a proxy server that is not well configured.")
        None
      case e: IOException =>
        Message.error("Server access Error: " + e.getMessage() + " url=" + url)
        None
    }
    infoOption.getOrElse(UNAVAILABLE)
  }

  def openStream(url0: URL): InputStream = {
    // Install the ErrorMessageAuthenticator
    if ("http" == url0.getProtocol() || "https" == url0.getProtocol()) {
      IvyAuthenticator.install()
      ErrorMessageAuthenticator.install()
    }

    val url = normalizeToURL(url0)
    val conn = GigahorseUrlHandler.open(url)
    conn.setRequestProperty("Accept-Encoding", "gzip,deflate")
    conn match {
      case httpCon: HttpURLConnection =>
        if (!checkStatusCode(url, httpCon)) {
          throw new IOException(
            "The HTTP response code for " + url + " did not indicate a success."
              + " See log for more detail.")
        }
      case _ =>
    }
    val inStream = getDecodingInputStream(conn.getContentEncoding(), conn.getInputStream())
    val outStream = new ByteArrayOutputStream()
    val buffer = new Array[Byte](BUFFER_SIZE)
    var len = 0
    while ({
      len = inStream.read(buffer)
      len > 0
    }) {
      outStream.write(buffer, 0, len)
    }
    new ByteArrayInputStream(outStream.toByteArray())
  }

  def download(src0: URL, dest: File, l: CopyProgressListener): Unit = {
    // Install the ErrorMessageAuthenticator
    if ("http" == src0.getProtocol() || "https" == src0.getProtocol()) {
      IvyAuthenticator.install()
      ErrorMessageAuthenticator.install()
    }

    val src = normalizeToURL(src0)
    val srcConn = GigahorseUrlHandler.open(src)
    srcConn.setRequestProperty("Accept-Encoding", "gzip,deflate")
    srcConn match {
      case httpCon: HttpURLConnection =>
        if (!checkStatusCode(src, httpCon)) {
          throw new IOException(
            "The HTTP response code for " + src + " did not indicate a success."
              + " See log for more detail.")
        }
      case _ =>
    }
    val inStream = getDecodingInputStream(srcConn.getContentEncoding(), srcConn.getInputStream())
    FileUtil.copy(inStream, dest, l)
    // check content length only if content was not encoded
    Option(srcConn.getContentEncoding) match {
      case None =>
        val contentLength = srcConn.getContentLength
        if (contentLength != -1 && dest.length != contentLength) {
          IO.delete(dest)
          throw new IOException(
            "Downloaded file size doesn't match expected Content Length for " + src
              + ". Please retry.")
        }
      case _ => ()
    }
    val lastModified = srcConn.getLastModified
    if (lastModified > 0) {
      dest.setLastModified(lastModified)
      ()
    }
    ()
  }

  def upload(source: File, dest0: URL, l: CopyProgressListener): Unit = {
    if (("http" != dest0.getProtocol()) && ("https" != dest0.getProtocol())) {
      throw new UnsupportedOperationException("URL repository only support HTTP PUT at the moment")
    }

    IvyAuthenticator.install()
    ErrorMessageAuthenticator.install()

    val dest = normalizeToURL(dest0)
    val conn = GigahorseUrlHandler.open(dest) match {
      case c: HttpURLConnection => c
    }
    conn.setDoOutput(true)
    conn.setRequestMethod("PUT")
    conn.setRequestProperty("Content-type", "application/octet-stream")
    conn.setRequestProperty("Content-length", source.length.toLong.toString)
    conn.setInstanceFollowRedirects(true)
    Using.fileInputStream(source) { in =>
      val os = conn.getOutputStream
      FileUtil.copy(in, os, l)
    }
    validatePutStatusCode(dest, conn.getResponseCode(), conn.getResponseMessage())
  }

  def checkStatusCode(url: URL, con: HttpURLConnection): Boolean =
    con.getResponseCode match {
      case 200                                   => true
      case 204 if "HEAD" == con.getRequestMethod => true
      case status =>
        Message.debug("HTTP response status: " + status + " url=" + url)
        if (status == 407 /* PROXY_AUTHENTICATION_REQUIRED */ ) {
          Message.warn("Your proxy requires authentication.");
        } else if (String.valueOf(status).startsWith("4")) {
          Message.verbose("CLIENT ERROR: " + con.getResponseMessage() + " url=" + url)
        } else if (String.valueOf(status).startsWith("5")) {
          Message.error("SERVER ERROR: " + con.getResponseMessage() + " url=" + url)
        }
        false
    }

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
}

object GigahorseUrlHandler {
  import gigahorse._, support.okhttp.Gigahorse
  import okhttp3.{ OkUrlFactory, OkHttpClient, JavaNetAuthenticator }

  lazy val http: HttpClient = Gigahorse.http(Gigahorse.config)

  private[sbt] def urlFactory = {
    val client0 = http.underlying[OkHttpClient]
    val client = client0
      .newBuilder()
      .authenticator(new JavaNetAuthenticator)
      .build
    new OkUrlFactory(client)
  }

  private[sbt] def open(url: URL): HttpURLConnection =
    urlFactory.open(url)
}
