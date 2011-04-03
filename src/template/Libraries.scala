import sbt._

case class ArtifactRevision(revision: String)
case class ArtifactConfig(confs: String, fn: ModuleID => ModuleID)
object ArtifactConfig {
  implicit def defaultArtifactConfig: ArtifactConfig = new ArtifactConfig("", identity[ModuleID])
  implicit def testArtifactConfig: ArtifactConfig = new ArtifactConfig("test", identity[ModuleID])
}

trait LowPriorityLibraries {
  self: DefaultProject =>
  
  // "latest.integration", "latest.milestone", "latest.release"
  def defaultRevision = "latest.integration"
  protected implicit def defaultArtifactRevision = new ArtifactRevision(defaultRevision)
  // protected implicit def defaultArtifactConfig   = new ArtifactConfig("", identity[ModuleID])
  
  protected implicit def autoConfig
    (artifact: GroupArtifactID)
    (implicit rev: ArtifactRevision, config: ArtifactConfig): ModuleID =
  {
    val ArtifactConfig(confs, fn) = config
    fn(
      if (confs == "") artifact % rev.revision
      else artifact % rev.revision % config.confs
    )
  }
}

trait TestLibraries extends LowPriorityLibraries {
  self: DefaultProject =>
  
  private implicit val testDepConfig = ArtifactConfig("test", _.withSources)
  val specs: ModuleID      = "org.scala-tools.testing" %% "specs"
  val scalacheck: ModuleID = "org.scala-tools.testing" %% "scalacheck"
}

trait Libraries extends Repositories with TestLibraries {
  self: DefaultProject =>
  
  import ArtifactConfig.defaultArtifactConfig
  
  // val ant: ModuleID             = "org.apache.ant" % "ant"
  // val asmAll: ModuleID          = "asm" % "asm-all" withSources()
  // val commonsVFS: ModuleID      = "org.apache.commons" % "commons-vfs-project"
  // val easymock: ModuleID        = "org.easymock" % "easymock"
  // val guava: ModuleID           = "com.google.guava" % "guava"
  // val ivy: ModuleID             = "org.apache.ivy" % "ivy"
  // val jdt: ModuleID             = "org.eclipse.jdt" % "core" notTransitive()
  // val jetty: ModuleID           = "org.mortbay.jetty" % "jetty"
  // val jmock: ModuleID           = "org.jmock" % "jmock"
  // val jodaTime: ModuleID        = "joda-time" % "joda-time"
  // val liftJson: ModuleID        = "net.liftweb" %% "lift-json" 
  // val maven: ModuleID           = "org.apache.maven" % "maven-ant-tasks"
  // val scalaARM: ModuleID        = "com.github.jsuereth.scala-arm" %% "scala-arm" withSources()
  // val scalaImproving: ModuleID  = "org.improving" %% "scala-improving"
  // val scalaSTM: ModuleID        = "org.scala-tools" %% "scala-stm" 
  // val scalariform: ModuleID     = "org.scalariform" %% "scalariform"
  // val scalazCore: ModuleID      = "org.scalaz" %% "scalaz-core" withSources()
  // val scalazHttp: ModuleID      = "org.scalaz" %% "scalaz-http" withSources()
  // val slf4s: ModuleID           = "com.weiglewilczek.slf4s" %% "slf4s" withSources()
}
