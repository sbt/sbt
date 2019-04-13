/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.util.Logger
import sbt.internal.util.JLine
import sbt.internal.inc.{ ScalaInstance, ZincLmUtil, ZincUtil }
import xsbti.compile.ClasspathOptionsUtil

object ConsoleProject {
  def apply(state: State, extra: String, cleanupCommands: String = "", options: Seq[String] = Nil)(
      implicit log: Logger
  ): Unit = {
    val extracted = Project extract state
    val cpImports = new Imports(extracted, state)
    val bindings = ("currentState" -> state) :: ("extracted" -> extracted) :: ("cpHelpers" -> cpImports) :: Nil
    val unit = extracted.currentUnit
    val (state1, dependencyResolution) =
      extracted.runTask(Keys.dependencyResolution, state)
    val (_, scalaCompilerBridgeBinaryJar) =
      extracted.runTask(Keys.scalaCompilerBridgeBinaryJar, state1)
    val scalaInstance = {
      val scalaProvider = state.configuration.provider.scalaProvider
      ScalaInstance(scalaProvider.version, scalaProvider.launcher)
    }
    val g = BuildPaths.getGlobalBase(state)
    val zincDir = BuildPaths.getZincDirectory(state, g)
    val app = state.configuration
    val launcher = app.provider.scalaProvider.launcher
    val compiler = scalaCompilerBridgeBinaryJar match {
      case Some(jar) =>
        ZincUtil.scalaCompiler(
          scalaInstance = scalaInstance,
          classpathOptions = ClasspathOptionsUtil.repl,
          compilerBridgeJar = jar
        )
      case None =>
        ZincLmUtil.scalaCompiler(
          scalaInstance = scalaInstance,
          classpathOptions = ClasspathOptionsUtil.repl,
          globalLock = launcher.globalLock,
          componentProvider = app.provider.components,
          secondaryCacheDir = Option(zincDir),
          dependencyResolution = dependencyResolution,
          compilerBridgeSource = extracted.get(Keys.scalaCompilerBridgeSource),
          scalaJarsTarget = zincDir,
          log = log
        )
    }
    val imports = BuildUtil.getImports(unit.unit) ++ BuildUtil.importAll(bindings.map(_._1))
    val importString = imports.mkString("", ";\n", ";\n\n")
    val initCommands = importString + extra

    JLine.usingTerminal { _ =>
      // TODO - Hook up dsl classpath correctly...
      (new Console(compiler))(
        unit.classpath,
        options,
        initCommands,
        cleanupCommands
      )(Some(unit.loader), bindings).get
    }
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
