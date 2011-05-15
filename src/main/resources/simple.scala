
trait ProjectSupport {
  self: DefaultProject =>
  
  /** Repositories.  Comment in or out to taste.
   */
  val localMaven          = "Local Maven" at "file://"+Path.userHome+"/.m2/repository"
  val localIvy            = "Local Ivy" at "file://"+Path.userHome+"/.ivy2/local"
  val sonatype            = "Sonatype" at "https://oss.sonatype.org/content/groups/public"
  val scalaToolsSnapshots = "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"
  val jboss               = "JBoss Repo" at "http://repository.jboss.org/maven2"
  // val akkaReleases        = "Akka Maven Repository" at "http://scalablesolutions.se/akka/repository"

  /*** Libraries ***/
  val specs: ModuleID      = "org.scala-tools.testing" %% "specs" % "1.6.8"
  val scalacheck: ModuleID = "org.scala-tools.testing" %% "scalacheck" % "1.9"
  // val specs2: ModuleID     = testConfig("org.specs2" %% "specs2")  
  // val ant: ModuleID             = "org.apache.ant" % "ant"
  // val jdt: ModuleID             = "org.eclipse.jdt" % "core" notTransitive()
  // val scalaImproving: ModuleID  = "org.improving" %% "scala-improving"
  // val scalaSTM: ModuleID        = "org.scala-tools" %% "scala-stm" 
  // val scalariform: ModuleID     = "org.scalariform" %% "scalariform"
  // 
  // val asmAll: ModuleID          = "asm" % "asm-all" % "3.3.1" withSources()
  // val easymock: ModuleID        = "org.easymock" % "easymock"
  // val guava: ModuleID           = "com.google.guava" % "guava"
  // val ivy: ModuleID             = "org.apache.ivy" % "ivy"
  // val jetty: ModuleID           = "org.mortbay.jetty" % "jetty"
  // val jmock: ModuleID           = "org.jmock" % "jmock"
  // val jodaTime: ModuleID        = "joda-time" % "joda-time"
  // val liftJson: ModuleID        = "net.liftweb" %% "lift-json" 
  // val maven: ModuleID           = "org.apache.maven" % "maven-ant-tasks"
  // val scalaARM: ModuleID        = "com.github.jsuereth.scala-arm" %% "scala-arm" withSources()
  // val scalazCore: ModuleID      = "org.scalaz" %% "scalaz-core" withSources()
  // val scalazHttp: ModuleID      = "org.scalaz" %% "scalaz-http" withSources()
  // val slf4s: ModuleID           = "com.weiglewilczek.slf4s" %% "slf4s" withSources()
}
