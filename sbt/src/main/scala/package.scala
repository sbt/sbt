/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt.nio.FileStamp
import sjsonnew.JsonFormat
import java.nio.file.{ Path => NioPath }

import scala.language.experimental.macros

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
    with sbt.SlashSyntax
    with sbt.Import {
  // IO
  def uri(s: String): URI = new URI(s)
  def file(s: String): File = new File(s)
  def url(s: String): URL = new URL(s)
  implicit def fileToRichFile(file: File): sbt.io.RichFile = new sbt.io.RichFile(file)
  implicit def filesToFinder(cc: Traversable[File]): sbt.io.PathFinder =
    sbt.io.PathFinder.strict(cc)
  implicit val fileStampJsonFormatter: JsonFormat[Seq[(NioPath, FileStamp)]] =
    FileStamp.Formats.fileStampJsonFormatter
  implicit val pathJsonFormatter: JsonFormat[Seq[NioPath]] = FileStamp.Formats.pathJsonFormatter
  implicit val fileJsonFormatter: JsonFormat[Seq[File]] = FileStamp.Formats.fileJsonFormatter
  implicit val singlePathJsonFormatter: JsonFormat[NioPath] = FileStamp.Formats.pathJson
  implicit val singleFileJsonFormatter: JsonFormat[File] = FileStamp.Formats.fileJson
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

  def config(name: String): Configuration =
    macro sbt.librarymanagement.ConfigurationMacro.configMacroImpl
}
