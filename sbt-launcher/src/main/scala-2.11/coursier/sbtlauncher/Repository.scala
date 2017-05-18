package coursier.sbtlauncher

import java.net.URL

object Repository {

  final case class Maven(id: String, url: URL) extends xsbti.MavenRepository

  final case class Ivy(
    id: String,
    url: URL,
    ivyPattern: String,
    artifactPattern: String,
    mavenCompatible: Boolean,
    skipConsistencyCheck: Boolean,
    descriptorOptional: Boolean
  ) extends xsbti.IvyRepository

}
