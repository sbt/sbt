/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt
package internal

import sbt.util.Logger
import sbt.internal.inc.{ ScalaInstance, ZincUtil }
import xsbti.compile.ClasspathOptionsUtil

object ConsoleProject {
  def apply(state: State, extra: String, cleanupCommands: String = "", options: Seq[String] = Nil)(
      implicit log: Logger): Unit = {
    val extracted = Project extract state
    val cpImports = new Imports(extracted, state)
    val bindings = ("currentState" -> state) :: ("extracted" -> extracted) :: ("cpHelpers" -> cpImports) :: Nil
    val unit = extracted.currentUnit
    val (_, ivyConf) = extracted.runTask(Keys.ivyConfiguration, state)
    val scalaInstance = {
      val scalaProvider = state.configuration.provider.scalaProvider
      ScalaInstance(scalaProvider.version, scalaProvider.launcher)
    }
    val g = BuildPaths.getGlobalBase(state)
    val zincDir = BuildPaths.getZincDirectory(state, g)
    val app = state.configuration
    val launcher = app.provider.scalaProvider.launcher
    val compiler = ZincUtil.scalaCompiler(
      scalaInstance = scalaInstance,
      classpathOptions = ClasspathOptionsUtil.repl,
      globalLock = launcher.globalLock,
      componentProvider = app.provider.components,
      secondaryCacheDir = Option(zincDir),
      ivyConfiguration = ivyConf,
      compilerBridgeSource = extracted.get(Keys.scalaCompilerBridgeSource),
      scalaJarsTarget = zincDir,
      log = log
    )
    val imports = BuildUtil.getImports(unit.unit) ++ BuildUtil.importAll(bindings.map(_._1))
    val importString = imports.mkString("", ";\n", ";\n\n")
    val initCommands = importString + extra
    // TODO - Hook up dsl classpath correctly...
    (new Console(compiler))(
      unit.classpath,
      options,
      initCommands,
      cleanupCommands
    )(Some(unit.loader), bindings)
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
