package coursier

import org.http4s.Http4s._

package object repository {

  type Remote = core.Remote
  val Remote: core.Remote.type = core.Remote

  val mavenCentral = Remote(uri("https://repo1.maven.org/maven2/"))

  val sonatypeReleases = Remote(uri("https://oss.sonatype.org/content/repositories/releases/"))
  val sonatypeSnapshots = Remote(uri("https://oss.sonatype.org/content/repositories/snapshots/"))

}
