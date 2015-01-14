package sbt.mavenint

import java.io.File

import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.url.URLResource
import org.apache.ivy.util.Message
import org.apache.ivy.util.url.URLHandlerRegistry
import org.apache.maven.repository.internal.{ MavenRepositorySystemUtils, SbtArtifactDescriptorReader, SnapshotMetadataGeneratorFactory, VersionsMetadataGeneratorFactory }
import org.eclipse.aether.{ RepositorySystem, RepositorySystemSession }
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.{ ArtifactDescriptorReader, DefaultServiceLocator, MetadataGeneratorFactory }
import org.eclipse.aether.repository.{ LocalRepository, RemoteRepository }
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory
import org.eclipse.aether.spi.connector.transport.{ TransporterFactory, _ }

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

