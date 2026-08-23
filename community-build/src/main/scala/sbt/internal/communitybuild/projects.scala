package sbt
package internal
package communitybuild

import java.nio.file._
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

lazy val communitybuildDir: Path =
  Paths.get(sys.props("user.dir")).resolve("community-build")

lazy val sbtVersion: String =
  val file = communitybuildDir.resolve("target").resolve("sbt.version")
  new String(Files.readAllBytes(file), UTF_8)

lazy val bootDir: Path =
  val dir = communitybuildDir.resolve("target").resolve("boot")
  Files.createDirectories(dir)
  dir

lazy val sbtPluginFilePath: String =
  // Workaround for https://github.com/sbt/sbt/issues/4395
  new File(sys.props("user.home") + "/config/sbt/2/plugins").mkdirs()
  communitybuildDir.resolve("sbt-injected-plugins").toAbsolutePath().toString()

def log(msg: String) = println(Console.GREEN + msg + Console.RESET)

/** Executes shell command, returns false in case of error. */
def exec(projectDir: Path, binary: String, arguments: Seq[String], environment: Map[String, String]): Int =
  import scala.jdk.CollectionConverters._
  val command = binary +: arguments
  log(command.mkString(" "))
  val builder = new ProcessBuilder(command*).directory(projectDir.toFile).inheritIO()
  builder.environment.putAll(environment.asJava)
  val process = builder.start()
  val exitCode = process.waitFor()
  exitCode


sealed trait CommunityProject:
  def project: String
  def testCommand: String
  def testCompileCommand: String
  def publishCommand: String
  def docCommand: String
  def binaryName: String
  def runCommandsArgs: List[String] = Nil
  def environment: Map[String, String] = Map.empty

  final val projectDir = communitybuildDir.resolve("community-projects").resolve(project)

  /** Publish this project to the local Maven repository */
  final def publish(): Unit =
    log(s"Publishing $project")
    if publishCommand eq null then
      throw RuntimeException(s"Publish command is not specified for $project. Project details:\n$this")
    val exitCode = exec(projectDir, binaryName, (runCommandsArgs :+ publishCommand), environment)
    if exitCode != 0 then
      throw RuntimeException(s"Publish command exited with code $exitCode for project $project. Project details:\n$this")

  final def doc(): Unit =
    log(s"Documenting $project")
    if docCommand eq null then
      throw RuntimeException(s"Doc command is not specified for $project. Project details:\n$this")
    val exitCode = exec(projectDir, binaryName, (runCommandsArgs :+ docCommand), environment)
    if exitCode != 0 then
      throw RuntimeException(s"Doc command exited with code $exitCode for project $project. Project details:\n$this")

  final def build(): Int = exec(projectDir, binaryName, buildCommands, environment)

  final def buildCommands = runCommandsArgs :+ testCompileCommand

end CommunityProject

val sbt1Version = "1.12.1"
val sbt2Version = "2.0.3"

final case class SbtCommunityProject(
    project: String,
    testCmd: String = "test",
    testCompileCmd: String = "Test/compile",
    extraSbtArgs: List[String] = Nil,
    publishCmd: String = "publishLocal",
    docCmd: String = "doc",
    scalacOptions: List[String] = SbtCommunityProject.scalacOptions,
    override val environment: Map[String, String] = Map.empty,
  ) extends CommunityProject:
  override val binaryName: String = "sbt"

  private def scalacOptionsString: String =
    scalacOptions.map("\"" + _ + "\"").mkString("List(", ",", ")")

  private val baseCommand =
    "set Global/logLevel := Level.Error; "
    ++ (if scalacOptions.isEmpty then "" else s"""set Global/scalacOptions ++= $scalacOptionsString;""")

  override val testCommand =
    """set Global/testOptions += Tests.Argument(TestFramework("munit.Framework"), "+l"); """
    ++ s"$baseCommand$testCmd"

  override val testCompileCommand =
    s"$baseCommand$testCompileCmd"

  override val publishCommand =
    if publishCmd eq null then null else s"$baseCommand$publishCmd"

  override val docCommand =
    if docCmd eq null then null else
      val cmd = if docCmd.startsWith(";") then docCmd else s";$docCmd"
      s"$baseCommand set every useScaladoc := true; set every doc/logLevel := Level.Warn $cmd "

  override val runCommandsArgs: List[String] =
    // Run the sbt command with the compiler version and sbt plugin set in the build
    val sbtProps = Option(System.getProperty("sbt.ivy.home")) match
      case Some(ivyHome) => List(s"-Dsbt.ivy.home=$ivyHome")
      case _ => Nil
    extraSbtArgs ++ sbtProps ++ List(
      s"-Dsbt.version=$sbtVersion",
      s"-Dsbt.boot=$bootDir",
      "-Dsbt.supershell=false",
    )

object SbtCommunityProject:
  def scalacOptions = Nil

object projects:

  private def forceDoc(projects: String*) =
    projects.map(project =>
      s""";set $project/Compile/doc/sources ++= ($project/Compile/doc/dotty.tools.sbtplugin.DottyPlugin.autoImport.tastyFiles).value ;$project/doc"""
    ).mkString(" ")

  private def removeRelease8(projects: String*): String =
    projects.map(project =>
      s"""set $project/Compile/scalacOptions := ($project/Compile/scalacOptions).value.filterNot(opt => opt == "-release" || opt == "-java-output-version" || opt == "8")"""
    ).mkString("; ")

  private def aggregateDoc(in: String)(projects: String*) =
    val tastyFiles =
      (in +: projects).map(p => s"($p/Compile/doc/dotty.tools.sbtplugin.DottyPlugin.autoImport.tastyFiles).value").mkString(" ++ ")
    s""";set $in/Compile/doc/sources ++= file("a.scala") +: ($tastyFiles) ;$in/doc"""

  lazy val `sbt-compile-benchmark` = SbtCommunityProject(
    project = "sbt-compile-benchmark",
  )

  lazy val scalaz = SbtCommunityProject(
    project = "scalaz",
    testCmd = "rootJVM/test",
    testCompileCmd = "rootJVM/Test/compile",
    docCmd = forceDoc("effectJVM"),
  )

  lazy val parboiled2 = SbtCommunityProject(
    project = "parboiled2",
    testCmd = "parboiledCoreJVM3/testFull; parboiledJVM3/testFull",
    testCompileCmd = "parboiledCoreJVM3/Test/compile; parboiledJVM3/Test/compile",
    publishCmd = "publishLocal",
    scalacOptions = SbtCommunityProject.scalacOptions.filter(_ != "-Xcheck-macros"),
  )

end projects

def allProjects = List(
  projects.parboiled2,
  projects.scalaz,
)

lazy val projectMap = allProjects.groupBy(_.project)
