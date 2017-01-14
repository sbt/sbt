package sbt

import sbt.internal.DslEntry
import sbt.util.Eval

object syntax extends syntax

abstract class syntax extends IOSyntax0 with sbt.std.TaskExtra with sbt.internal.util.Types with sbt.ProcessExtra
    with sbt.internal.librarymanagement.impl.DependencyBuilders with sbt.ProjectExtra
    with sbt.internal.librarymanagement.DependencyFilterExtra with sbt.BuildExtra with sbt.TaskMacroExtra
    with sbt.ScopeFilter.Make {

  // IO
  def uri(s: String): URI = new URI(s)
  def file(s: String): File = new File(s)
  def url(s: String): URL = new URL(s)
  implicit def fileToRichFile(file: File): sbt.io.RichFile = new sbt.io.RichFile(file)
  implicit def filesToFinder(cc: Traversable[File]): sbt.io.PathFinder = sbt.io.PathFinder.strict(cc)

  // others

  object CompileOrder {
    val JavaThenScala = xsbti.compile.CompileOrder.JavaThenScala
    val ScalaThenJava = xsbti.compile.CompileOrder.ScalaThenJava
    val Mixed = xsbti.compile.CompileOrder.Mixed
  }
  type CompileOrder = xsbti.compile.CompileOrder

  implicit def maybeToOption[S](m: xsbti.Maybe[S]): Option[S] =
    if (m.isDefined) Some(m.get) else None

  final val ThisScope = Scope.ThisScope
  final val GlobalScope = Scope.GlobalScope

  import sbt.{ Configurations => C }
  final val Compile = C.Compile
  final val Test = C.Test
  final val Runtime = C.Runtime
  final val IntegrationTest = C.IntegrationTest
  final val Default = C.Default
  final val Provided = C.Provided
  // java.lang.System is more important, so don't alias this one
  //  final val System = C.System
  final val Optional = C.Optional
  def config(s: String): Configuration = C.config(s)

  import language.experimental.macros
  def settingKey[T](description: String): SettingKey[T] = macro std.KeyMacro.settingKeyImpl[T]
  def taskKey[T](description: String): TaskKey[T] = macro std.KeyMacro.taskKeyImpl[T]
  def inputKey[T](description: String): InputKey[T] = macro std.KeyMacro.inputKeyImpl[T]

  def enablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslEnablePlugins(ps)
  def disablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslDisablePlugins(ps)
  def configs(cs: Configuration*): DslEntry = DslEntry.DslConfigs(cs)
  def dependsOn(deps: Eval[ClasspathDep[ProjectReference]]*): DslEntry = DslEntry.DslDependsOn(deps)
  // avoid conflict with `sbt.Keys.aggregate`
  def aggregateProjects(refs: Eval[ProjectReference]*): DslEntry = DslEntry.DslAggregate(refs)
}

// Todo share this this io.syntax
private[sbt] trait IOSyntax0 extends IOSyntax1 {
  implicit def alternative[A, B](f: A => Option[B]): Alternative[A, B] =
    new Alternative[A, B] {
      def |(g: A => Option[B]) =
        (a: A) => f(a) orElse g(a)
    }
}
private[sbt] trait Alternative[A, B] {
  def |(g: A => Option[B]): A => Option[B]
}

private[sbt] trait IOSyntax1 {
  implicit def singleFileFinder(file: File): sbt.io.PathFinder = sbt.io.PathFinder(file)
}
