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
import sbt.internal.util.Terminal
import sbt.util.Logger
import xsbti.compile.ClasspathOptionsUtil

object ConsoleProject {
  def apply(state: State, extra: String, cleanupCommands: String = "", options: Seq[String] = Nil)(
      using log: Logger
  ): Unit = {
    val extracted = Project.extract(state)
    val cpImports = new Imports(extracted, state)
    val bindings =
      ("currentState" -> state) :: ("extracted" -> extracted) :: ("cpHelpers" -> cpImports) :: Nil
    val unit = extracted.currentUnit
    val (state1, dependencyResolution) =
      extracted.runTask(Keys.dependencyResolution, state)
    val (_, scalaCompilerBridgeBinaryJar) =
      extracted.runTask(Keys.consoleProject / Keys.scalaCompilerBridgeBinaryJar, state1)
    val scalaInstance = {
      val scalaProvider = state.configuration.provider.scalaProvider
      ScalaInstance(scalaProvider.version, scalaProvider)
    }
    val g = BuildPaths.getGlobalBase(state)
    val zincDir = BuildPaths.getZincDirectory(state, g)
    val app = state.configuration
    val launcher = app.provider.scalaProvider.launcher
    val compiler = scalaCompilerBridgeBinaryJar match {
      case Some(jar) =>
        AlternativeZincUtil.scalaCompiler(
          scalaInstance = scalaInstance,
          classpathOptions = ClasspathOptionsUtil.repl,
          compilerBridgeJar = jar,
          classLoaderCache = state1.get(BasicKeys.classLoaderCache)
        )
      case None =>
        ZincLmUtil.scalaCompiler(
          scalaInstance = scalaInstance,
          classpathOptions = ClasspathOptionsUtil.repl,
          globalLock = launcher.globalLock,
          componentProvider = app.provider.components,
          secondaryCacheDir = Option(zincDir),
          dependencyResolution = dependencyResolution,
          compilerBridgeSource =
            extracted.get(Keys.consoleProject / Keys.scalaCompilerBridgeSource),
          scalaJarsTarget = zincDir,
          classLoaderCache = state1.get(BasicKeys.classLoaderCache),
          log = log
        )
    }
    val imports = BuildUtil.getImports(unit.unit) ++ BuildUtil.importAll(bindings.map(_._1))
    val importString = imports.mkString("", ";\n", ";\n\n")
    val initCommands = importString + extra

    val terminal = Terminal.get
    // TODO - Hook up dsl classpath correctly...
    (new Console(compiler))(
      unit.classpath.map(_.toFile),
      options,
      initCommands,
      cleanupCommands,
      terminal
    )(Some(unit.loader), bindings).get
    ()
  }

  /** Conveniences for consoleProject that shouldn't normally be used for builds. */
  final class Imports private[sbt] (extracted: Extracted, state: State) {
    import extracted._
    implicit def taskKeyEvaluate[T](t: TaskKey[T]): Evaluate[T] =
      new Evaluate(runTask(t, state)._2)
    implicit def settingKeyEvaluate[T](s: SettingKey[T]): Evaluate[T] = new Evaluate(get(s))
  }
  final class Evaluate[T] private[sbt] (val eval: T)
}
