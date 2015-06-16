package coursier

package object repository {

  type Remote = core.Remote
  val Remote: core.Remote.type = core.Remote

  val mavenCentral = Remote("https://repo1.maven.org/maven2/")

  val sonatypeReleases = Remote("https://oss.sonatype.org/content/repositories/releases/")
  val sonatypeSnapshots = Remote("https://oss.sonatype.org/content/repositories/snapshots/")

}
