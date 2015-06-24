package coursier

import coursier.core.DefaultFetchMetadata

package object repository {

  val mavenCentral = MavenRepository(DefaultFetchMetadata("https://repo1.maven.org/maven2/"))

  val sonatypeReleases = MavenRepository(DefaultFetchMetadata("https://oss.sonatype.org/content/repositories/releases/"))
  val sonatypeSnapshots = MavenRepository(DefaultFetchMetadata("https://oss.sonatype.org/content/repositories/snapshots/"))

}
