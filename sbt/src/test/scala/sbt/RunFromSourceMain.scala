/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import scala.annotation.tailrec

import xsbti._

object RunFromSourceMain {
  private val sbtVersion = "1.1.0" // "dev"
  private val scalaVersion = "2.12.4"

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

  private def getConf(baseDir: File, args: Seq[String]): AppConfiguration = new AppConfiguration {
    def baseDirectory = baseDir
    def arguments = args.toArray
    def provider = new AppProvider { appProvider =>
      def bootDirectory: File = file(sys.props("user.home")) / ".sbt" / "boot"
      def scalaHome: File = bootDirectory / s"scala-$scalaVersion"
      def scalaProvider = new ScalaProvider { scalaProvider =>
        def scalaOrg = "org.scala-lang"
        def launcher = new Launcher {
          def getScala(version: String) = getScala(version, "")
          def getScala(version: String, reason: String) = getScala(version, reason, scalaOrg)
          def getScala(version: String, reason: String, scalaOrg: String) = scalaProvider
          def app(id: xsbti.ApplicationID, version: String) = appProvider
          def topLoader = new java.net.URLClassLoader(Array(), null)
          def globalLock = noGlobalLock
          def bootDirectory = appProvider.bootDirectory
          def ivyHome = file(sys.props("user.home")) / ".ivy2"
          def ivyRepositories = Array(new PredefinedRepository { def id() = Predefined.Local })
          def appRepositories = Array(new PredefinedRepository { def id() = Predefined.Local })
          def isOverrideRepositories = false
          def checksums = Array("sha1", "md5")
        }
        def version = scalaVersion
        def libDir: File = scalaHome / "lib"
        def jar(name: String): File = libDir / s"$name.jar"
        def libraryJar = jar("scala-library")
        def compilerJar = jar("scala-compiler")
        def jars = libDir.listFiles(f => !f.isDirectory && f.getName.endsWith(".jar"))
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

      def mainClasspath =
        buildinfo.TestBuildInfo.fullClasspath.iterator
          .map(s => file(s.stripPrefix("Attributed(").stripSuffix(")")))
          .toArray

      def loader = new java.net.URLClassLoader(mainClasspath map (_.toURI.toURL), null)
      def entryPoint = classOf[xMain]
      def mainClass = classOf[xMain]
      def newMain = new xMain

      def components = new ComponentProvider {
        def componentLocation(id: String) = appHome / id
        def component(id: String) = IO.listFiles(componentLocation(id), _.isFile)
        def defineComponent(id: String, components: Array[File]) = ()
        def addToComponent(id: String, components: Array[File]) = false
        def lockFile = null
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
