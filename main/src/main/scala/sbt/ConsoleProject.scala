/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

import sbt.util.Logger
import java.io.File
import sbt.librarymanagement.Resolver
import sbt.internal.librarymanagement.{ InlineIvyConfiguration, IvyPaths }
import sbt.internal.inc.{ AnalyzingCompiler, ClasspathOptions, IncrementalCompilerImpl }

object ConsoleProject {
  def apply(state: State, extra: String, cleanupCommands: String = "", options: Seq[String] = Nil)(implicit log: Logger): Unit = {
    val extracted = Project extract state
    val cpImports = new Imports(extracted, state)
    val bindings = ("currentState" -> state) :: ("extracted" -> extracted) :: ("cpHelpers" -> cpImports) :: Nil
    val unit = extracted.currentUnit
    val localOnly = false
    val lock = None
    val checksums = Nil
    val ivyPaths = new IvyPaths(unit.unit.localBase, bootIvyHome(state.configuration))
    val ivyConfiguration = new InlineIvyConfiguration(ivyPaths, Resolver.withDefaultResolvers(Nil),
      Nil, Nil, localOnly, lock, checksums, None, log)
    val compiler: AnalyzingCompiler = Compiler.compilers(ClasspathOptions.repl, ivyConfiguration)(state.configuration, log) match { case IncrementalCompilerImpl.Compilers(scalac, _) => scalac }
    val imports = BuildUtil.getImports(unit.unit) ++ BuildUtil.importAll(bindings.map(_._1))
    val importString = imports.mkString("", ";\n", ";\n\n")
    val initCommands = importString + extra
    // TODO - Hook up dsl classpath correctly...
    (new Console(compiler))(
      unit.classpath, options, initCommands, cleanupCommands
    )(Some(unit.loader), bindings)
  }
  /** Conveniences for consoleProject that shouldn't normally be used for builds. */
  final class Imports private[sbt] (extracted: Extracted, state: State) {
    import extracted._
    implicit def taskKeyEvaluate[T](t: TaskKey[T]): Evaluate[T] = new Evaluate(runTask(t, state)._2)
    implicit def settingKeyEvaluate[T](s: SettingKey[T]): Evaluate[T] = new Evaluate(get(s))
  }
  final class Evaluate[T] private[sbt] (val eval: T)
  private def bootIvyHome(app: xsbti.AppConfiguration): Option[File] =
    try { Option(app.provider.scalaProvider.launcher.ivyHome) }
    catch { case _: NoSuchMethodError => None }
}
