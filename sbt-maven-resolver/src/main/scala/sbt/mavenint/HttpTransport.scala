package sbt.mavenint

import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.url.URLResource
import org.apache.ivy.util.url.URLHandlerRegistry
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport._

/** Aether Http <-> Ivy Http adapter.   Aether's is better, but Ivy's has configuration hooks in sbt. */
class HttpTransport(repository: RemoteRepository) extends AbstractTransporter {
  class NotFoundException(msg: String) extends Exception(msg)
  private def toURL(task: TransportTask): java.net.URL =
    try new java.net.URL(s"${repository.getUrl}/${task.getLocation.toASCIIString}")
    catch {
      case e: IllegalArgumentException =>
        throw new IllegalArgumentException(s" URL (${task.getLocation}) is not absolute.")
    }
  private def toResource(task: TransportTask): Resource = new URLResource(toURL(task))
  override def implPeek(peek: PeekTask): Unit =
    if (!toResource(peek).exists()) throw new NotFoundException(s"Could not find ${peek.getLocation}")
  override def implClose(): Unit = ()
  override def implGet(out: GetTask): Unit = {
    if (!toResource(out).exists()) throw new NotFoundException(s"Could not find ${out.getLocation}")
    URLHandlerRegistry.getDefault.download(toURL(out), out.getDataFile, null)
  }
  override def implPut(put: PutTask): Unit = {
    val to = toURL(put)
    Option(put.getDataFile) match {
      case Some(file) => URLHandlerRegistry.getDefault.upload(file, to, null)
      case None       =>
        // TODO - Ivy does not support uploading not from a file.  This isn't very efficient in ANY way,
        //        so if we rewrite the URL handler for Ivy we should fix this as well.
        sbt.IO.withTemporaryFile("tmp", "upload") { file =>
          val in = put.newInputStream()
          try sbt.IO.transfer(in, file)
          finally in.close()
          URLHandlerRegistry.getDefault.upload(file, to, null)
        }
    }
  }
  override def classify(err: Throwable): Int =
    err match {
      // TODO - Have we caught all the important exceptions here.
      case _: NotFoundException => Transporter.ERROR_NOT_FOUND
      case _                    => Transporter.ERROR_OTHER
    }
}
