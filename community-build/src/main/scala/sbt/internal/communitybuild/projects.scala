package sbt
package internal
package communitybuild

import java.nio.file.*
import java.io.File
import sbt.io.IO
import java.nio.charset.StandardCharsets.UTF_8
import scala.io.Source
import scala.util.Using

lazy val communitybuildDir: Path =
  Paths.get(sys.props("user.dir")).resolve("community-build")

lazy val sbtVersion: String =
  val file = communitybuildDir.resolve("target").resolve("sbt.version")
  new String(Files.readAllBytes(file), UTF_8)

lazy val bootDir: Path =
  val dir = communitybuildDir.resolve("target").resolve("boot")
  Files.createDirectories(dir)
  dir

lazy val remoteCachePluginDir: Path =
  val dir = communitybuildDir.resolve("target").resolve("remote-cache-plugin")
  Files.createDirectories(dir)
  Files.writeString(
    dir.resolve("plugins.sbt"),
    """addRemoteCachePlugin
      |libraryDependencySchemes += "org.scala-sbt" % "compiler-interface" % VersionScheme.Always
      |""".stripMargin,
  )
  dir

lazy val sbt1PluginDir: Path =
  val dir = communitybuildDir.resolve("target").resolve("sbt1-plugins")
  Files.createDirectories(dir)
  Files.writeString(
    dir.resolve("plugins.sbt"),
    """addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")
      |addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.2.0")
      |""".stripMargin,
  )
  Files.writeString(
    dir.resolve("Sbt2Shims.scala"),
    """import sbt._
      |
      |object Sbt2Shims extends AutoPlugin {
      |  override def trigger = allRequirements
      |  object autoImport {
      |    val allowMismatchScala = settingKey[Boolean]("sbt 2.x shim")
      |  }
      |}
      |""".stripMargin,
  )
  dir
end sbt1PluginDir

lazy val sbtPluginFilePath: String =
  // Workaround for https://github.com/sbt/sbt/issues/4395
  new File(sys.props("user.home") + "/config/sbt/2/plugins").mkdirs()
  communitybuildDir.resolve("sbt-injected-plugins").toAbsolutePath().toString()

def log(msg: String) = println(Console.GREEN + msg + Console.RESET)

/** Executes shell command, returns false in case of error. */
def exec(
    projectDir: Path,
    binary: String,
    arguments: Seq[String],
    environment: Map[String, String],
    onLine: String => Unit = println,
): Int =
  import scala.jdk.CollectionConverters.*
  val command = binary +: arguments
  log(command.mkString(" "))
  val builder = new ProcessBuilder(command*)
    .directory(projectDir.toFile)
    .redirectInput(ProcessBuilder.Redirect.INHERIT)
    .redirectErrorStream(true)
  builder.environment.putAll(environment.asJava)
  val process = builder.start()
  Using.resource(Source.fromInputStream(process.getInputStream, UTF_8.name))(
    _.getLines().foreach(onLine)
  )
  process.waitFor()

enum Scenario:
  case Test
  case Build
  case TestTest(minHitRate: Double, minTestHitRate: Double = 1.0)
  case TestSbt1

sealed trait CommunityProject:
  def project: String
  def testCommand: String
  def testCompileCommand: String
  def publishCommand: String
  def docCommand: String
  def binaryName: String
  def runCommandsArgs: List[String] = Nil
  def sbt1CommandsArgs: List[String] = Nil
  def sbt1TestCommand: String = testCommand
  def warmupCommand: Option[String] = None
  def sbt1WarmupCommand: Option[String] = None
  def environment: Map[String, String] = Map.empty
  def diskCacheDir: Option[File] = None
  def scenarioType: Scenario

  final val projectDir = communitybuildDir.resolve("community-projects").resolve(project)

  /** Publish this project to the local Maven repository */
  final def publish(): Unit =
    log(s"Publishing $project")
    if publishCommand eq null then
      throw RuntimeException(
        s"Publish command is not specified for $project. Project details:\n$this"
      )
    val (exitCode, _) = execAndShutdown(runCommandsArgs :+ publishCommand, "publish")
    if exitCode != 0 then
      throw RuntimeException(
        s"Publish command exited with code $exitCode for project $project. Project details:\n$this"
      )

  final def doc(): Unit =
    log(s"Documenting $project")
    if docCommand eq null then
      throw RuntimeException(s"Doc command is not specified for $project. Project details:\n$this")
    val (exitCode, _) = execAndShutdown(runCommandsArgs :+ docCommand, "doc")
    if exitCode != 0 then
      throw RuntimeException(
        s"Doc command exited with code $exitCode for project $project. Project details:\n$this"
      )

  def scenario(): Int = scenarioType match
    case Scenario.Test                                 => test()
    case Scenario.Build                                => build()
    case Scenario.TestTest(minHitRate, minTestHitRate) => testTest(minHitRate, minTestHitRate)
    case Scenario.TestSbt1                             => testSbt1()

  final def build(): Int = execAndShutdown(buildCommands, "build")._1

  final def buildCommands = runCommandsArgs :+ testCompileCommand

  final def test(): Int = execAndShutdown(runCommandsArgs :+ testCommand, "test")._1

  /** Runs the tests twice using sbt 1.x. */
  final def testSbt1(): Int =
    warmup(sbt1CommandsArgs, sbt1WarmupCommand)
    val (firstExitCode, _) =
      execAndShutdown(sbt1CommandsArgs :+ sbt1TestCommand, "test (1st)", sbt1CommandsArgs)
    if firstExitCode != 0 then firstExitCode
    else execAndShutdown(sbt1CommandsArgs :+ sbt1TestCommand, "test (2nd)", sbt1CommandsArgs)._1

  /** Runs the tests twice, and asserts the second run is served from the cache. */
  final def testTest(minHitRate: Double, minTestHitRate: Double): Int =
    warmup(runCommandsArgs, warmupCommand)
    val (firstExitCode, _) = execAndShutdown(runCommandsArgs :+ testCommand, "test (1st)")
    if firstExitCode != 0 then firstExitCode
    else
      diskCacheDir.foreach(wipeDirectory)
      val (exitCode, summaries) =
        execAndShutdown(runCommandsArgs :+ testCommand, "test (2nd, disk cache wiped)")
      assert(summaries.nonEmpty, s"no cache summary found in the second test run of $project")
      val belowThreshold = summaries.filter(_.hitRate.forall(_ < minHitRate))
      assert(
        belowThreshold.isEmpty,
        s"cache hit rate of the second test run of $project is below $minHitRate: $belowThreshold"
      )
      val tests = summaries.flatMap(_.tests)
      assert(tests.nonEmpty, s"no test summary found in the second test run of $project")
      val testsBelowThreshold = tests.filter(_.testHitRate < minTestHitRate)
      assert(
        testsBelowThreshold.isEmpty,
        s"test hit rate of the second test run of $project is below $minTestHitRate: $testsBelowThreshold"
      )
      exitCode
  end testTest

  private def warmup(baseArgs: List[String], command: Option[String]): Unit =
    command.foreach(cmd => execAndShutdown(baseArgs :+ cmd, "warm-up", baseArgs))

  private def wipeDirectory(dir: File): Unit =
    log(s"Wiping disk cache $dir")
    IO.delete(IO.listFiles(dir))

  private def execAndShutdown(
      arguments: List[String],
      run: String,
      baseArgs: List[String] = runCommandsArgs,
  ): (Int, List[ParsedCacheSummary]) =
    val summaries = CacheSummaryCollector()
    val start = System.nanoTime()
    val exitCode = exec(
      projectDir,
      binaryName,
      arguments,
      environment,
      line =>
        println(line)
        summaries.add(line)
    )
    val commandEnd = System.nanoTime()
    exec(projectDir, binaryName, baseArgs :+ "shutdown", environment)
    val shutdownEnd = System.nanoTime()
    val wallClockSeconds = (commandEnd - start) / 1e9
    log(
      f"[$project] $run wall clock: $wallClockSeconds%.1f s command, ${(shutdownEnd - commandEnd) / 1e9}%.1f s shutdown"
    )
    val sbtVersionUsed = baseArgs.collectFirst { case s"-Dsbt.version=$v" => v }.getOrElse("")
    val result = summaries.result()
    ParsedCacheSummary.report(RunInfo(project, run, sbtVersionUsed, wallClockSeconds), result)
    (exitCode, result)
  end execAndShutdown

end CommunityProject

val sbt1Version = "1.13.0"
val sbt2Version = "2.0.3"

final case class SbtCommunityProject(
    project: String,
    testCmd: String = "test",
    testCompileCmd: String = "Test/compile",
    sbt1TestCmd: Option[String] = None,
    warmupCmd: Option[String] = None,
    sbt1WarmupCmd: Option[String] = None,
    extraSbtArgs: List[String] = Nil,
    publishCmd: String = "publishLocal",
    docCmd: String = "doc",
    scalacOptions: List[String] = SbtCommunityProject.scalacOptions,
    scenarioType: Scenario = Scenario.Test,
    override val environment: Map[String, String] = Map.empty,
) extends CommunityProject:
  override val binaryName: String = "sbt"

  private def scalacOptionsString: String =
    scalacOptions.map("\"" + _ + "\"").mkString("List(", ",", ")")

  private val baseCommand =
    (if scalacOptions.isEmpty then ""
     else s"""set Global/scalacOptions ++= $scalacOptionsString;""")

  private def mkTestCommand(cmd: String): String =
    s"$baseCommand$cmd"

  override val testCommand = mkTestCommand(testCmd)

  override def sbt1TestCommand: String = sbt1TestCmd.fold(testCommand)(mkTestCommand)

  override def warmupCommand: Option[String] = warmupCmd

  override def sbt1WarmupCommand: Option[String] = sbt1WarmupCmd

  override val testCompileCommand =
    s"$baseCommand$testCompileCmd"

  override val publishCommand =
    if publishCmd eq null then null else s"$baseCommand$publishCmd"

  override val docCommand =
    if docCmd eq null then null
    else
      val cmd = if docCmd.startsWith(";") then docCmd else s";$docCmd"
      s"$baseCommand set every useScaladoc := true; set every doc/logLevel := Level.Warn $cmd "

  private val localCacheDir: File = IO.createTemporaryDirectory

  override def diskCacheDir: Option[File] = Some(localCacheDir)

  private val sbtProps: List[String] = Option(System.getProperty("sbt.ivy.home")) match
    case Some(ivyHome) => List(s"-Dsbt.ivy.home=$ivyHome")
    case _             => Nil

  override val sbt1CommandsArgs: List[String] =
    extraSbtArgs ++ sbtProps ++ List(
      s"-Dsbt.global.plugins=$sbt1PluginDir",
      s"-Dsbt.version=$sbt1Version",
      s"-Dsbt.boot=$bootDir",
      "-Dsbt.supershell=false",
      "--error",
    )

  override val runCommandsArgs: List[String] =
    // Run the sbt command with the compiler version and sbt plugin set in the build
    val remoteCacheProps = SbtCommunityProject.remoteCache.toList.flatMap(uri =>
      List(
        s"-Dsbt.global.plugins=$remoteCachePluginDir",
        s"-Dsbt.remote_cache=$uri",
      )
    )
    extraSbtArgs ++ sbtProps ++ remoteCacheProps ++ List(
      s"-Dsbt.version=$sbtVersion",
      s"-Dsbt.boot=$bootDir",
      s"-Dsbt.global.localcache=$localCacheDir",
      "-Dsbt.supershell=false",
      "--error",
    )
end SbtCommunityProject

object SbtCommunityProject:
  def scalacOptions = Nil

  /** Remote cache URI for the community projects, e.g. grpc://127.0.0.1:2024 */
  def remoteCache: Option[String] = sys.env.get("COMMUNITY_BUILD_REMOTE_CACHE")

object projects:

  private def forceDoc(projects: String*) =
    projects
      .map(project =>
        s""";set $project/Compile/doc/sources ++= ($project/Compile/doc/dotty.tools.sbtplugin.DottyPlugin.autoImport.tastyFiles).value ;$project/doc"""
      )
      .mkString(" ")

  private def removeRelease8(projects: String*): String =
    projects
      .map(project =>
        s"""set $project/Compile/scalacOptions := ($project/Compile/scalacOptions).value.filterNot(opt => opt == "-release" || opt == "-java-output-version" || opt == "8")"""
      )
      .mkString("; ")

  private def aggregateDoc(in: String)(projects: String*) =
    val tastyFiles =
      (in +: projects)
        .map(p => s"($p/Compile/doc/dotty.tools.sbtplugin.DottyPlugin.autoImport.tastyFiles).value")
        .mkString(" ++ ")
    s""";set $in/Compile/doc/sources ++= file("a.scala") +: ($tastyFiles) ;$in/doc"""

  private def all(tasks: String*): String =
    tasks.mkString("all ", " ", "")

  private val chimneyJvmProjects = List(
    "chimney",
    "chimneyCats",
    "chimneyProtobufs",
    "chimneyJavaCollections",
    "chimneySandwichTests",
  )

  lazy val chimney = SbtCommunityProject(
    project = "chimney",
    testCmd = all(chimneyJvmProjects.map(p => s"$p/test")*),
    testCompileCmd = all(chimneyJvmProjects.map(p => s"$p/Test/compile")*),
    warmupCmd = Some(all(chimneyJvmProjects.map(p => s"$p/update")*)),
    environment = Map("_JAVA_OPTIONS" -> "-Xmx2g"),
    scenarioType = Scenario.TestTest(minHitRate = 0.9),
  )

  lazy val `chimney-sbt1` = chimney.copy(
    sbt1TestCmd = Some(all(chimneyJvmProjects.map(p => s"${p}3/test")*)),
    sbt1WarmupCmd = Some(all(chimneyJvmProjects.map(p => s"${p}3/update")*)),
    scenarioType = Scenario.TestSbt1,
  )

  lazy val `sbt-compile-benchmark` = SbtCommunityProject(
    project = "sbt-compile-benchmark",
    scenarioType = Scenario.Build,
  )

  lazy val scalaz = SbtCommunityProject(
    project = "scalaz",
    environment = Map("_JAVA_OPTIONS" -> "-Xms1g -Xmx3g"),
    testCmd = "rootJVM/test",
    testCompileCmd = "rootJVM/Test/compile",
    docCmd = forceDoc("effectJVM"),
    scenarioType = Scenario.Build,
  )

  lazy val parboiled2 = SbtCommunityProject(
    project = "parboiled2",
    testCmd = "parboiledCoreJVM3/testFull; parboiledJVM3/testFull",
    testCompileCmd = "parboiledCoreJVM3/Test/compile; parboiledJVM3/Test/compile",
    publishCmd = "publishLocal",
    scalacOptions = SbtCommunityProject.scalacOptions.filter(_ != "-Xcheck-macros"),
    scenarioType = Scenario.Build,
  )

end projects

def allProjects = List(
  projects.chimney,
  projects.parboiled2,
  projects.scalaz,
)

lazy val projectMap = allProjects.groupBy(_.project)
