/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package coursierint

import java.io.File
import java.util.concurrent.Callable

import verify.BasicTestSuite
import xsbti.{ Logger => _, _ }
import sbt.util.Logger

object LMCoursierUserAgentSpec extends BasicTestSuite:
  private def unimplemented: Nothing = throw new NotImplementedError

  private def appConfig(sbtVersion: String): AppConfiguration = new AppConfiguration:
    def baseDirectory(): File = new File(".")
    def arguments(): Array[String] = Array()
    def provider(): AppProvider = new AppProvider:
      def scalaProvider(): ScalaProvider = new ScalaProvider:
        def launcher(): Launcher = new Launcher:
          def getScala(version: String): ScalaProvider = unimplemented
          def getScala(version: String, reason: String): ScalaProvider = unimplemented
          def getScala(version: String, reason: String, scalaOrg: String): ScalaProvider =
            unimplemented
          def app(id: ApplicationID, version: String): AppProvider = unimplemented
          def topLoader(): ClassLoader = getClass.getClassLoader
          def globalLock(): GlobalLock = new GlobalLock:
            def apply[T](lockFile: File, run: Callable[T]): T = run.call()
          def bootDirectory(): File = new File(".")
          def ivyRepositories(): Array[Repository] = Array()
          def appRepositories(): Array[Repository] = Array()
          def isOverrideRepositories: Boolean = false
          def ivyHome(): File = new File(".")
          def checksums(): Array[String] = Array()
        def version(): String = "2.12.21"
        def loader(): ClassLoader = getClass.getClassLoader
        def jars(): Array[File] = Array()
        def libraryJar(): File = new File("scala-library.jar")
        def compilerJar(): File = new File("scala-compiler.jar")
        def app(id: ApplicationID): AppProvider = unimplemented
      def id(): ApplicationID = sbt.ApplicationID(
        "org.scala-sbt",
        "sbt",
        sbtVersion,
        "sbt.xMain",
        components = Seq(),
        crossVersionedValue = CrossValue.Disabled,
        extra = Seq()
      )
      def loader(): ClassLoader = getClass.getClassLoader
      def entryPoint(): Class[?] = unimplemented
      def mainClass(): Class[? <: AppMain] = unimplemented
      def newMain(): AppMain = unimplemented
      def mainClasspath(): Array[File] = Array()
      def components(): ComponentProvider = new ComponentProvider:
        def componentLocation(id: String): File = unimplemented
        def component(componentID: String): Array[File] = unimplemented
        def defineComponent(componentID: String, components: Array[File]): Unit = unimplemented
        def addToComponent(componentID: String, components: Array[File]): Boolean = unimplemented
        def lockFile(): File = unimplemented

  test("user agent names the Coursier product before sbt") {
    val ua = LMCoursier.userAgent("2.0.8", None)
    assert(
      ua == "Coursier/2.1 (+https://github.com/coursier) sbt/2.0.8 (+https://www.scala-sbt.org/)",
      ua
    )
    assert(ua.indexOf("Coursier/") < ua.indexOf("sbt/"), ua)
  }

  test("user agent honors the coursier.http.agent override") {
    val ua = LMCoursier.userAgent("2.0.8", Some("MyTool/1.0"))
    assert(ua == "MyTool/1.0", ua)
  }

  test("coursierConfiguration derives a Coursier-first user agent from the sbt version") {
    val conf = LMCoursier.coursierConfiguration(
      rs = Vector(),
      interProjectDependencies = Vector(),
      extraProjects = Vector(),
      fallbackDeps = Vector(),
      appConfig = appConfig("2.0.8"),
      profiles = Set(),
      scalaOrg = "org.scala-lang",
      scalaVer = "2.12.21",
      scalaBinaryVer = "2.12",
      autoScalaLib = false,
      scalaModInfo = None,
      excludeDeps = Vector(),
      credentials = Vector(),
      createLogger = None,
      cacheDirectory = new File("."),
      reconciliation = Vector(),
      ivyHome = None,
      strict = None,
      depsOverrides = Vector(),
      updateConfig = None,
      sameVersions = Vector(),
      enableDependencyOverrides = None,
      localArtifactsShouldBeCached = false,
      lockFile = None,
      log = Logger.Null
    )
    assert(
      conf.userAgent == Some(
        "Coursier/2.1 (+https://github.com/coursier) sbt/2.0.8 (+https://www.scala-sbt.org/)"
      ),
      conf.userAgent.toString
    )
  }
end LMCoursierUserAgentSpec
