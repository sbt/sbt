/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.{ File, FileInputStream, IOException }
import java.net.{ HttpURLConnection, URL }
import org.apache.ivy.util.url.{ BasicURLHandler, IvyAuthenticator }
import org.apache.ivy.util.{ CopyProgressListener, FileUtil, Message }
import org.apache.ivy.Ivy
import scala.io.Source
import scala.util.Using

private[librarymanagement] class ErrorLoggingURLHandler extends BasicURLHandler {
  private val ErrorBodyTruncateLen = 1024

  override def upload(
      source: File,
      dest: URL,
      l: CopyProgressListener
  ): Unit = {
    if (dest.getProtocol != "http" && dest.getProtocol != "https") {
      throw new UnsupportedOperationException(
        "URL repository only support HTTP PUT at the moment"
      )
    }

    IvyAuthenticator.install()

    var conn: HttpURLConnection = null
    try {
      val normalizedDest = normalizeToURL(dest)
      conn = normalizedDest.openConnection().asInstanceOf[HttpURLConnection]
      conn.setDoOutput(true)
      conn.setRequestMethod("PUT")
      conn.setRequestProperty("User-Agent", "Apache Ivy/" + Ivy.getIvyVersion)
      conn.setRequestProperty(
        "Accept",
        "application/octet-stream, application/json, application/xml, */*"
      )
      conn.setRequestProperty("Content-type", "application/octet-stream")
      conn.setRequestProperty("Content-length", source.length().toString)
      conn.setInstanceFollowRedirects(true)

      val in = new FileInputStream(source)
      try {
        val os = conn.getOutputStream
        FileUtil.copy(in, os, l)
      } finally {
        try in.close()
        catch { case _: IOException => }
      }

      val responseCode = conn.getResponseCode
      val responseMessage = conn.getResponseMessage

      val errorBody = Option(conn.getErrorStream).map { stream =>
        Using.resource(stream) { s =>
          val body = Source.fromInputStream(s, "UTF-8").mkString
          if (body.length > ErrorBodyTruncateLen)
            body.take(ErrorBodyTruncateLen) + "..."
          else body
        }
      }

      errorBody.filter(_.nonEmpty).foreach { body =>
        Message.error(s"Server response body: $body")
      }

      validatePutStatusCode(dest, responseCode, responseMessage)
    } finally {
      if (conn != null) conn.disconnect()
    }
  }
}
