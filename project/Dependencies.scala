import sbt._
import Keys._
import sbt.contraband.ContrabandPlugin.autoImport._

object Dependencies {
  // WARNING: Please Scala update versions in PluginCross.scala too
  val scala212 = "2.12.11"
  val scala213 = "2.13.1"
  val checkPluginCross = settingKey[Unit]("Make sure scalaVersion match up")
  val baseScalaVersion = scala212
  def nightlyVersion = sys.props.get("sbt.build.version")

  // sbt modules
  private val ioVersion = nightlyVersion.getOrElse("1.4.0-M6")
  private val lmVersion =
    sys.props.get("sbt.build.lm.version").orElse(nightlyVersion).getOrElse("1.3.0")
  val zincVersion = nightlyVersion.getOrElse("1.4.0-M5")

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val libraryManagementCore = "org.scala-sbt" %% "librarymanagement-core" % lmVersion
  private val libraryManagementIvy = "org.scala-sbt" %% "librarymanagement-ivy" % lmVersion

  val launcherVersion = "1.1.3"
  val launcherInterface = "org.scala-sbt" % "launcher-interface" % launcherVersion
  val rawLauncher = "org.scala-sbt" % "launcher" % launcherVersion
  val testInterface = "org.scala-sbt" % "test-interface" % "1.0"
  val ipcSocket = "org.scala-sbt.ipcsocket" % "ipcsocket" % "1.0.1"

  private val compilerInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  private val compilerClasspath = "org.scala-sbt" %% "zinc-classpath" % zincVersion
  private val compilerApiInfo = "org.scala-sbt" %% "zinc-apiinfo" % zincVersion
  private val compilerBridge = "org.scala-sbt" %% "compiler-bridge" % zincVersion
  private val zinc = "org.scala-sbt" %% "zinc" % zincVersion
  private val zincCompile = "org.scala-sbt" %% "zinc-compile" % zincVersion
  private val zincCompileCore = "org.scala-sbt" %% "zinc-compile-core" % zincVersion

  def getSbtModulePath(key: String) = {
    val localProps = new java.util.Properties()
    IO.load(localProps, file("project/local.properties"))
    val path = Option(localProps.getProperty(key)).orElse(sys.props.get(key))
    path.foreach(f => println(s"Using $key=$f"))
    path
  }

  lazy val sbtIoPath = getSbtModulePath("sbtio.path")
  lazy val sbtUtilPath = getSbtModulePath("sbtutil.path")
  lazy val sbtLmPath = getSbtModulePath("sbtlm.path")
  lazy val sbtZincPath = getSbtModulePath("sbtzinc.path")

  def addSbtModule(
      path: Option[String],
      projectName: String,
      moduleId: ModuleID,
      c: Option[Configuration] = None
  ) = (p: Project) => {
    val m = moduleId.withConfigurations(c.map(_.name))
    path match {
      case Some(f) =>
        p.dependsOn(ClasspathDependency(ProjectRef(file(f), projectName), c.map(_.name)))
      case None => p.settings(libraryDependencies += m, dependencyOverrides += m)
    }
  }

  def addSbtIO = addSbtModule(sbtIoPath, "io", sbtIO)

  def addSbtLmCore = addSbtModule(sbtLmPath, "lmCore", libraryManagementCore)
  def addSbtLmIvy = addSbtModule(sbtLmPath, "lmIvy", libraryManagementIvy)
  def addSbtLmIvyTest = addSbtModule(sbtLmPath, "lmIvy", libraryManagementIvy, Some(Test))

  def addSbtCompilerInterface = addSbtModule(sbtZincPath, "compilerInterfaceJVM", compilerInterface)
  def addSbtCompilerClasspath = addSbtModule(sbtZincPath, "zincClasspathJVM2_12", compilerClasspath)
  def addSbtCompilerApiInfo = addSbtModule(sbtZincPath, "zincApiInfoJVM2_12", compilerApiInfo)
  def addSbtCompilerBridge = addSbtModule(sbtZincPath, "compilerBridgeJVM2_12", compilerBridge)
  def addSbtZinc = addSbtModule(sbtZincPath, "zincJVM2_12", zinc)
  def addSbtZincCompile = addSbtModule(sbtZincPath, "zincCompileJVM2_12", zincCompile)
  def addSbtZincCompileCore = addSbtModule(sbtZincPath, "zincCompileCoreJVM2_12", zincCompileCore)

  val lmCoursierShaded = "io.get-coursier" %% "lm-coursier-shaded" % "2.0.0-RC6-4"

  def sjsonNew(n: String) = Def.setting("com.eed3si9n" %% n % contrabandSjsonNewVersion.value)
  val sjsonNewScalaJson = sjsonNew("sjson-new-scalajson")
  val sjsonNewMurmurhash = sjsonNew("sjson-new-murmurhash")

  val jline = "jline" % "jline" % "2.14.6"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.8"
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0"
  val specs2 = "org.specs2" %% "specs2-junit" % "4.0.1"
  val junit = "junit" % "junit" % "4.11"
  val scalaVerify = "com.eed3si9n.verify" %% "verify" % "0.2.0"
  val templateResolverApi = "org.scala-sbt" % "template-resolver" % "0.1"

  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
  val scalaParsers = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
  val scalaReflect = Def.setting("org.scala-lang" % "scala-reflect" % scalaVersion.value)

  // specify all of log4j modules to prevent misalignment
  def log4jModule = (n: String) => "org.apache.logging.log4j" % n % "2.11.2"
  val log4jApi = log4jModule("log4j-api")
  val log4jCore = log4jModule("log4j-core")
  val log4jSlf4jImpl = log4jModule("log4j-slf4j-impl")
  val log4jModules = Vector(log4jApi, log4jCore, log4jSlf4jImpl)

  val scalaCacheCaffeine = "com.github.cb372" %% "scalacache-caffeine" % "0.20.0"

  val hedgehog = "hedgehog" %% "hedgehog-sbt" % "0.1.0"
  val disruptor = "com.lmax" % "disruptor" % "3.4.2"
  val silencerPlugin = "com.github.ghik" %% "silencer-plugin" % "1.4.2"
  val silencerLib = "com.github.ghik" %% "silencer-lib" % "1.4.2" % Provided
}
