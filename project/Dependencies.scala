import sbt._
import Keys._
import sbt.contraband.ContrabandPlugin.autoImport._

object Dependencies {
  // WARNING: Please Scala update versions in PluginCross.scala too
  val scala212 = "2.12.19"
  val scala213 = "2.13.12"
  val scala3 = "3.3.1"
  val checkPluginCross = settingKey[Unit]("Make sure scalaVersion match up")
  val baseScalaVersion = scala3
  def nightlyVersion: Option[String] =
    sys.env.get("BUILD_VERSION") orElse sys.props.get("sbt.build.version")

  // sbt modules
  private val ioVersion = nightlyVersion.getOrElse("1.10.0")
  val zincVersion = nightlyVersion.getOrElse("2.0.0-M1")

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  val launcherVersion = "1.4.3"
  val launcherInterface = "org.scala-sbt" % "launcher-interface" % launcherVersion
  val rawLauncher = "org.scala-sbt" % "launcher" % launcherVersion
  val testInterface = "org.scala-sbt" % "test-interface" % "1.0"
  val ipcSocket = "org.scala-sbt.ipcsocket" % "ipcsocket" % "1.6.2"

  private val compilerInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  private val compilerClasspath = "org.scala-sbt" %% "zinc-classpath" % zincVersion
  private val compilerApiInfo = "org.scala-sbt" %% "zinc-apiinfo" % zincVersion
  private val compilerBridge = "org.scala-sbt" %% "compiler-bridge" % zincVersion
  private val zinc = "org.scala-sbt" %% "zinc" % zincVersion
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
    val m0 = moduleId.withConfigurations(c.map(_.name))
    val m = m0
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

  def addSbtCompilerInterface = addSbtModule(sbtZincPath, "compilerInterface", compilerInterface)
  def addSbtCompilerClasspath = addSbtModule(sbtZincPath, "zincClasspath", compilerClasspath)
  def addSbtCompilerApiInfo = addSbtModule(sbtZincPath, "zincApiInfo", compilerApiInfo)
  def addSbtCompilerBridge = addSbtModule(sbtZincPath, "compilerBridge2_12", compilerBridge)
  def addSbtZinc = addSbtModule(sbtZincPath, "zinc", zinc)
  def addSbtZincCompileCore = addSbtModule(sbtZincPath, "zincCompileCore", zincCompileCore)

  // val lmCoursierShaded = "io.get-coursier" %% "lm-coursier-shaded" % "2.0.10"
  val lmCoursierShaded = "org.scala-sbt" %% "librarymanagement-coursier" % "2.0.0-alpha8"

  lazy val sjsonNewVersion = "0.14.0-M1"
  def sjsonNew(n: String) = Def.setting(
    "com.eed3si9n" %% n % sjsonNewVersion
  ) // contrabandSjsonNewVersion.value
  val sjsonNewScalaJson = sjsonNew("sjson-new-scalajson")
  val sjsonNewMurmurhash = sjsonNew("sjson-new-murmurhash")
  val sjsonNewCore = sjsonNew("sjson-new-core")

  val eval = ("com.eed3si9n.eval" % "eval" % "0.3.0").cross(CrossVersion.full)

  // JLine 3 version must be coordinated together with JAnsi version
  // and the JLine 2 fork version, which uses the same JAnsi
  val jline =
    "org.scala-sbt.jline" % "jline" % "2.14.7-sbt-9c3b6aca11c57e339441442bbf58e550cdfecb79"
  val jline3Version = "3.24.1"
  val jline3Terminal = "org.jline" % "jline-terminal" % jline3Version
  val jline3Jansi = "org.jline" % "jline-terminal-jansi" % jline3Version
  val jline3JNA = "org.jline" % "jline-terminal-jna" % jline3Version
  val jline3Reader = "org.jline" % "jline-reader" % jline3Version
  val jline3Builtins = "org.jline" % "jline-builtins" % jline3Version
  val jansi = "org.fusesource.jansi" % "jansi" % "2.4.1"
  val scalatest = "org.scalatest" %% "scalatest" % "3.2.10"
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.15.4"
  val junit = "junit" % "junit" % "4.13.1"
  val scalaVerify = "com.eed3si9n.verify" %% "verify" % "1.0.0"
  val templateResolverApi = "org.scala-sbt" % "template-resolver" % "0.1"
  val remoteapis =
    "com.eed3si9n.remoteapis.shaded" % "shaded-remoteapis-java" % "2.3.0-M1-52317e00d8d4c37fa778c628485d220fb68a8d08"

  val scalaCompiler = "org.scala-lang" %% "scala3-compiler" % scala3

  val scalaXml = Def.setting(
    if (scalaBinaryVersion.value == "3") {
      "org.scala-lang.modules" %% "scala-xml" % "2.2.0"
    } else {
      "org.scala-lang.modules" %% "scala-xml" % "2.2.0"
    }
  )
  val scalaParsers = Def.setting(
    if (scalaBinaryVersion.value == "3") {
      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.1.0"
    } else {
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
    }
  )
  val scalaReflect = Def.setting(
    if (scalaBinaryVersion.value == "3") {
      "org.scala-lang" % "scala-reflect" % scala213
    } else {
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    }
  )
  val scalaPar = "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"
  val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.11.0"

  // specify all of log4j modules to prevent misalignment
  def log4jModule = (n: String) => "org.apache.logging.log4j" % n % "2.17.1"
  val log4jApi = log4jModule("log4j-api")
  val log4jCore = log4jModule("log4j-core")
  val log4jSlf4jImpl = log4jModule("log4j-slf4j-impl")
  val log4jModules = Vector(log4jApi, log4jCore, log4jSlf4jImpl)

  val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "2.8.5"

  val hedgehog = "qa.hedgehog" %% "hedgehog-sbt" % "0.7.0"
  val disruptor = "com.lmax" % "disruptor" % "3.4.2"
  val kindProjector = ("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full)
  val zeroAllocationHashing = "net.openhft" % "zero-allocation-hashing" % "0.10.1"
  val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-396a783bba347016e7fe30dacc60d355be607fe2"

  val jsch = "com.github.mwiede" % "jsch" % "0.2.17" intransitive ()
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.15.3"
  val gigahorseApacheHttp = "com.eed3si9n" %% "gigahorse-apache-http" % "0.7.0"
}
