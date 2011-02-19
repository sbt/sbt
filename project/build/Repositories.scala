import sbt._

trait Repositories {
  self: DefaultProject =>
  
  val localMaven          = "Local Maven" at "file://"+Path.userHome+"/.m2/repository"
  val localIvy            = "Local Ivy" at "file://"+Path.userHome+"/.ivy2/local"
  val sonatype            = "Sonatype" at "https://oss.sonatype.org/content/groups/public"
  val scalaToolsSnapshots = "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"
  val jboss               = "JBoss Repo" at "http://repository.jboss.org/maven2"
}
