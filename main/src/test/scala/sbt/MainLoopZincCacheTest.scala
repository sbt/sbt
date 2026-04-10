/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.util.concurrent.Callable

import sbt.internal.util.{ AttributeMap, ConsoleOut, GlobalLogging, MainAppender }
import sbt.io.IO
import sbt.io.syntax.*
import sbt.librarymanagement.SbtArtifacts

import xsbti.{
  AppConfiguration,
  AppMain,
  AppProvider,
  ApplicationID as XApplicationID,
  ComponentProvider,
  CrossValue,
  GlobalLock,
  Launcher,
  Repository,
  ScalaProvider,
}

object MainLoopZincCacheTest extends verify.BasicTestSuite:

  private object Stubs:
    val NoGlobalLock: GlobalLock = new GlobalLock:
      def apply[T](lockFile: File, run: Callable[T]): T = run.call()

    lazy val componentProvider: ComponentProvider = new ComponentProvider:
      def componentLocation(id: String): File = new File(id)
      def component(id: String): Array[File] = Array.empty
      def defineComponent(id: String, jars: Array[File]): Unit = ()
      def addToComponent(id: String, jars: Array[File]): Boolean = false
      def lockFile(): File = new File(System.getProperty("java.io.tmpdir"), "stub-components.lock")

    lazy val launcher: Launcher = new Launcher:
      def getScala(version: String): ScalaProvider = Stubs.scalaProvider
      def getScala(version: String, reason: String): ScalaProvider = Stubs.scalaProvider
      def getScala(version: String, reason: String, scalaOrg: String): ScalaProvider =
        Stubs.scalaProvider
      def app(id: XApplicationID, version: String): AppProvider = Stubs.appProvider
      def topLoader(): ClassLoader = classOf[String].getClassLoader
      def globalLock(): GlobalLock = NoGlobalLock
      def bootDirectory(): File = new File(System.getProperty("java.io.tmpdir"))
      def ivyRepositories(): Array[Repository] = Array.empty
      def appRepositories(): Array[Repository] = Array.empty
      def isOverrideRepositories: Boolean = false
      def ivyHome(): File = new File(System.getProperty("java.io.tmpdir"))
      def checksums(): Array[String] = Array.empty

    lazy val scalaProvider: ScalaProvider = new ScalaProvider:
      def launcher(): Launcher = Stubs.launcher
      def version(): String = "3.8.3"
      def loader(): ClassLoader = classOf[String].getClassLoader
      def jars(): Array[File] = Array.empty
      def libraryJar(): File = new File("scala-library.jar")
      def compilerJar(): File = new File("scala-compiler.jar")
      def app(id: XApplicationID): AppProvider = Stubs.appProvider

    val appId: sbt.ApplicationID = sbt.ApplicationID(
      "org.scala-sbt",
      "sbt",
      "2.0.0",
      "sbt.xMain",
      Seq.empty,
      CrossValue.Disabled,
      Seq.empty,
    )

    lazy val appProvider: AppProvider = new AppProvider:
      def scalaProvider(): ScalaProvider = Stubs.scalaProvider
      def id(): XApplicationID = appId
      def loader(): ClassLoader = classOf[String].getClassLoader
      def mainClass(): Class[? <: AppMain] = classOf[xMain]
      def entryPoint(): Class[?] = classOf[xMain]
      def newMain(): AppMain = new xMain()
      def mainClasspath(): Array[File] = Array.empty
      def components(): ComponentProvider = Stubs.componentProvider

    def appConfiguration(baseDir: File): AppConfiguration = new AppConfiguration:
      def arguments(): Array[String] = Array.empty
      def baseDirectory(): File = baseDir
      def provider(): AppProvider = Stubs.appProvider

  end Stubs

  private def mkState(zincRoot: File, logFile: File, baseDir: File) =
    val attrs = AttributeMap.empty
      .put(BuildPaths.globalBaseDirectory, zincRoot.getParentFile)
      .put(BuildPaths.globalZincDirectory, zincRoot)
    State(
      configuration = Stubs.appConfiguration(baseDir),
      definedCommands = Nil,
      exitHooks = Set.empty,
      onFailure = None,
      remainingCommands = Nil,
      history = State.newHistory,
      attributes = attrs,
      globalLogging = GlobalLogging.initial(
        MainAppender.globalDefault(ConsoleOut.globalProxy),
        logFile,
        ConsoleOut.globalProxy
      ),
      currentCommand = None,
      next = State.Continue
    )

  test("deleteZincBridgeSecondaryCache removes org.scala-sbt under global zinc"):
    IO.withTemporaryDirectory: tmp =>
      val zincRoot = tmp / "zinc"
      val bridge = zincRoot / SbtArtifacts.Organization
      IO.write(bridge / "marker.txt", "cached")
      val logFile = File.createTempFile("sbt-mlz", ".log")
      try
        MainLoop.deleteZincBridgeSecondaryCache(mkState(zincRoot, logFile, tmp))
        assert(!bridge.exists(), s"expected $bridge deleted")
      finally IO.delete(logFile)

  test("deleteZincBridgeSecondaryCache is a no-op when org.scala-sbt is absent"):
    IO.withTemporaryDirectory: tmp =>
      val zincRoot = tmp / "zinc"
      IO.createDirectory(zincRoot)
      val logFile = File.createTempFile("sbt-mlz", ".log")
      try
        MainLoop.deleteZincBridgeSecondaryCache(mkState(zincRoot, logFile, tmp))
        assert(zincRoot.exists())
      finally IO.delete(logFile)

  test("deleteZincBridgeSecondaryCache respects sbt.global.zinc system property"):
    IO.withTemporaryDirectory: customZinc =>
      val prop = BuildPaths.GlobalZincProperty
      val prev = sys.props.get(prop)
      try
        sys.props(prop) = customZinc.getAbsolutePath
        val bridge = customZinc / SbtArtifacts.Organization
        IO.write(bridge / "x.jar", Array.emptyByteArray)
        val logFile = File.createTempFile("sbt-mlz", ".log")
        try
          val attrs = AttributeMap.empty.put(
            BuildPaths.globalBaseDirectory,
            customZinc.getParentFile / "unused-base"
          )
          val state = State(
            configuration = Stubs.appConfiguration(customZinc.getParentFile),
            definedCommands = Nil,
            exitHooks = Set.empty,
            onFailure = None,
            remainingCommands = Nil,
            history = State.newHistory,
            attributes = attrs,
            globalLogging = GlobalLogging.initial(
              MainAppender.globalDefault(ConsoleOut.globalProxy),
              logFile,
              ConsoleOut.globalProxy
            ),
            currentCommand = None,
            next = State.Continue
          )
          MainLoop.deleteZincBridgeSecondaryCache(state)
          assert(!bridge.exists())
        finally IO.delete(logFile)
      finally
        prev match
          case Some(v) => sys.props(prop) = v
          case None    => sys.props.remove(prop)

end MainLoopZincCacheTest
