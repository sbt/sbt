import sbt._
import Keys._

object Dependencies {
  val scala282 = "2.8.2"
  val scala292 = "2.9.2"
  val scala293 = "2.9.3"
  val scala210 = "2.10.6"
  val scala211 = "2.11.8"

  // sbt modules
  private val ioVersion = "1.0.0-M9"
  private val utilVersion = "1.0.0-M17"
  private val lmVersion = "0.1.0-X3"
  private val zincVersion = "1.0.0-X7"

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val utilApplyMacro = "org.scala-sbt" %% "util-apply-macro" % utilVersion
  private val utilCache = "org.scala-sbt" %% "util-cache" % utilVersion
  private val utilCollection = "org.scala-sbt" %% "util-collection" % utilVersion
  private val utilCompletion = "org.scala-sbt" %% "util-completion" % utilVersion
  private val utilControl = "org.scala-sbt" %% "util-control" % utilVersion
  private val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  private val utilLogic = "org.scala-sbt" %% "util-logic" % utilVersion
  private val utilRelation = "org.scala-sbt" %% "util-relation" % utilVersion
  private val utilScripted = "org.scala-sbt" %% "util-scripted" % utilVersion
  private val utilTesting = "org.scala-sbt" %% "util-testing" % utilVersion
  private val utilTracking = "org.scala-sbt" %% "util-tracking" % utilVersion

  private val libraryManagement = "org.scala-sbt" %% "librarymanagement" % lmVersion

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
  lazy val sbtUtilPath = getSbtModulePath("sbtutil.path", "sbt/util")
  lazy val sbtLmPath = getSbtModulePath("sbtlm.path", "sbt/lm")
  lazy val sbtZincPath = getSbtModulePath("sbtzinc.path", "sbt/zinc")

  def addSbtModule(p: Project, path: Option[String], projectName: String, m: ModuleID, c: Option[Configuration] = None) =
    path match {
      case Some(f) => p dependsOn c.fold[ClasspathDependency](ProjectRef(file(f), projectName))(ProjectRef(file(f), projectName) % _)
      case None    => p settings (libraryDependencies += c.fold(m)(m % _))
    }

  def addSbtIO(p: Project): Project = addSbtModule(p, sbtIoPath, "io", sbtIO)

  def addSbtUtilApplyMacro(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilApplyMacro", utilApplyMacro)
  def addSbtUtilCache(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilCache", utilCache)
  def addSbtUtilCollection(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilCollection", utilCollection)
  def addSbtUtilCompletion(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilComplete", utilCompletion)
  def addSbtUtilControl(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilControl", utilControl)
  def addSbtUtilLogging(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilLogging", utilLogging)
  def addSbtUtilLogic(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilLogic", utilLogic)
  def addSbtUtilRelation(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilRelation", utilRelation)
  def addSbtUtilScripted(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilScripted", utilScripted)
  def addSbtUtilTesting(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilTesting", utilTesting, Some(Test))
  def addSbtUtilTracking(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilTracking", utilTracking)

  def addSbtLm(p: Project): Project = addSbtModule(p, sbtLmPath, "lm", libraryManagement)

  def addSbtCompilerApiInfo(p: Project): Project = addSbtModule(p, sbtZincPath, "zincApiInfo", compilerApiInfo)
  def addSbtCompilerBridge(p: Project): Project = addSbtModule(p, sbtZincPath, "compilerBridge", compilerBridge)
  def addSbtCompilerClasspath(p: Project): Project = addSbtModule(p, sbtZincPath, "zincClasspath", compilerClasspath)
  def addSbtCompilerInterface(p: Project): Project = addSbtModule(p, sbtZincPath, "compilerInterface", compilerInterface)
  def addSbtCompilerIvyIntegration(p: Project): Project = addSbtModule(p, sbtZincPath, "zincIvyIntegration", compilerIvyIntegration)
  def addSbtZinc(p: Project): Project = addSbtModule(p, sbtZincPath, "zinc", zinc)
  def addSbtZincCompile(p: Project): Project = addSbtModule(p, sbtZincPath, "zincCompile", zincCompile)

  val sjsonNewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % "0.7.0"

  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  val junit = "junit" % "junit" % "4.11"

  private def scala211Module(name: String, moduleVersion: String) = Def setting (
    scalaBinaryVersion.value match {
      case "2.9" | "2.10" => Nil
      case _              => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
    }
  )

  val scalaXml = scala211Module("scala-xml", "1.0.1")
  val scalaParsers = scala211Module("scala-parser-combinators", "1.0.1")
}
