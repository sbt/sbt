package sbt.mavenint

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.{ Transporter, TransporterFactory }

/** Override aether's default transport with Ivy-ones. */
class MyTransportFactory extends TransporterFactory {
  override def newInstance(session: RepositorySystemSession, repository: RemoteRepository): Transporter =
    repository.getProtocol match {
      case "http" | "https" => new HttpTransport(repository)
      case "file"           => new FileTransport(repository)
      case other            => throw new IllegalArgumentException(s"Unsupported transport protocol: $other")
    }
  override def getPriority: Float = 1.0f
}