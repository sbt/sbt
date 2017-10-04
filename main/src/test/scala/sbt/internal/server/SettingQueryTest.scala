/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package server

import java.io._
import java.net._
import java.nio.file._
import java.util.concurrent._

import scala.collection.mutable

import xsbti._
import sbt.io.IO
import sbt.internal.util._
import sbt.internal.BuildStreams.{ Streams => _, _ }
import sbt.internal.Load._
import sbt.util._
import sbt.BuildPaths._
import sbt.Def.{ ScopeLocal, ScopedKey, Setting }
import sbt.Keys._

object SettingQueryTest extends org.specs2.mutable.Specification {
  implicit class PathOps(val path: Path) extends AnyVal {
    def /(other: String): Path = if (other == ".") path else path resolve other
  }

  val baseDir: Path = Files createTempDirectory "sbt-setting-query-test"
  val globalDir: Path = Files createTempDirectory "sbt-setting-query-test-global-dir"
  val bootDir: Path = Files createTempDirectory "sbt-setting-query-test-boot-dir"
  val ivyHome: Path = Files createTempDirectory "sbt-setting-query-test-ivy-home"
  val logFile: File = File.createTempFile("sbt", ".log")

  val baseFile: File = baseDir.toFile
  val baseUri: URI = IO directoryURI baseFile
  IO assertAbsolute baseUri

  val globalDirFile: File = globalDir.toFile

  def ??? : Nothing = { Thread.dumpStack(); throw new NotImplementedError }

  val noopLoader: ClassLoader = new URLClassLoader(Array(), null)

  object NoGlobalLock extends GlobalLock {
    def apply[T](lockFile: File, run: Callable[T]) = run.call()
  }

  lazy val structure: BuildStructure = {
    val projectSettings: Seq[Setting[_]] = Seq(scalaVersion := "2.12.1")

    val appConfig: AppConfiguration = new AppConfiguration {
      def baseDirectory(): File = baseFile
      def arguments(): Array[String] = Array()
      def provider(): AppProvider = new AppProvider {
        def scalaProvider(): ScalaProvider = new ScalaProvider { scalaProvider =>
          def launcher(): Launcher = new Launcher {
            def getScala(version: String): ScalaProvider = getScala(version, "")
            def getScala(version: String, reason: String): ScalaProvider =
              getScala(version, reason, "org.scala-lang")
            def getScala(version: String, reason: String, scalaOrg: String): ScalaProvider =
              scalaProvider

            def app(id: ApplicationID, version: String): AppProvider = ???

            def topLoader(): ClassLoader = noopLoader
            def globalLock(): GlobalLock = NoGlobalLock
            def bootDirectory(): File = bootDir.toFile
            def ivyRepositories(): Array[Repository] = Array()
            def appRepositories(): Array[Repository] = Array()
            def isOverrideRepositories: Boolean = false
            def ivyHome(): File = SettingQueryTest.this.ivyHome.toFile
            def checksums(): Array[String] = Array()
          }
          def version(): String = "2.12.1"

          def loader(): ClassLoader = noopLoader
          def jars(): Array[File] = Array(libraryJar, compilerJar)

          def libraryJar(): File = new File("scala-library.jar")
          def compilerJar(): File = new File("scala-compiler.jar")

          def app(id: ApplicationID): AppProvider = ???
        }

        def id(): ApplicationID = sbt.ApplicationID(
          "org.scala-sbt",
          "sbt",
          "0.13.13",
          "sbt.xMain",
          components = Seq(),
          crossVersionedValue = CrossValue.Disabled,
          extra = Seq()
        )

        def loader(): ClassLoader = noopLoader

        def entryPoint(): Class[_] = ???
        def mainClass(): Class[_ <: AppMain] = ???
        def newMain(): AppMain = ???

        def mainClasspath(): Array[File] = Array()

        def components(): ComponentProvider = new ComponentProvider {
          def componentLocation(id: String): File = ???
          def component(componentID: String): Array[File] = ???
          def defineComponent(componentID: String, components: Array[File]): Unit = ???
          def addToComponent(componentID: String, components: Array[File]): Boolean = ???
          def lockFile(): File = ???
        }
      }
    }

    val state: State =
      StandardMain
        .initialState(appConfig, initialDefinitions = Seq(), preCommands = Seq())
        .put(globalBaseDirectory, globalDirFile)

    val config0 = defaultPreGlobal(state, baseFile, globalDirFile, state.log)
    val config = defaultWithGlobal(state, baseFile, config0, globalDirFile, state.log)

    val buildUnit: BuildUnit = {
      val loadedPlugins: LoadedPlugins =
        noPlugins(projectStandard(baseFile),
                  config.copy(pluginManagement = config.pluginManagement.forPlugin))

      val project: Project = {
        val project0 = Project("t", baseFile) settings projectSettings
        val fileToLoadedSbtFileMap = new mutable.HashMap[File, LoadedSbtFile]
        val autoPlugins = loadedPlugins.detected.deducePluginsFromProject(project0, state.log)
        val injectSettings = config.injectSettings
        resolveProject(project0,
                       autoPlugins,
                       loadedPlugins,
                       injectSettings,
                       fileToLoadedSbtFileMap,
                       state.log)
      }

      val projects: Seq[Project] = Seq(project)
      val builds: Seq[BuildDef] = BuildDef.defaultAggregated(project.id, Nil) :: Nil
      val defs: LoadedDefinitions =
        new LoadedDefinitions(baseFile, Nil, noopLoader, builds, projects, Nil)
      new BuildUnit(baseUri, baseFile, defs, loadedPlugins)
    }

    val (partBuildUnit: PartBuildUnit, projectRefs: List[ProjectReference]) = loaded(buildUnit)
    val partBuildUnits: Map[URI, PartBuildUnit] = Map(buildUnit.uri -> partBuildUnit)
    val allProjectRefs: Map[URI, List[ProjectReference]] = Map(buildUnit.uri -> projectRefs)
    checkAll(allProjectRefs, partBuildUnits)

    val partBuild: PartBuild = new PartBuild(baseUri, partBuildUnits)
    val loadedBuild: LoadedBuild = resolveProjects(partBuild)

    val units: Map[URI, LoadedBuildUnit] = loadedBuild.units

    val settings: Seq[Setting[_]] = finalTransforms(
      buildConfigurations(loadedBuild, getRootProject(units), config.injectSettings))
    val delegates: Scope => Seq[Scope] = defaultDelegates(loadedBuild)
    val scopeLocal: ScopeLocal = EvaluateTask.injectStreams
    val display: Show[ScopedKey[_]] = Project showLoadingKey loadedBuild

    val data: Settings[Scope] = Def.make(settings)(delegates, scopeLocal, display)
    val extra: KeyIndex => BuildUtil[_] = index => BuildUtil(baseUri, units, index, data)

    val index: StructureIndex = structureIndex(data, settings, extra, units)
    val streams: State => Streams = mkStreams(units, baseUri, data)

    val structure: BuildStructure =
      new BuildStructure(units, baseUri, settings, data, index, streams, delegates, scopeLocal)

    structure
  }

  def query(setting: String): String = {
    import sbt.protocol._
    val req: SettingQuery = sbt.protocol.SettingQuery(setting)
    val rsp: SettingQueryResponse = server.SettingQuery.handleSettingQuery(req, structure)
    val bytes: Array[Byte] = Serialization serializeEventMessage rsp
    val payload: String = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
    payload
  }

  // -.- avoid specs2's ko/ok
  import org.specs2.matcher.MatchResult
  def qok(x: String, t: String): String => MatchResult[Any] =
    query(_) must_== """{"type":"SettingQuerySuccess","value":""" + x + ""","contentType":"""" + t + """"}"""
  def qko(msg: String): String => MatchResult[Any] =
    query(_) must_== """{"type":"SettingQueryFailure","message":"""" + msg + """"}"""

  "setting query" should {
    "t/scalaVersion" in qok("\"2.12.1\"", "java.lang.String")
    // "t/pollInterval" in qok("500", "Int")
    "t/sourcesInBase" in qok("true", "Boolean")
    "t/startYear" in qok("null", "scala.Option[Int]")
    "t/scalaArtifacts" in qok(
      """["scala-library","scala-compiler","scala-reflect","scala-actors","scalap"]""",
      "scala.collection.Seq[java.lang.String]")

    "t/libraryDependencies" in qok(
      """[{"organization":"org.scala-lang","name":"scala-library","revision":"2.12.1","isChanging":false,"isTransitive":true,"isForce":false,"explicitArtifacts":[],"inclusions":[],"exclusions":[],"extraAttributes":{},"crossVersion":{"type":"Disabled"}}]""",
      "scala.collection.Seq[sbt.librarymanagement.ModuleID]"
    )

    "scalaVersion" in qko("Not a valid project ID: scalaVersion\\nscalaVersion\\n            ^")
    "t/scalacOptions" in qko(
      s"""Key ProjectRef(uri(\\"$baseUri\\"), \\"t\\") / Compile / scalacOptions is a task, can only query settings""")
    "t/fooo" in qko(
      "Expected ':' (if selecting a configuration)\\nNot a valid key: fooo (similar: fork)\\nt/fooo\\n      ^")
  }
}
