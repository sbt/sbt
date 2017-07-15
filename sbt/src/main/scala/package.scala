import scala.language.experimental.macros

/* sbt -- Simple Build Tool
 * Copyright 2010, 2011 Mark Harrah
 */
package object sbt
    extends sbt.IOSyntax0
    with sbt.std.TaskExtra
    with sbt.internal.util.Types
    with sbt.ProjectExtra
    with sbt.librarymanagement.DependencyBuilders
    with sbt.librarymanagement.DependencyFilterExtra
    with sbt.librarymanagement.LibraryManagementSyntax
    with sbt.BuildExtra
    with sbt.TaskMacroExtra
    with sbt.ScopeFilter.Make
    with sbt.BuildSyntax
    with sbt.OptionSyntax
    with sbt.Import {
  // IO
  def uri(s: String): URI = new URI(s)
  def file(s: String): File = new File(s)
  def url(s: String): URL = new URL(s)
  implicit def fileToRichFile(file: File): sbt.io.RichFile = new sbt.io.RichFile(file)
  implicit def filesToFinder(cc: Traversable[File]): sbt.io.PathFinder =
    sbt.io.PathFinder.strict(cc)

  // others

  object CompileOrder {
    val JavaThenScala = xsbti.compile.CompileOrder.JavaThenScala
    val ScalaThenJava = xsbti.compile.CompileOrder.ScalaThenJava
    val Mixed = xsbti.compile.CompileOrder.Mixed
  }
  type CompileOrder = xsbti.compile.CompileOrder

  final val ThisScope = Scope.ThisScope
  final val Global = Scope.Global
  final val GlobalScope = Scope.GlobalScope

  // import sbt.{ Configurations => C }
  // final val Compile = C.Compile
  // final val Test = C.Test
  // final val Runtime = C.Runtime
  // final val IntegrationTest = C.IntegrationTest
  // final val Default = C.Default
  // final val Provided = C.Provided
  // java.lang.System is more important, so don't alias this one
  //  final val System = C.System
  // final val Optional = C.Optional
  def config(name: String): Configuration =
    macro sbt.librarymanagement.ConfigurationMacro.configMacroImpl
}
