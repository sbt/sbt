/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ Path, Paths }
import sbt.internal.inc.{ MappedFileConverter, MappedVirtualFile, MixedAnalyzingCompiler }
import xsbti.{ FileConverter, VirtualFileRef }
import xsbti.compile.{ AnalysisStore as XAnalysisStore, MiniSetup }
import xsbti.compile.analysis.{ GenericMapper, ReadMapper, ReadWriteMappers, Stamp, WriteMapper }

private[sbt] object AnalysisUtil:
  // some machines have many cores.
  // we don't want to occupy them all for analysis serialization.
  lazy val parallelism: Int =
    scala.math.min(
      Runtime.getRuntime.availableProcessors(),
      8,
    )
  def staticCachedStore(
      analysisFile: Path,
      useTextAnalysis: Boolean,
      useConsistent: Boolean,
  ): XAnalysisStore =
    MixedAnalyzingCompiler.staticCachedStore(
      analysisFile = analysisFile,
      useTextAnalysis = useTextAnalysis,
      useConsistent = useConsistent,
      mappers = ReadWriteMappers.getEmptyMappers(),
      reproducible = true,
      parallelism = parallelism,
    )

  /**
   * Persists machine paths in the analysis as `$${ROOT}`-prefixed ids, so that the serialized
   * bytes do not depend on the machine that produced them.
   */
  def virtualizedMappers(converter: FileConverter): ReadWriteMappers =
    converter match
      case mapped: MappedFileConverter =>
        val rootPaths = mapped.rootPaths
        ReadWriteMappers(VirtualizedReadMapper(rootPaths), VirtualizedWriteMapper(rootPaths))
      case _ => ReadWriteMappers.getEmptyMappers()

  private trait IdentityMapper extends GenericMapper:
    override def mapSourceFile(sourceFile: VirtualFileRef): VirtualFileRef = sourceFile
    override def mapBinaryFile(binaryFile: VirtualFileRef): VirtualFileRef = binaryFile
    override def mapProductFile(productFile: VirtualFileRef): VirtualFileRef = productFile
    override def mapOutputDir(outputDir: Path): Path = outputDir
    override def mapSourceDir(sourceDir: Path): Path = sourceDir
    override def mapJavacOption(javacOption: String): String = javacOption
    override def mapScalacOption(scalacOption: String): String = scalacOption
    override def mapBinaryStamp(file: VirtualFileRef, stamp: Stamp): Stamp = stamp
    override def mapSourceStamp(file: VirtualFileRef, stamp: Stamp): Stamp = stamp
    override def mapProductStamp(file: VirtualFileRef, stamp: Stamp): Stamp = stamp
    override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup

  private final class VirtualizedWriteMapper(rootPaths: Map[String, Path])
      extends IdentityMapper,
        WriteMapper:
    override def mapClasspathEntry(classpathEntry: Path): Path =
      rootPaths.toSeq
        .collectFirst:
          case (key, rootPath) if classpathEntry.startsWith(rootPath) =>
            Paths.get(s"$${$key}/${rootPath.relativize(classpathEntry)}".replace('\\', '/'))
        .getOrElse(classpathEntry)

  private final class VirtualizedReadMapper(rootPaths: Map[String, Path])
      extends IdentityMapper,
        ReadMapper:
    override def mapClasspathEntry(classpathEntry: Path): Path =
      MappedVirtualFile.toPath(s"$classpathEntry".replace('\\', '/'), rootPaths)
end AnalysisUtil
