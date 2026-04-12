/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.ProjectExtra.extract
import sbt.internal.classpath.AlternativeZincUtil
import sbt.internal.inc.{ ScalaInstance, ZincLmUtil }
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.util.Terminal
import sbt.io.IO
import sbt.librarymanagement.DependencyResolution
import sbt.util.Logger
import xsbti.HashedVirtualFileRef
import xsbti.compile.ClasspathOptionsUtil

object ConsoleProject:
  def consoleProjectTask =
    Def.task {
      val st = Keys.state.value
      val si = (Keys.consoleProject / Keys.scalaInstance).value
      val dr = (LocalRootProject / Keys.dependencyResolution).value
      val compilerBridgeBinaryBin =
        (LocalRootProject / Keys.consoleProject / Keys.scalaCompilerBridgeBin).value
      ConsoleProject(
        st,
        si,
        dr,
        compilerBridgeBinaryBin,
        (Keys.consoleProject / Keys.initialCommands).value
      )(using
        Keys.streams.value.log
      )
      println()
    }

  def apply(
      state: State,
      si: ScalaInstance,
      dr: DependencyResolution,
      compilerBridgeBinaryBin: Seq[HashedVirtualFileRef],
      extra: String,
      cleanupCommands: String = "",
      options: Seq[String] = Nil
  )(using
      log: Logger
  ): Unit = {
    val extracted = Project.extract(state)
    val cpImports = new Imports(extracted, state)
    // Bindings are ignored by Scala 3 bridge: https://github.com/scala/scala3/issues/5069
    // Workaround: vals are injected via initialCommands from ConsoleProjectBindings holder.
    // bindings are still passed to Console for Scala 2 backward compatibility.
    val bindings =
      ("currentState" -> state) :: ("extracted" -> extracted) :: ("cpHelpers" -> cpImports) :: Nil
    val unit = extracted.currentUnit
    val tempDir0 = extracted.get(Keys.consoleProject / Keys.taskTemporaryDirectory)
    val tempDir = IO.createUniqueDirectory(tempDir0).toPath()
    val conv = extracted.get(Keys.fileConverter)
    val g = BuildPaths.getGlobalBase(state)
    val zincDir = BuildPaths.getZincDirectory(state, g)
    val app = state.configuration
    val launcher = app.provider.scalaProvider.launcher
    val compiler = compilerBridgeBinaryBin.toList match
      case jar :: xs =>
        AlternativeZincUtil.scalaCompiler(
          scalaInstance = si,
          classpathOptions = ClasspathOptionsUtil.repl,
          compilerBridgeJar = conv.toPath(jar).toFile(),
          classLoaderCache = state.get(BasicKeys.classLoaderCache)
        )
      case Nil =>
        ZincLmUtil.scalaCompiler(
          scalaInstance = si,
          classpathOptions = ClasspathOptionsUtil.repl,
          globalLock = launcher.globalLock,
          componentProvider = app.provider.components,
          secondaryCacheDir = Option(zincDir),
          dependencyResolution = dr,
          compilerBridgeSource =
            extracted.get(Keys.consoleProject / Keys.scalaCompilerBridgeSource),
          scalaJarsTarget = zincDir,
          classLoaderCache = state.get(BasicKeys.classLoaderCache),
          log = log
        )
    ConsoleProjectBindings.set(state, extracted, cpImports)
    val baseImports = BuildUtil.getImports(unit.unit)
    val bindingDefs = Seq(
      "val currentState = _root_.sbt.internal.ConsoleProjectBindings.state",
      "val extracted = _root_.sbt.internal.ConsoleProjectBindings.extracted",
      "val cpHelpers = _root_.sbt.internal.ConsoleProjectBindings.cpHelpers",
    )
    val bindingImports = BuildUtil.importAll(bindings.map(_._1))
    val allLines = baseImports ++ bindingDefs ++ bindingImports
    val initCommands = allLines.mkString("", ";\n", ";\n\n") + extra
    val loader = ClasspathUtil.makeLoader(unit.classpath, si, tempDir)
    val terminal = Terminal.get
    // TODO - Hook up dsl classpath correctly...
    try
      (new Console(compiler))(
        unit.classpath.map(_.toFile),
        options,
        initCommands,
        cleanupCommands,
        terminal
      )(Some(loader), bindings).get
      ()
    finally ConsoleProjectBindings.clear()
  }

  /** Conveniences for consoleProject that shouldn't normally be used for builds. */
  final class Imports private[sbt] (extracted: Extracted, state: State) {
    import extracted.*
    implicit def taskKeyEvaluate[T](t: TaskKey[T]): Evaluate[T] =
      new Evaluate(runTask(t, state)._2)
    implicit def settingKeyEvaluate[T](s: SettingKey[T]): Evaluate[T] = new Evaluate(get(s))
  }
  final class Evaluate[T] private[sbt] (val eval: T)
end ConsoleProject
