import sbt.*
import Keys.*

object Dependencies {
  // WARNING: Please Scala update versions in PluginCross.scala too
  val scala3 = "3.8.3"
  val scala212 = "2.12.21"
  val baseScalaVersion = scala3
  def nightlyVersion: Option[String] =
    sys.env.get("BUILD_VERSION") orElse sys.props.get("sbt.build.version")

  // sbt modules
  val ioVersion = nightlyVersion.getOrElse("1.10.5")
  val zincVersion = nightlyVersion.getOrElse("2.0.0-M15")

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  val launcherVersion = "1.6.1"
  val launcherInterface = "org.scala-sbt" % "launcher-interface" % launcherVersion
  val rawLauncher = "org.scala-sbt" % "launcher" % launcherVersion
  val testInterface = "org.scala-sbt" % "test-interface" % "1.0"
  val ipcSocket = "org.scala-sbt.ipcsocket" % "ipcsocket" % "1.6.3"

  private val compilerInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  private val compilerClasspath = "org.scala-sbt" %% "zinc-classpath" % zincVersion
  private val compilerApiInfo = "org.scala-sbt" %% "zinc-apiinfo" % zincVersion
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
  def addSbtIOForTest = addSbtModule(sbtIoPath, "io", sbtIO, Some(Test))

  def addSbtCompilerInterface = addSbtModule(sbtZincPath, "compilerInterface", compilerInterface)
  def addSbtCompilerClasspath = addSbtModule(sbtZincPath, "zincClasspath", compilerClasspath)
  def addSbtCompilerApiInfo = addSbtModule(sbtZincPath, "zincApiInfo", compilerApiInfo)
  def addSbtZinc = addSbtModule(sbtZincPath, "zinc", zinc)
  def addSbtZincCompileCore = addSbtModule(sbtZincPath, "zincCompileCore", zincCompileCore)

  lazy val sjsonNewVersion = "0.14.0"
  def sjsonNew(n: String) = Def.setting(
    "com.eed3si9n" %% n % sjsonNewVersion
  ) // contrabandSjsonNewVersion.value
  val sjsonNewScalaJson = sjsonNew("sjson-new-scalajson")
  val sjsonNewMurmurhash = sjsonNew("sjson-new-murmurhash")
  val sjsonNewCore = sjsonNew("sjson-new-core")

  // JLine 3 version must be coordinated together with JAnsi version
  // and the JLine 2 fork version, which uses the same JAnsi
  val jline =
    "org.scala-sbt.jline" % "jline" % "2.14.7-sbt-9a88bc413e2b34a4580c001c654d1a7f4f65bf18"
  val jline3Version = "3.27.1"
  val jline3Terminal = "org.jline" % "jline-terminal" % jline3Version
  val jline3JNI = "org.jline" % "jline-terminal-jni" % jline3Version
  val jline3Native = "org.jline" % "jline-native" % jline3Version
  val jline3Reader = "org.jline" % "jline-reader" % jline3Version
  val jline3Builtins = "org.jline" % "jline-builtins" % jline3Version
  val scalatest = Seq(
    "scalatest-diagrams",
    "scalatest-flatspec",
    "scalatest-freespec",
    "scalatest-funspec",
    "scalatest-funsuite",
    "scalatest-propspec",
    "scalatest-shouldmatchers",
  ).map(
    "org.scalatest" %% _ % "3.2.20" % Test
  )
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.19.0"
  val junit = "junit" % "junit" % "4.13.2"
  val scalaVerify = "com.eed3si9n.verify" %% "verify" % "1.0.0"
  val templateResolverApi = "org.scala-sbt" % "template-resolver" % "0.1"
  val remoteapis =
    "com.eed3si9n.remoteapis.shaded" % "shaded-remoteapis-java" % "2.3.0-M1-52317e00d8d4c37fa778c628485d220fb68a8d08"
  val gson = "org.scala-sbt.gson" % "shaded-gson" % "2.13.1"

  val scalaCompiler = "org.scala-lang" %% "scala3-compiler" % scala3
  val scala3Library = "org.scala-lang" %% "scala3-library" % scala3

  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "2.4.0"
  val scalaParsers = "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0"
  val scalaPar = "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0"
  val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.14.0"

  val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "2.8.5"

  val hedgehog = "qa.hedgehog" %% "hedgehog-sbt" % "0.13.0"
  val disruptor = "com.lmax" % "disruptor" % "3.4.2"
  val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-77cc781d727b367d3761f097d89f5a4762771d41"

  // lm dependencies
  val jsch = ("com.github.mwiede" % "jsch" % "0.2.23").intransitive()
  val gigahorseApacheHttp = "com.eed3si9n" %% "gigahorse-apache-http" % "0.9.4"

  // lm-coursier dependencies
  val dataclassScalafixVersion = "0.3.0"
  val coursierVersion = "2.1.25-M24"

  val coursier = ("io.get-coursier" %% "coursier" % coursierVersion)
    .cross(CrossVersion.for3Use2_13)
    .exclude("org.codehaus.plexus", "plexus-archiver")
    .exclude("org.codehaus.plexus", "plexus-container-default")

  val coursierSbtMavenRepo =
    ("io.get-coursier" %% "coursier-sbt-maven-repository" % coursierVersion)
      .cross(CrossVersion.for3Use2_13)

  // FIXME Ideally, we should depend on the same version of io.get-coursier.jniutils:windows-jni-utils that
  // io.get-coursier::coursier depends on.
  val jniUtilsVersion = "0.3.3"
}
