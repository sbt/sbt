/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt.nio.FileStamp
import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }
import java.nio.file.{ Path as NioPath }
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.util.Base64

import sbt.librarymanagement.{ Configuration, ConfigurationMacro }
import scala.language.experimental.macros
import xsbti.compile.CompileAnalysis
import xsbti.compile.analysis.ReadWriteMappers
import sbt.internal.inc.{ Analysis, CompileOutput }
import sbt.internal.inc.consistent.{
  BinaryDeserializer,
  BinarySerializer,
  ConsistentAnalysisFormat
}

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
  def url(s: String): URI = new URI(s)
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
  given compileAnalysisJsonFormatter: JsonFormat[CompileAnalysis] =
    new JsonFormat[CompileAnalysis] {
      private val analysisFormat =
        new ConsistentAnalysisFormat(ReadWriteMappers.getEmptyMappers(), true)
      private val encoder = Base64.getEncoder
      private val decoder = Base64.getDecoder
      private val emptyMiniSetup = xsbti.compile.MiniSetup.create(
        CompileOutput.empty,
        xsbti.compile.MiniOptions.create(
          Array.empty[xsbti.compile.FileHash],
          Array.empty[String],
          Array.empty[String],
        ),
        "",
        xsbti.compile.CompileOrder.Mixed,
        true,
        Array.empty[xsbti.T2[String, String]],
      )

      override def write[J](obj: CompileAnalysis, builder: Builder[J]): Unit = {
        val bytes = obj match {
          case a: Analysis =>
            val baos = new ByteArrayOutputStream()
            val serializer = new BinarySerializer(baos)
            analysisFormat.write(serializer, a, emptyMiniSetup)
            serializer.end()
            baos.toByteArray
          case _ =>
            throw new UnsupportedOperationException(
              s"Cannot serialize ${obj.getClass.getName}. Expected sbt.internal.inc.Analysis"
            )
        }
        builder.writeString(encoder.encodeToString(bytes))
      }

      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): CompileAnalysis =
        jsOpt match {
          case Some(js) =>
            val bytes = decoder.decode(unbuilder.readString(js))
            val deserializer = new BinaryDeserializer(new ByteArrayInputStream(bytes))
            val (analysis, _) = analysisFormat.read(deserializer)
            deserializer.end()
            analysis
          case None =>
            deserializationError("Expected JsString but found None")
        }
    }
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
