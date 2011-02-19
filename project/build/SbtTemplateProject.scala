import sbt._

class SbttemplateProject(info: ProjectInfo) extends DefaultProject(info) with Libraries {  
  // -Dscala.local=/path/to/scala/build
  override def localScala = System.getenv("scala.local") match {
    case null   => super.localScala
    case path   => 
      log.info("Found scala.local: " + path)
      List(defineScala("2.9.0-local", new java.io.File(path)))
  }  
}
