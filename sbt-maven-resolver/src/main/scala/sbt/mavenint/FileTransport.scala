package sbt.mavenint

import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.url.URLResource
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport._

/**
  * A bridge file transportation protocol which uses some Ivy/sbt mechanisms.
  */
class FileTransport(repository: RemoteRepository) extends AbstractTransporter {
  class NotFoundException(msg: String) extends Exception(msg)
  private def toURL(task: TransportTask): java.net.URL =
    try new java.net.URL(s"${repository.getUrl}/${task.getLocation.toASCIIString}")
    catch {
      case e: IllegalArgumentException =>
        throw new IllegalArgumentException(s" URL (${task.getLocation}) is not absolute.")
    }
  private def toResource(task: TransportTask): Resource = new URLResource(toURL(task))
  private def toFile(task: TransportTask): java.io.File =
    new java.io.File(toURL(task).toURI)
  override def implPeek(peek: PeekTask): Unit =
    if (!toFile(peek).exists()) throw new NotFoundException(s"Could not find ${peek.getLocation}")
  override def implClose(): Unit = ()
  override def implGet(out: GetTask): Unit = {
    val from = toFile(out)
    if (!from.exists()) throw new NotFoundException(s"Could not find ${out.getLocation}")
    sbt.IO.copyFile(from, out.getDataFile, true)
  }
  override def implPut(put: PutTask): Unit = {
    val to = toFile(put)
    Option(put.getDataFile) match {
      case Some(from) =>
        sbt.IO.copyFile(from, to, true)
      case None =>
        // Here it's most likely a SHA or somethign where we read from memory.
        val in = put.newInputStream
        try sbt.IO.transfer(in, to)
        finally in.close()
    }
  }
  override def classify(err: Throwable): Int =
    err match {
      // TODO - Have we caught enough exceptions here?
      case _: NotFoundException => Transporter.ERROR_NOT_FOUND
      case _                    => Transporter.ERROR_OTHER
    }
}
