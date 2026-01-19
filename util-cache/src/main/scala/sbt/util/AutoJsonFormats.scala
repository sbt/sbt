/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sjsonnew.{ JsonFormat }
import xsbti.compile.{ CompileAnalysis, CompileResult, PreviousResult, Setup }
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFileRef }

/**
 * Automatic JsonFormat instances for common sbt types.
 *
 * This addresses issue #8288 by providing JsonFormats for types that
 * commonly cause compilation errors when used in cached tasks.
 */
object AutoJsonFormats {

  // Common sbt internal types that need JsonFormats
  given compileAnalysisFormat: JsonFormat[CompileAnalysis] =
    AutoJsonFormat.fallbackFormat[CompileAnalysis]("xsbti.compile.CompileAnalysis")

  given compileResultFormat: JsonFormat[CompileResult] =
    AutoJsonFormat.fallbackFormat[CompileResult]("xsbti.compile.CompileResult")

  given previousResultFormat: JsonFormat[PreviousResult] =
    AutoJsonFormat.fallbackFormat[PreviousResult]("xsbti.compile.PreviousResult")

  given setupFormat: JsonFormat[Setup] =
    AutoJsonFormat.fallbackFormat[Setup]("xsbti.compile.Setup")

  given fileConverterFormat: JsonFormat[FileConverter] =
    AutoJsonFormat.fallbackFormat[FileConverter]("xsbti.FileConverter")

  given hashedVirtualFileRefFormat: JsonFormat[HashedVirtualFileRef] =
    AutoJsonFormat.fallbackFormat[HashedVirtualFileRef]("xsbti.HashedVirtualFileRef")

  given virtualFileRefFormat: JsonFormat[VirtualFileRef] =
    AutoJsonFormat.fallbackFormat[VirtualFileRef]("xsbti.VirtualFileRef")

  // Import existing formats for convenience
  // Note: FileInfo formats are available through FileInfo companion objects

  /**
   * Helper method to get JsonFormat for any type with fallback
   */
  def format[T](using cls: Class[T]): JsonFormat[T] = AutoJsonFormat[T]
}
