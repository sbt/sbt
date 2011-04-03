
trait ProjectSupport extends ModuleIdDynamifactory {
  self: DefaultProject =>
  
  /** Default "dynamic revision" to use with ivy.
   *  See [[http://www.jaya.free.fr/ivy/doc/ivyfile/dependency.html]].
   *  Likely alternatives: latest.milestone, latest.release
   */
  def dynamicRevision = "latest.integration"
  
  /** Repositories.  Comment in or out to taste.
   */
  val localMaven          = "Local Maven" at "file://"+Path.userHome+"/.m2/repository"
  val localIvy            = "Local Ivy" at "file://"+Path.userHome+"/.ivy2/local"
  val sonatype            = "Sonatype" at "https://oss.sonatype.org/content/groups/public"
  val scalaToolsSnapshots = "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"
  val jboss               = "JBoss Repo" at "http://repository.jboss.org/maven2"

  private val testConfig: ArtifactConfig = ArtifactConfig(
    ArtifactRevision(_ => dynamicRevision), ArtifactTransform(inScope("test"), withSources)
  )

  /*** Libraries ***/
  val specs: ModuleID           = testConfig("org.scala-tools.testing" %% "specs")
  val scalacheck: ModuleID      = testConfig("org.scala-tools.testing" %% "scalacheck")

  private implicit lazy val implicitTransform: ArtifactTransform = ArtifactTransform()

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

trait ModuleIdDynamifactory extends Dynamifactory {
  self: DefaultProject =>
  
  protected type DepId = GroupArtifactID
  protected type DepOut = ModuleID
  protected def finishDependency(in: GroupArtifactID, revision: String): ModuleID = in % revision

  protected implicit lazy val implicitRevision: ArtifactRevision =
    ArtifactRevision(_ => dynamicRevision)
  
  protected def inScope(scope: String): DepFn = _ % scope
  protected def withSources: DepFn            = _.withSources()  
  protected def intransitive: DepFn           = _.intransitive()
  protected def withJavadoc: DepFn            = _.withJavadoc

  protected def withRevision(newRevision: String): DepFn = (m: ModuleID) => {
    ModuleID(m.organization, m.name, newRevision, m.configurations, m.isChanging, m.isTransitive, m.explicitArtifacts, m.extraAttributes)
  }
}

trait Dynamifactory {
  protected type DepId
  protected type DepOut
  protected type DepFn = DepOut => DepOut
  protected def dynamicRevision: String
  protected def finishDependency(in: DepId, revision: String): DepOut

  case class ArtifactRevision(revisionFn: DepId => String) {
  }
  case class ArtifactTransform(fns: DepFn*) {
    def apply(x: DepOut): DepOut = if (fns.isEmpty) x else fns.reduceLeft(_ andThen _)(x)
  }
  case class ArtifactConfig(rev: ArtifactRevision, transform: ArtifactTransform) {
    def apply(in: DepId): DepOut = transform(finishDependency(in, rev.revisionFn(in)))
  }
  
  protected implicit def autoassembleConfig(implicit rev: ArtifactRevision, transform: ArtifactTransform): ArtifactConfig =
    ArtifactConfig(rev, transform)

  protected implicit def autoconfigureDependencies(in: DepId)(implicit config: ArtifactConfig): DepOut = config(in)
}
