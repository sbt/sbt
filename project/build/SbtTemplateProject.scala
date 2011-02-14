import sbt._

class SbtTemplateProject(info: ProjectInfo) extends DefaultProject(info) {
  val localMaven   = "Local Maven" at "file://"+Path.userHome+"/.m2/repository"
  val localIvy     = "Local Ivy" at "file://"+Path.userHome+"/.ivy2/local"
  val sonatype     = "Sonatype" at "https://oss.sonatype.org/content/groups/public"
  
  // local use
  override def localScala = System.getenv("scala.local") match {
    case null   => super.localScala
    case path   => 
      log.info("Found scala.local: " + path)
      List(defineScala("2.9.0-local", new java.io.File(path)))
  }

  val scalacheck = "org.scala-tools.testing" %% "scalacheck" % "1.8" % "test" withSources()
  val specs      = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test" withSources()
}
