import sbt._
import Keys._

object Dependencies {
  val scala282 = "2.8.2"
  val scala292 = "2.9.2"
  val scala293 = "2.9.3"
  val scala210 = "2.10.6"
  val scala211 = "2.11.8"
  val scala212 = "2.12.2"
  val baseScalaVersion = scala212

  // sbt modules
  private val ioVersion = "1.0.0-M11"
  private val utilVersion = "1.0.0-M24"
  private val lmVersion = "1.0.0-X15"
  private val zincVersion = "1.0.0-X16"

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val lmPosition = "org.scala-sbt" %% "lm-position" % lmVersion
  private val lmLogging = "org.scala-sbt" %% "lm-logging" % lmVersion
  private val lmCache = "org.scala-sbt" %% "lm-cache" % lmVersion
  private val libraryManagement = "org.scala-sbt" %% "librarymanagement" % lmVersion

  private val zincControl = "org.scala-sbt" %% "zinc-control" % zincVersion
  private val zincRelation = "org.scala-sbt" %% "zinc-relation" % zincVersion
  private val zincUtilScripted = "org.scala-sbt" %% "zinc-scripted" % zincVersion
  private val zincTracking = "org.scala-sbt" %% "zinc-tracking" % zincVersion

  val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"
  val rawLauncher = "org.scala-sbt" % "launcher" % "1.0.0"
  val testInterface = "org.scala-sbt" % "test-interface" % "1.0"

  private val compilerApiInfo = "org.scala-sbt" %% "zinc-apiinfo" % zincVersion
  private val compilerBridge = "org.scala-sbt" %% "compiler-bridge" % zincVersion
  private val compilerClasspath = "org.scala-sbt" %% "zinc-classpath" % zincVersion
  private val compilerInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  private val compilerIvyIntegration = "org.scala-sbt" %% "zinc-ivy-integration" % zincVersion
  private val zinc = "org.scala-sbt" %% "zinc" % zincVersion
  private val zincCompile = "org.scala-sbt" %% "zinc-compile" % zincVersion

  def getSbtModulePath(key: String, name: String) = {
    val localProps = new java.util.Properties()
    IO.load(localProps, file("project/local.properties"))
    val path = Option(localProps getProperty key) orElse (sys.props get key)
    path foreach (f => println(s"Using $name from $f"))
    path
  }

  lazy val sbtIoPath = getSbtModulePath("sbtio.path", "sbt/io")
  lazy val sbtLmPath = getSbtModulePath("sbtlm.path", "sbt/lm")
  lazy val sbtZincPath = getSbtModulePath("sbtzinc.path", "sbt/zinc")

  def addSbtModule(p: Project, path: Option[String], projectName: String, m: ModuleID) =
    path match {
      case Some(f) => p dependsOn ProjectRef(file(f), projectName)
      case None    => p settings (libraryDependencies += m)
    }

  def addSbtIO(p: Project): Project = addSbtModule(p, sbtIoPath, "io", sbtIO)

  def addSbtLmPosition(p: Project): Project = addSbtModule(p, sbtLmPath, "lmPosition", lmPosition)
  def addSbtLmLogging(p: Project): Project = addSbtModule(p, sbtLmPath, "lmLogging", lmLogging)
  def addSbtLmCache(p: Project): Project = addSbtModule(p, sbtLmPath, "lmCache", lmCache)
  def addSbtLm(p: Project): Project = addSbtModule(p, sbtLmPath, "lm", libraryManagement)

  def addSbtZincControl(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincControl", zincControl)
  def addSbtZincRelation(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincRelation", zincRelation)
  def addSbtZincTracking(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincTracking", zincTracking)
  def addSbtZincUtilScripted(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincUtilScripted", zincUtilScripted)

  def addSbtCompilerApiInfo(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincApiInfo", compilerApiInfo)
  def addSbtCompilerBridge(p: Project): Project =
    addSbtModule(p, sbtZincPath, "compilerBridge", compilerBridge)
  def addSbtCompilerClasspath(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincClasspath", compilerClasspath)
  def addSbtCompilerInterface(p: Project): Project =
    addSbtModule(p, sbtZincPath, "compilerInterface", compilerInterface)
  def addSbtCompilerIvyIntegration(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincIvyIntegration", compilerIvyIntegration)
  def addSbtZinc(p: Project): Project = addSbtModule(p, sbtZincPath, "zinc", zinc)
  def addSbtZincCompile(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincCompile", zincCompile)

  val sjsonNewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % "0.7.0"

  val jline = "jline" % "jline" % "2.14.4"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.1"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4"
  val specs2 = "org.specs2" %% "specs2" % "2.4.17"
  val junit = "junit" % "junit" % "4.11"
  val templateResolverApi = "org.scala-sbt" % "template-resolver" % "0.1"

  private def scala211Module(name: String, moduleVersion: String) = Def setting (
    scalaBinaryVersion.value match {
      case "2.9" | "2.10" => Nil
      case _              => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
    }
  )

  val scalaXml = scala211Module("scala-xml", "1.0.6")
  val scalaParsers = scala211Module("scala-parser-combinators", "1.0.5")
}
