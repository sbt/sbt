/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import scala.util.Try
import sbt.util.LogExchange
import scala.annotation.tailrec
import buildinfo.TestBuildInfo
import xsbti._

object RunFromSourceMain {
  private val sbtVersion = "1.1.4" // TestBuildInfo.version
  private val scalaVersion = "2.12.6"

  def fork(workingDirectory: File): Try[Unit] = {
    val fo = ForkOptions()
      .withOutputStrategy(OutputStrategy.StdoutOutput)
    fork(fo, workingDirectory)
  }

  def fork(fo0: ForkOptions, workingDirectory: File): Try[Unit] = {
    val fo = fo0
      .withWorkingDirectory(workingDirectory)
    implicit val runner = new ForkRun(fo)
    val cp = {
      TestBuildInfo.test_classDirectory +: TestBuildInfo.fullClasspath
    }
    val options = Vector(workingDirectory.toString)
    val log = LogExchange.logger("RunFromSourceMain.fork", None, None)
    Run.run("sbt.RunFromSourceMain", cp, options, log)
  }

  def main(args: Array[String]): Unit = args match {
    case Array()              => sys.error(s"Must specify working directory as the first argument")
    case Array(wd, args @ _*) => run(file(wd), args)
  }

  // this arrangement is because Scala does not always properly optimize away
  // the tail recursion in a catch statement
  @tailrec private[sbt] def run(baseDir: File, args: Seq[String]): Unit =
    runImpl(baseDir, args) match {
      case Some((baseDir, args)) => run(baseDir, args)
      case None                  => ()
    }

  private def runImpl(baseDir: File, args: Seq[String]): Option[(File, Seq[String])] =
    try launch(getConf(baseDir, args)) map exit
    catch {
      case r: xsbti.FullReload            => Some((baseDir, r.arguments()))
      case scala.util.control.NonFatal(e) => e.printStackTrace(); errorAndExit(e.toString)
    }

  @tailrec private def launch(conf: AppConfiguration): Option[Int] =
    new xMain().run(conf) match {
      case e: xsbti.Exit     => Some(e.code)
      case _: xsbti.Continue => None
      case r: xsbti.Reboot   => launch(getConf(conf.baseDirectory(), r.arguments()))
      case x                 => handleUnknownMainResult(x)
    }

  private val noGlobalLock = new GlobalLock {
    def apply[T](lockFile: File, run: java.util.concurrent.Callable[T]) = run.call()
  }

  private lazy val bootDirectory: File = file(sys.props("user.home")) / ".sbt" / "boot"
  private lazy val scalaHome: File = {
    val scalaHome0 = bootDirectory / s"scala-$scalaVersion"
    if (scalaHome0.exists) scalaHome0
    else {
      val target = new File("target").getAbsoluteFile
      val fakeboot = target / "fakeboot"
      val scalaHome1 = fakeboot / s"scala-$scalaVersion"
      val scalaHome1Lib = scalaHome1 / "lib"
      val scalaHome1Temp = scalaHome1 / "temp"
      if (scalaHome1Lib.exists) ()
      else {
        IO.createDirectories(List(scalaHome1Lib, scalaHome1Temp))
        val log = sbt.util.LogExchange.logger("run-from-source")
        val lm = {
          import sbt.librarymanagement.ivy.IvyDependencyResolution
          val ivyConfig = InlineIvyConfiguration().withLog(log)
          IvyDependencyResolution(ivyConfig)
        }
        val Name = """(.*)(\-[\d|\.]+)\.jar""".r
        val module = "org.scala-lang" % "scala-compiler" % scalaVersion
        lm.retrieve(module, scalaModuleInfo = None, scalaHome1Temp, log) match {
          case Right(_) =>
            (scalaHome1Temp ** "*.jar").get foreach { x =>
              val Name(head, _) = x.getName
              IO.copyFile(x, scalaHome1Lib / (head + ".jar"))
            }
          case Left(w) => sys.error(w.toString)
        }
      }
      scalaHome1
    }
  }

  private def getConf(baseDir: File, args: Seq[String]): AppConfiguration = new AppConfiguration {
    def baseDirectory = baseDir
    def arguments = args.toArray

    def provider = new AppProvider { appProvider =>
      def scalaProvider = new ScalaProvider { scalaProvider =>
        def scalaOrg = "org.scala-lang"
        def launcher = new Launcher {
          def getScala(version: String) = getScala(version, "")
          def getScala(version: String, reason: String) = getScala(version, reason, scalaOrg)
          def getScala(version: String, reason: String, scalaOrg: String) = scalaProvider
          def app(id: xsbti.ApplicationID, version: String) = appProvider
          def topLoader = new java.net.URLClassLoader(Array(), null)
          def globalLock = noGlobalLock
          def bootDirectory = RunFromSourceMain.bootDirectory
          def ivyHome = file(sys.props("user.home")) / ".ivy2"
          final case class PredefRepo(id: Predefined) extends PredefinedRepository
          import Predefined._
          def ivyRepositories = Array(PredefRepo(Local), PredefRepo(MavenCentral))
          def appRepositories = Array(PredefRepo(Local), PredefRepo(MavenCentral))
          def isOverrideRepositories = false
          def checksums = Array("sha1", "md5")
        }
        def version = scalaVersion
        lazy val libDir: File = RunFromSourceMain.scalaHome / "lib"
        def jar(name: String): File = libDir / s"$name.jar"
        lazy val libraryJar = jar("scala-library")
        lazy val compilerJar = jar("scala-compiler")
        lazy val jars = {
          assert(libDir.exists)
          libDir.listFiles(f => !f.isDirectory && f.getName.endsWith(".jar"))
        }
        def loader = new java.net.URLClassLoader(jars map (_.toURI.toURL), null)
        def app(id: xsbti.ApplicationID) = appProvider
      }

      def id = ApplicationID(
        "org.scala-sbt",
        "sbt",
        sbtVersion,
        "sbt.xMain",
        Seq("xsbti", "extra"),
        CrossValue.Disabled,
        Nil
      )
      def appHome: File = scalaHome / id.groupID / id.name / id.version

      def mainClasspath = buildinfo.TestBuildInfo.fullClasspath.toArray
      def loader = new java.net.URLClassLoader(mainClasspath map (_.toURI.toURL), null)
      def entryPoint = classOf[xMain]
      def mainClass = classOf[xMain]
      def newMain = new xMain

      def components = new ComponentProvider {
        def componentLocation(id: String) = appHome / id
        def component(id: String) = IO.listFiles(componentLocation(id), _.isFile)

        def defineComponent(id: String, files: Array[File]) = {
          val location = componentLocation(id)
          if (location.exists)
            sys error s"Cannot redefine component. ID: $id, files: ${files mkString ","}"
          else {
            copy(files.toList, location)
            ()
          }
        }

        def addToComponent(id: String, files: Array[File]) =
          copy(files.toList, componentLocation(id))

        def lockFile = appHome / "sbt.components.lock"

        private def copy(files: List[File], toDirectory: File): Boolean =
          files exists (copy(_, toDirectory))

        private def copy(file: File, toDirectory: File): Boolean = {
          val to = toDirectory / file.getName
          val missing = !to.exists
          IO.copyFile(file, to)
          missing
        }
      }
    }
  }

  private def handleUnknownMainResult(x: MainResult): Nothing = {
    val clazz = if (x eq null) "" else " (class: " + x.getClass + ")"
    errorAndExit("Invalid main result: " + x + clazz)
  }

  private def errorAndExit(msg: String): Nothing = { System.err.println(msg); exit(1) }
  private def exit(code: Int): Nothing = System.exit(code).asInstanceOf[Nothing]
}
