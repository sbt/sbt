import sbt._
import Keys._
import sbt.contraband.ContrabandPlugin.autoImport._

object Dependencies {
  val scala282 = "2.8.2"
  val scala292 = "2.9.2"
  val scala293 = "2.9.3"
  val scala210 = "2.10.6"
  val scala211 = "2.11.8"
  val scala212 = "2.12.3"
  val baseScalaVersion = scala212

  // sbt modules
  private val ioVersion = "1.1.0"
  private val utilVersion = "1.0.2"
  private val lmVersion = "1.0.2"
  private val zincVersion = "1.0.2"

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val utilPosition = "org.scala-sbt" %% "util-position" % utilVersion
  private val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  private val utilCache = "org.scala-sbt" %% "util-cache" % utilVersion
  private val utilControl = "org.scala-sbt" %% "util-control" % utilVersion
  private val utilRelation = "org.scala-sbt" %% "util-relation" % utilVersion
  private val utilTracking = "org.scala-sbt" %% "util-tracking" % utilVersion
  private val utilScripted = "org.scala-sbt" %% "util-scripted" % utilVersion

  private val libraryManagementCore = "org.scala-sbt" %% "librarymanagement-core" % lmVersion
  private val libraryManagementIvy = "org.scala-sbt" %% "librarymanagement-ivy" % lmVersion

  val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"
  val rawLauncher = "org.scala-sbt" % "launcher" % "1.0.0"
  val testInterface = "org.scala-sbt" % "test-interface" % "1.0"

  private val compilerInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  private val compilerClasspath = "org.scala-sbt" %% "zinc-classpath" % zincVersion
  private val compilerApiInfo = "org.scala-sbt" %% "zinc-apiinfo" % zincVersion
  private val compilerBridge = "org.scala-sbt" %% "compiler-bridge" % zincVersion
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
  lazy val sbtUtilPath = getSbtModulePath("sbtutil.path", "sbt/util")
  lazy val sbtLmPath = getSbtModulePath("sbtlm.path", "sbt/lm")
  lazy val sbtZincPath = getSbtModulePath("sbtzinc.path", "sbt/zinc")

  def addSbtModule(p: Project, path: Option[String], projectName: String, m: ModuleID) =
    path match {
      case Some(f) => p dependsOn ProjectRef(file(f), projectName)
      case None    => p settings (libraryDependencies += m)
    }

  def addSbtIO(p: Project): Project = addSbtModule(p, sbtIoPath, "io", sbtIO)

  def addSbtUtilPosition(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilPosition", utilPosition)
  def addSbtUtilLogging(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilLogging", utilLogging)
  def addSbtUtilCache(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilCache", utilCache)
  def addSbtUtilControl(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilControl", utilControl)
  def addSbtUtilRelation(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilRelation", utilRelation)
  def addSbtUtilTracking(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilTracking", utilTracking)
  def addSbtUtilScripted(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilScripted", utilScripted)

  def addSbtLmCore(p: Project): Project =
    addSbtModule(p, sbtLmPath, "lmCore", libraryManagementCore)
  def addSbtLmIvy(p: Project): Project = addSbtModule(p, sbtLmPath, "lmIvy", libraryManagementIvy)

  def addSbtCompilerInterface(p: Project): Project =
    addSbtModule(p, sbtZincPath, "compilerInterface", compilerInterface)
  def addSbtCompilerClasspath(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincClasspath", compilerClasspath)
  def addSbtCompilerApiInfo(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincApiInfo", compilerApiInfo)
  def addSbtCompilerBridge(p: Project): Project =
    addSbtModule(p, sbtZincPath, "compilerBridge", compilerBridge)
  def addSbtCompilerIvyIntegration(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincIvyIntegration", compilerIvyIntegration)
  def addSbtZinc(p: Project): Project = addSbtModule(p, sbtZincPath, "zinc", zinc)
  def addSbtZincCompile(p: Project): Project =
    addSbtModule(p, sbtZincPath, "zincCompile", zincCompile)

  val sjsonNewScalaJson = Def.setting {
    "com.eed3si9n" %% "sjson-new-scalajson" % contrabandSjsonNewVersion.value
  }

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

  def log4jVersion = "2.8.1"
  val log4jApi = "org.apache.logging.log4j" % "log4j-api" % log4jVersion
  val log4jCore = "org.apache.logging.log4j" % "log4j-core" % log4jVersion
  val log4jSlf4jImpl = "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion
  // specify all of log4j modules to prevent misalignment
  val log4jDependencies = Vector(log4jApi, log4jCore, log4jSlf4jImpl)
}
