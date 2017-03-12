package sbt.mavenint

import java.net.URI

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.metadata.Metadata
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.layout.RepositoryLayout.Checksum
import org.eclipse.aether.spi.connector.layout.{ RepositoryLayout, RepositoryLayoutFactory }
import org.eclipse.aether.transfer.NoRepositoryLayoutException

import scala.util.matching.Regex

/** A factory which knows how to create repository layouts which can find sbt plugins. */
class SbtPluginLayoutFactory extends RepositoryLayoutFactory {
  def newInstance(session: RepositorySystemSession, repository: RemoteRepository): RepositoryLayout =
    repository.getContentType match {
      case SbtRepositoryLayout.LAYOUT_NAME =>
        SbtRepositoryLayout
      case _ => throw new NoRepositoryLayoutException(repository, "Not an sbt-plugin repository")
    }
  def getPriority: Float = 100.0f
}

object SbtRepositoryLayout extends RepositoryLayout {

  val LAYOUT_NAME = "sbt-plugin"

  // get location is ALMOST the same for Metadata + artifact... but subtle differences are important.

  def getLocation(artifact: Artifact, upload: Boolean): URI = {
    val sbtVersion = Option(artifact.getProperties.get(SbtPomExtraProperties.POM_SBT_VERSION))
    val scalaVersion = Option(artifact.getProperties.get(SbtPomExtraProperties.POM_SCALA_VERSION))
    val path = new StringBuilder(128)
    path.append(artifact.getGroupId.replace('.', '/')).append('/')
    (sbtVersion zip scalaVersion).headOption match {
      case Some((sbt, scala)) =>
        if (artifact.getArtifactId contains "_sbt_") {
          val SbtNameVersionSplit(name, sbt2) = artifact.getArtifactId
          path.append(name).append('_').append(scala).append('_').append(sbt).append('/')
        } else
          path.append(artifact.getArtifactId).append('_').append(scala).append('_').append(sbt).append('/')
      case None =>
        // TODO - Should we automatically append the _<scala-verison> here if it's not there?  Probably not for now.
        path.append(artifact.getArtifactId).append('/')
    }
    path.append(artifact.getBaseVersion).append('/')
    sbtVersion match {
      case Some(_) if artifact.getArtifactId contains "_sbt_" =>
        val SbtNameVersionSplit(name, sbt2) = artifact.getArtifactId
        path.append(name).append('-').append(artifact.getVersion)
      case None => path.append(artifact.getArtifactId).append('-').append(artifact.getVersion)
    }

    if (artifact.getClassifier != null && !artifact.getClassifier.trim.isEmpty) {
      path.append("-").append(artifact.getClassifier)
    }
    if (artifact.getExtension.length > 0) {
      path.append('.').append(artifact.getExtension)
    }
    URI.create(path.toString())
  }

  // Trickery for disambiguating sbt plugins in maven repositories.
  val SbtNameVersionSplit = new Regex("(.*)_sbt_(.*)")

  def getLocation(metadata: Metadata, upload: Boolean): URI = {
    val sbtVersion = Option(metadata.getProperties.get(SbtPomExtraProperties.POM_SBT_VERSION))
    val scalaVersion = Option(metadata.getProperties.get(SbtPomExtraProperties.POM_SCALA_VERSION))
    val path = new StringBuilder(128)
    path.append(metadata.getGroupId.replace('.', '/')).append('/')
    (sbtVersion zip scalaVersion).headOption match {
      case Some((sbt, scala)) =>
        if (metadata.getArtifactId contains "_sbt_") {
          val SbtNameVersionSplit(name, sbt2) = metadata.getArtifactId
          path.append(name).append('_').append(scala).append('_').append(sbt).append('/')
        } else
          path.append(metadata.getArtifactId).append('_').append(scala).append('_').append(sbt).append('/')
      case None =>
        // TODO - Should we automatically append the _<scala-verison> here?  Proabbly not for now.
        path.append(metadata.getArtifactId).append('/')
    }
    if (metadata.getVersion.length > 0)
      path.append(metadata.getVersion).append('/')
    path.append(metadata.getType)
    URI.create(path.toString)
  }

  // TODO - This should be the same as configured from Ivy...
  def getChecksums(artifact: Artifact, upload: Boolean, location: URI): java.util.List[Checksum] =
    getChecksums(location)
  def getChecksums(metadata: Metadata, upload: Boolean, location: URI): java.util.List[Checksum] =
    getChecksums(location)

  private def getChecksums(location: URI): java.util.List[Checksum] =
    java.util.Arrays.asList(Checksum.forLocation(location, "SHA-1"), Checksum.forLocation(location, "MD5"))
}
