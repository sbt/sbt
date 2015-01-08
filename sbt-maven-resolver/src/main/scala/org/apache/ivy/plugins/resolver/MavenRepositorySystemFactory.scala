package org.apache.ivy.plugins.resolver

import java.net.URI

import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.url.URLResource
import org.apache.ivy.util.Message
import org.apache.ivy.util.url.URLHandlerRegistry
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.impl.{ MetadataGeneratorFactory, ArtifactDescriptorReader, RepositoryConnectorProvider, DefaultServiceLocator }
import org.eclipse.aether.metadata.Metadata
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.layout.{ RepositoryLayoutFactory, RepositoryLayoutProvider, RepositoryLayout }
import org.eclipse.aether.spi.connector.layout.RepositoryLayout.Checksum
import org.eclipse.aether.{
  RepositorySystem,
  RepositorySystemSession
}

import org.eclipse.aether.repository.{ RemoteRepository, LocalRepository }
import org.eclipse.aether.RepositorySystemSession
import org.apache.maven.repository.internal._
import org.eclipse.aether.spi.connector.transport._
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import java.io.File

/** Helper methods for dealing with starting up Aether. */
object MavenRepositorySystemFactory {
  def newRepositorySystemImpl: RepositorySystem = {
    // For now we just log Aether instantiation issues.  These should probably cause fatal errors.
    val locator = MavenRepositorySystemUtils.newServiceLocator()
    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler {
      override def serviceCreationFailed(tpe: Class[_], impl: Class[_], exception: Throwable): Unit = {
        Message.error(s"Failed to create $tpe, of class $impl")
      }
    })
    // Here we register the Ivy <-> Aether transport bridge
    locator.addService(classOf[TransporterFactory], classOf[MyTransportFactory])
    // This connects the download mechanism to our transports.  Why is it needed? no clue.
    locator.addService(classOf[RepositoryConnectorFactory], classOf[BasicRepositoryConnectorFactory])

    // Plugins cause issues here, as their layout is super odd.  Here we inject a new plugin layout
    locator.addService(classOf[RepositoryLayoutFactory], classOf[SbtPluginLayoutFactory])

    // Here we add the metadata services so aether will automatically add maven-metadata.xml files.
    locator.addService(classOf[MetadataGeneratorFactory], classOf[SnapshotMetadataGeneratorFactory])
    locator.addService(classOf[MetadataGeneratorFactory], classOf[VersionsMetadataGeneratorFactory])

    // Add our hook for parsing pom.xml files.
    locator.setService(classOf[ArtifactDescriptorReader], classOf[SbtArtifactDescriptorReader])
    // Finally, use the DI to create our repository system.
    locator.getService(classOf[RepositorySystem])
  }
  def newSessionImpl(system: RepositorySystem, localRepoDir: File): RepositorySystemSession = {
    val session = MavenRepositorySystemUtils.newSession()
    val localRepo = new LocalRepository(localRepoDir)
    session setLocalRepositoryManager (system.newLocalRepositoryManager(session, localRepo))
    // Here we set a descriptor policy that FORCES the pom.xml to exist, otherwise Ivy's resolution
    // algorithm freaks out.   What we could do is also do the ivy lame-thing of checking for a JAR
    // instead of a pom.xml, but let's see if this is actually a problem in practice.
    val descriptorPolicy = new org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy(
      /* ignoreMissing */ false, /* ignoreInvalid. */ true)
    session.setArtifactDescriptorPolicy(descriptorPolicy)
    session
  }

  def defaultLocalRepo: java.io.File = {
    new java.io.File(s"${sys.props("user.home")}/.m2/repository")
  }
}
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

/** Aether Http <-> Ivy Http adapter.   Aether's is better, but Ivy's has configuration hooks in sbt. */
class HttpTransport(repository: RemoteRepository) extends AbstractTransporter {
  class NotFoundException(msg: String) extends Exception(msg)
  private def toURL(task: TransportTask): java.net.URL =
    try new java.net.URL(s"${repository.getUrl}/${task.getLocation.toASCIIString}")
    catch {
      case e: IllegalArgumentException => throw new IllegalArgumentException(s" URL (${task.getLocation}) is not absolute.")
    }
  private def toResource(task: TransportTask): Resource = new URLResource(toURL(task))
  override def implPeek(peek: PeekTask): Unit = {
    if (!toResource(peek).exists()) throw new NotFoundException(s"Could not find ${peek.getLocation}")
  }
  override def implClose(): Unit = ()
  override def implGet(out: GetTask): Unit = {
    if (!toResource(out).exists()) throw new NotFoundException(s"Could not find ${out.getLocation}")
    URLHandlerRegistry.getDefault.download(toURL(out), out.getDataFile, null)
  }
  override def implPut(put: PutTask): Unit = {
    val to = toURL(put)
    Option(put.getDataFile) match {
      case Some(file) => URLHandlerRegistry.getDefault.upload(file, to, null)
      case None =>
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
      // TODO - Implement
      case _: NotFoundException => Transporter.ERROR_NOT_FOUND
      case _                    => Transporter.ERROR_OTHER
    }
}

class FileTransport(repository: RemoteRepository) extends AbstractTransporter {
  class NotFoundException(msg: String) extends Exception(msg)
  private def toURL(task: TransportTask): java.net.URL =
    try new java.net.URL(s"${repository.getUrl}/${task.getLocation.toASCIIString}")
    catch {
      case e: IllegalArgumentException => throw new IllegalArgumentException(s" URL (${task.getLocation}) is not absolute.")
    }
  private def toResource(task: TransportTask): Resource = new URLResource(toURL(task))
  private def toFile(task: TransportTask): java.io.File =
    new java.io.File(toURL(task).toURI)
  override def implPeek(peek: PeekTask): Unit = {
    if (!toFile(peek).exists()) throw new NotFoundException(s"Could not find ${peek.getLocation}")
  }
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
      // TODO - Implement
      case _: NotFoundException => Transporter.ERROR_NOT_FOUND
      case _                    => Transporter.ERROR_OTHER
    }
}