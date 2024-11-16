/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt.nio.FileStamp
import sjsonnew.JsonFormat
import java.nio.file.{ Path => NioPath }

import sbt.librarymanagement.{ Configuration, ConfigurationMacro }
import scala.language.experimental.macros

package object sbt
    extends sbt.IOSyntax0
    with sbt.std.TaskExtra
    // with sbt.internal.util.Types
    with sbt.ProjectExtra
    with sbt.ProjectMatrixExtra
    with sbt.librarymanagement.DependencyBuilders
    with sbt.librarymanagement.DependencyFilterExtra
    with sbt.librarymanagement.LibraryManagementSyntax
    with sbt.BuildExtra
    with sbt.BuildSyntax
    with sbt.ScopeFilter.Make
    with sbt.OptionSyntax
    with sbt.SlashSyntax
    with sbt.Import:
  export Project.{
    validProjectID,
    fillTaskAxis,
    mapScope,
    transform,
    inThisBuild,
    inScope,
    normalizeModuleID
  }
  // IO
  def uri(s: String): URI = new URI(s)
  def file(s: String): File = new File(s)
  def url(s: String): URL = new URI(s).toURL
  implicit def fileToRichFile(file: File): sbt.io.RichFile = new sbt.io.RichFile(file)
  implicit def filesToFinder(cc: Iterable[File]): sbt.io.PathFinder =
    sbt.io.PathFinder.strict(cc)
  /*
   * Provides macro extension methods. Because the extension methods are all macros, no instance
   * of FileChangesMacro.TaskOps is ever made which is why it is ok to use `???`.
   */
  // implicit def taskToTaskOpts[T](t: TaskKey[T]): FileChangesMacro.TaskOps[T] = ???
  given fileStampJsonFormatter: JsonFormat[Seq[(NioPath, FileStamp)]] =
    FileStamp.Formats.seqPathFileStampJsonFormatter
  given pathJsonFormatter: JsonFormat[Seq[NioPath]] = FileStamp.Formats.seqPathJsonFormatter
  given fileJsonFormatter: JsonFormat[Seq[File]] = FileStamp.Formats.seqFileJsonFormatter
  given singlePathJsonFormatter: JsonFormat[NioPath] = FileStamp.Formats.pathJsonFormatter
  given singleFileJsonFormatter: JsonFormat[File] = FileStamp.Formats.fileJsonFormatter
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
  val `Package` = Pkg

  inline def config(name: String): Configuration = ${
    ConfigurationMacro.configMacroImpl('{ name })
  }
end sbt
