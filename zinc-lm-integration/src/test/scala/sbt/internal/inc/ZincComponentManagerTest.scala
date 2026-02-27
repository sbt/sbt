/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package inc

import java.io.File
import java.util.concurrent.Callable

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.Result
import _root_.sbt.io.IO
import _root_.sbt.io.syntax.*
import xsbti.{ ComponentProvider, GlobalLock, Logger }

object ZincComponentManagerTest extends Properties:
  override def tests: List[Test] = List(
    example("files should return defined component files", testFilesReturnsDefined),
    example(
      "files should throw InvalidComponent when component is missing and IfMissing.Fail",
      testFilesMissingFail,
    ),
    example("file should return single file for a component", testFileSingle),
    example("file should throw when multiple files exist for a component", testFileMultiple),
    example("define should register component files", testDefine),
    example("files should call IfMissing.Define when component is missing", testFilesMissingDefine),
    example(
      "files should cache to secondary cache when IfMissing.Define with useSecondaryCache",
      testSecondaryCacheWrite,
    ),
    example(
      "files should retrieve from secondary cache when component is missing locally",
      testSecondaryCacheRead,
    ),
  )

  private val noOpLock: GlobalLock = new GlobalLock:
    override def apply[T](file: File, callable: Callable[T]): T = callable.call()

  private val silentLogger: Logger = new Logger:
    override def error(msg: java.util.function.Supplier[String]): Unit = ()
    override def warn(msg: java.util.function.Supplier[String]): Unit = ()
    override def info(msg: java.util.function.Supplier[String]): Unit = ()
    override def debug(msg: java.util.function.Supplier[String]): Unit = ()
    override def trace(exception: java.util.function.Supplier[Throwable]): Unit = ()

  private def withTempDir[A](f: File => A): A =
    IO.withTemporaryDirectory(f)

  private def fileProvider(baseDir: File): ComponentProvider = new ComponentProvider:
    private def componentDir(id: String): File =
      val dir = baseDir / id
      IO.createDirectory(dir)
      dir

    override def componentLocation(id: String): File = componentDir(id)

    override def component(componentID: String): Array[File] =
      val dir = componentDir(componentID)
      if dir.exists() then dir.listFiles().filter(_.isFile)
      else Array.empty

    override def defineComponent(componentID: String, files: Array[File]): Unit =
      val dir = componentDir(componentID)
      files.foreach: f =>
        IO.copyFile(f, dir / f.getName)

    override def addToComponent(componentID: String, files: Array[File]): Boolean =
      defineComponent(componentID, files)
      true

    override def lockFile(): File = baseDir / ".lock"

  private def createTempJar(dir: File, name: String): File =
    val f = dir / name
    IO.write(f, "fake-jar-content")
    f

  def testFilesReturnsDefined: Result = withTempDir: tmpDir =>
    val provider = fileProvider(tmpDir / "components")
    val manager = new ZincComponentManager(noOpLock, provider, None, silentLogger)
    val sourceDir = tmpDir / "source"
    IO.createDirectory(sourceDir)
    val jar = createTempJar(sourceDir, "bridge.jar")
    manager.define("test-component", Seq(jar))
    val result = manager.files("test-component")(IfMissing.fail)
    Result
      .assert(result.nonEmpty)
      .log(s"expected non-empty files, got: ${result.toList}")

  def testFilesMissingFail: Result = withTempDir: tmpDir =>
    val provider = fileProvider(tmpDir / "components")
    val manager = new ZincComponentManager(noOpLock, provider, None, silentLogger)
    try
      manager.files("nonexistent")(IfMissing.fail)
      Result.failure.log("expected InvalidComponent to be thrown")
    catch
      case _: InvalidComponent => Result.success
      case e: Throwable =>
        Result.failure.log(s"unexpected exception: ${e.getClass.getName}: ${e.getMessage}")

  def testFileSingle: Result = withTempDir: tmpDir =>
    val provider = fileProvider(tmpDir / "components")
    val manager = new ZincComponentManager(noOpLock, provider, None, silentLogger)
    val sourceDir = tmpDir / "source"
    IO.createDirectory(sourceDir)
    val jar = createTempJar(sourceDir, "single.jar")
    manager.define("single-component", Seq(jar))
    val result = manager.file("single-component")(IfMissing.fail)
    Result
      .assert(result.getName == "single.jar")
      .log(s"expected single.jar, got: ${result.getName}")

  def testFileMultiple: Result = withTempDir: tmpDir =>
    val provider = fileProvider(tmpDir / "components")
    val manager = new ZincComponentManager(noOpLock, provider, None, silentLogger)
    val sourceDir = tmpDir / "source"
    IO.createDirectory(sourceDir)
    val jar1 = createTempJar(sourceDir, "multi-component.jar")
    val jar2 = createTempJar(sourceDir, "b.jar")
    manager.define("multi-component", Seq(jar1, jar2))
    val result = manager.file("multi-component")(IfMissing.fail)
    val remaining = provider.component("multi-component").filter(_.isFile)
    Result
      .assert(result.getName == "multi-component.jar")
      .and(Result.assert(remaining.length == 1))
      .log(s"got: ${result.getName}, remaining: ${remaining.toList.map(_.getName)}")

  def testDefine: Result = withTempDir: tmpDir =>
    val provider = fileProvider(tmpDir / "components")
    val manager = new ZincComponentManager(noOpLock, provider, None, silentLogger)
    val sourceDir = tmpDir / "source"
    IO.createDirectory(sourceDir)
    val jar = createTempJar(sourceDir, "defined.jar")
    manager.define("def-component", Seq(jar))
    val files = provider.component("def-component")
    Result
      .assert(files.length == 1)
      .and(Result.assert(files.head.getName == "defined.jar"))
      .log(s"files: ${files.toList.map(_.getName)}")

  def testFilesMissingDefine: Result = withTempDir: tmpDir =>
    val secondaryDir = tmpDir / "secondary"
    IO.createDirectory(secondaryDir)
    val provider = fileProvider(tmpDir / "components")
    val manager = new ZincComponentManager(noOpLock, provider, Some(secondaryDir), silentLogger)
    val sourceDir = tmpDir / "source"
    IO.createDirectory(sourceDir)
    val jar = createTempJar(sourceDir, "created.jar")
    val ifMissing = IfMissing.define(
      false,
      manager.define("lazy-component", Seq(jar)),
    )
    val result = manager.files("lazy-component")(ifMissing)
    Result
      .assert(result.nonEmpty)
      .log(s"expected non-empty files after define, got: ${result.toList}")

  def testSecondaryCacheWrite: Result = withTempDir: tmpDir =>
    val secondaryDir = tmpDir / "secondary"
    IO.createDirectory(secondaryDir)
    val provider = fileProvider(tmpDir / "components")
    val manager = new ZincComponentManager(noOpLock, provider, Some(secondaryDir), silentLogger)
    val sourceDir = tmpDir / "source"
    IO.createDirectory(sourceDir)
    val jar = createTempJar(sourceDir, "cached.jar")
    val ifMissing = IfMissing.define(
      true,
      manager.define("cache-component", Seq(jar)),
    )
    manager.files("cache-component")(ifMissing)
    val sbtOrgDir = secondaryDir / "org.scala-sbt"
    val cachedFiles = if sbtOrgDir.exists() then sbtOrgDir.listFiles().toList else Nil
    Result
      .assert(cachedFiles.nonEmpty)
      .log(s"expected files in secondary cache dir, got: $cachedFiles")

  def testSecondaryCacheRead: Result = withTempDir: tmpDir =>
    val secondaryDir = tmpDir / "secondary"
    IO.createDirectory(secondaryDir)
    val provider1 = fileProvider(tmpDir / "components1")
    val manager1 = new ZincComponentManager(noOpLock, provider1, Some(secondaryDir), silentLogger)
    val sourceDir = tmpDir / "source"
    IO.createDirectory(sourceDir)
    val jar = createTempJar(sourceDir, "shared.jar")
    val ifMissing1 = IfMissing.define(
      true,
      manager1.define("shared-component", Seq(jar)),
    )
    manager1.files("shared-component")(ifMissing1)

    val provider2 = fileProvider(tmpDir / "components2")
    val manager2 = new ZincComponentManager(noOpLock, provider2, Some(secondaryDir), silentLogger)
    val result = manager2.files("shared-component")(IfMissing.fail)
    Result
      .assert(result.nonEmpty)
      .log(s"expected to retrieve component from secondary cache, got: ${result.toList}")

end ZincComponentManagerTest
