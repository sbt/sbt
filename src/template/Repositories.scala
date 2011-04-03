import sbt._

trait Repositories extends DefaultProject {
  // self: DefaultProject =>
  
  val localMaven          = "Local Maven" at "file://"+Path.userHome+"/.m2/repository"
  val localIvy            = "Local Ivy" at "file://"+Path.userHome+"/.ivy2/local"
  val sonatype            = "Sonatype" at "https://oss.sonatype.org/content/groups/public"
  val scalaToolsSnapshots = "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"
  val jboss               = "JBoss Repo" at "http://repository.jboss.org/maven2"
  
  // -Dscala.local=2.9.0.local=/scala/trunk/build/pack
  override def localScala = System.getProperty("scala.local") match {
    case null   => super.localScala
    case str    =>
      val (name, path) = str indexOf '=' match {
        case -1   => ("local", str)
        case idx  => (str take idx toString, str drop idx + 1 toString)
      }
      log.info("Found scala.local setting '" + name + "' at: " + path)
      List(defineScala(name, new java.io.File(path)))
  }
}
