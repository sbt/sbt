/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.{ File, IOException }
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths, StandardCopyOption }
import sbt.internal.util.{ ActionCacheEvent, CacheEventLog, StringVirtualFile1 }
import sbt.io.syntax.*
import sbt.io.IO
import sbt.nio.file.{ **, FileTreeView }
import sbt.nio.file.syntax.*
import sbt.util.CacheImplicits
import scala.reflect.ClassTag
import scala.annotation.{ meta, StaticAnnotation }
import scala.util.control.{ Exception, NonFatal }
import sjsonnew.{ HashWriter, JsonFormat }
import sjsonnew.support.murmurhash.Hasher
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }
import scala.quoted.{ Expr, FromExpr, ToExpr, Quotes }
import xsbti.{ CompileFailed, FileConverter, HashedVirtualFileRef, VirtualFile, VirtualFileRef }

object ActionCache:
  private[sbt] val dirZipExt = ".sbtdir.zip"
  private[sbt] val manifestFileName = "sbtdir_manifest.json"
  private[sbt] val failureFileName = "failure.json"
  private[sbt] val failureExitCode = 1

  /**
   * This is a key function that drives remote caching.
   * This is intended to be called from the cached task macro for the most part.
   *
   * - key: This represents the input key for this action, typically consists
   *   of all the input into the action. For the purpose of caching,
   *   all we need from the input is to generate some hash value.
   * - codeContentHash: This hash represents the Scala code of the task.
   *   Even if the input tasks are the same, the code part needs to be tracked.
   * - extraHash: Extra hash for cache invalidation (combined with config.cacheVersion).
   * - tags: Tags to track cache level.
   * - config: The configuration that's used to store where the cache backends are.
   *   config.cacheVersion is incorporated into the cache key to allow global invalidation.
   * - action: The actual action to be cached.
   */
  def cache[I: HashWriter, O: JsonFormat](
      key: I,
      codeContentHash: Digest,
      extraHash: Digest,
      tags: List[CacheLevelTag],
      config: BuildWideCacheConfiguration,
  )(
      action: I => InternalActionResult[O],
  ): O =
    import config.*

    def cacheFailure(e: CompileFailed): Nothing =
      // Cache the failure so subsequent builds don't re-run failed compilation
      // This fixes https://github.com/sbt/sbt/issues/7662
      // Use the same input digest as success, distinguished by exitCode
      cacheEventLog.append(ActionCacheEvent.OnsiteTask)
      val (input, valuePath) = mkInput(key, codeContentHash, extraHash, config.cacheVersion)
      val cachedFailure = CachedCompileFailure.fromException(e)
      val json = Converter.toJsonUnsafe(cachedFailure)
      val failureFile = StringVirtualFile1(valuePath, CompactPrinter(json))
      store.put(
        UpdateActionResultRequest(input, Vector(failureFile), exitCode = failureExitCode)
      )
      throw e

    def organicTask: O =
      // run action(...) and combine the newResult with outputs
      val InternalActionResult(result, outputs) =
        try action(key): @unchecked
        catch
          case e: CompileFailed =>
            cacheFailure(e)
          case e: Exception =>
            cacheEventLog.append(ActionCacheEvent.Error)
            throw e
      try
        val json = Converter.toJsonUnsafe(result)
        val normalizedOutputDir = outputDirectory.toAbsolutePath.normalize()
        val uncacheableOutputs =
          outputs.filter(f =>
            f match
              case vf if vf.id.endsWith(ActionCache.dirZipExt) =>
                false
              case _ =>
                val outputPath = fileConverter.toPath(f).toAbsolutePath.normalize()
                !outputPath.startsWith(normalizedOutputDir)
          )
        if uncacheableOutputs.nonEmpty then
          cacheEventLog.append(ActionCacheEvent.Error)
          logger.error(
            s"Cannot cache task because its output files are outside the output directory: \n" +
              uncacheableOutputs.mkString("  - ", "\n  - ", "")
          )
          result
        else
          cacheEventLog.append(ActionCacheEvent.OnsiteTask)
          val (input, valuePath) = mkInput(key, codeContentHash, extraHash, config.cacheVersion)
          val valueFile = StringVirtualFile1(valuePath, CompactPrinter(json))
          val newOutputs = Vector(valueFile) ++ outputs.toVector
          store.put(UpdateActionResultRequest(input, newOutputs, exitCode = 0)) match
            case Right(cachedResult) =>
              store.syncBlobs(cachedResult.outputFiles, outputDirectory)
              result
            case Left(e) => throw e
      catch
        case e: IOException =>
          logger.debug(s"Skipping cache storage due to error: ${e.getMessage}")
          cacheEventLog.append(ActionCacheEvent.Error)
          result

    // Single cache lookup - use exitCode to distinguish success from failure
    getWithFailure(key, codeContentHash, extraHash, tags, config) match
      case Right(value) => value
      case Left(Some(failure)) =>
        config.cacheEventLog.append(ActionCacheEvent.Found("cached-failure"))
        // Replay problems to the logger so users see the cached errors/warnings
        failure.replay(config.logger)
        throw failure.toException
      case Left(None) => organicTask
  end cache

  /**
   * Retrieves the cached value or failure with a single cache lookup.
   * Returns Right(value) for cached success, Left(Some(failure)) for cached failure,
   * or Left(None) for cache miss.
   */
  private def getWithFailure[I: HashWriter, O: JsonFormat](
      key: I,
      codeContentHash: Digest,
      extraHash: Digest,
      tags: List[CacheLevelTag],
      config: BuildWideCacheConfiguration,
  ): Either[Option[CachedCompileFailure], O] =
    import config.store
    def valueFromStr(str: String, origin: Option[String]): O =
      config.cacheEventLog.append(ActionCacheEvent.Found(origin.getOrElse("unknown")))
      val json = Parser.parseUnsafe(str)
      Converter.fromJsonUnsafe[O](json)

    def failureFromStr(str: String): CachedCompileFailure =
      val json = Parser.parseUnsafe(str)
      Converter.fromJsonUnsafe[CachedCompileFailure](json)

    def parseCachedValue(
        str: String,
        origin: Option[String],
        isFailure: Boolean,
    ): Option[Either[Option[CachedCompileFailure], O]] =
      try
        if isFailure then Some(Left(Some(failureFromStr(str))))
        else Some(Right(valueFromStr(str, origin)))
      catch case _: Exception => None

    // Optimization: Check if we can read directly from symlinked value file
    val (input, valuePath) = mkInput(key, codeContentHash, extraHash, config.cacheVersion)
    val resolvedValuePath = config.fileConverter.toPath(VirtualFileRef.of(valuePath))

    def readFromSymlink(): Option[Either[Option[CachedCompileFailure], O]] =
      if java.nio.file.Files.isSymbolicLink(resolvedValuePath) && java.nio.file.Files
          .exists(resolvedValuePath)
      then
        Exception.nonFatalCatch
          .opt(IO.read(resolvedValuePath.toFile(), StandardCharsets.UTF_8))
          .flatMap: str =>
            findActionResult(key, codeContentHash, extraHash, config) match
              case Right(result) =>
                try
                  store.syncBlobs(result.outputFiles, config.outputDirectory)
                  parseCachedValue(str, Some("disk"), result.exitCode.contains(failureExitCode))
                catch case NonFatal(_) => None
              case Left(_) => None
      else None

    readFromSymlink() match
      case Some(result) => result
      case None =>
        findActionResult(key, codeContentHash, extraHash, config) match
          case Right(result) =>
            try
              val isFailure = result.exitCode.contains(failureExitCode)
              result.contents.headOption match
                case Some(head) =>
                  store.syncBlobs(result.outputFiles, config.outputDirectory)
                  val str = String(head.array(), StandardCharsets.UTF_8)
                  parseCachedValue(str, result.origin, isFailure).getOrElse(Left(None))
                case _ =>
                  val paths = store.syncBlobs(result.outputFiles, config.outputDirectory)
                  if paths.isEmpty then Left(None)
                  else
                    val str = IO.read(paths.head.toFile())
                    parseCachedValue(str, result.origin, isFailure).getOrElse(Left(None))
            catch
              case NonFatal(e) =>
                config.logger.debug(
                  s"Ignoring cache retrieval failure, will recompute: ${e.getMessage}"
                )
                Left(None)
          case Left(_) => Left(None)

  /**
   * Retrieves the cached value.
   */
  def get[I: HashWriter, O: JsonFormat](
      key: I,
      codeContentHash: Digest,
      extraHash: Digest,
      tags: List[CacheLevelTag],
      config: BuildWideCacheConfiguration,
  ): Option[O] =
    getWithFailure(key, codeContentHash, extraHash, tags, config) match
      case Right(value) => Some(value)
      case Left(_)      => None

  /**
   * Checks if the ActionResult exists in the cache.
   */
  def exists[I: HashWriter](
      key: I,
      codeContentHash: Digest,
      extraHash: Digest,
      config: BuildWideCacheConfiguration,
  ): Boolean =
    findActionResult(key, codeContentHash, extraHash, config) match
      case Right(_) => true
      case Left(_)  => false

  inline private[sbt] def findActionResult[I: HashWriter](
      key: I,
      codeContentHash: Digest,
      extraHash: Digest,
      config: BuildWideCacheConfiguration,
  ): Either[Throwable, ActionResult] =
    // val logger = config.logger
    CacheImplicits.setCacheSize(config.localDigestCacheByteSize)
    val (input, valuePath) = mkInput(key, codeContentHash, extraHash, config.cacheVersion)
    val getRequest =
      GetActionResultRequest(input, inlineStdout = false, inlineStderr = false, Vector(valuePath))
    config.store.get(getRequest)

  private inline def mkInput[I: HashWriter](
      key: I,
      codeContentHash: Digest,
      extraHash: Digest,
      cacheVersion: Long,
  ): (Digest, String) =
    val effectiveExtraHash =
      if cacheVersion != 0L then Digest.sha256Hash(extraHash, Digest.dummy(cacheVersion))
      else extraHash
    val input =
      Digest.sha256Hash(
        codeContentHash,
        effectiveExtraHash,
        Digest.dummy(Hasher.hashUnsafe[I](key))
      )
    (input, s"$${OUT}/value/$input.json")

  def manifestFromFile(manifest: Path): Manifest =
    import sbt.internal.util.codec.ManifestCodec.given
    val json = Parser.parseFromFile(manifest.toFile()).get
    Converter.fromJsonUnsafe[Manifest](json)

  private val default2010Timestamp: Long = 1262304000000L

  def packageDirectory(
      dir: VirtualFileRef,
      conv: FileConverter,
      outputDirectory: Path,
  ): VirtualFile =
    import sbt.internal.util.codec.ManifestCodec.given
    val dirPath = conv.toPath(dir)
    val allPaths = FileTreeView.default
      .list(dirPath.toGlob / ** / "*")
      .withFilter(!_._2.isDirectory)
      .map(_._1)
      .sortBy(_.toString())
    // create a manifest of files and their hashes here
    def makeManifest(manifestFile: Path): Unit =
      val vfs = (allPaths
        .map: p =>
          (conv.toVirtualFile(p): HashedVirtualFileRef))
        .toVector
      val manifest = Manifest(
        version = "0.1.0",
        outputFiles = vfs,
      )
      val str = CompactPrinter(Converter.toJsonUnsafe(manifest))
      IO.write(manifestFile.toFile(), str)
    IO.withTemporaryDirectory: tempDir =>
      val mPath = (tempDir / manifestFileName).toPath()
      makeManifest(mPath)
      val zipPath = Paths.get(dirPath.toString + dirZipExt)
      val rebase: Path => Seq[(File, String)] =
        (p: Path) =>
          p match
            case p if p == dirPath => Nil
            case p if p == mPath   => (mPath.toFile() -> manifestFileName) :: Nil
            case f                 => (f.toFile() -> outputDirectory.relativize(f).toString) :: Nil
      // Create the zip in a temp directory to avoid overwriting the cache if `zipPath` is a symlink to the CAS
      val tempZipPath = (tempDir / (dirPath.getFileName.toString + dirZipExt)).toPath()
      IO.zip(
        (allPaths ++ Seq(mPath)).flatMap(rebase),
        tempZipPath.toFile(),
        Some(default2010Timestamp)
      )
      Files.copy(tempZipPath, zipPath, StandardCopyOption.REPLACE_EXISTING)

      conv.toVirtualFile(zipPath)

  inline def actionResult[A1](inline value: A1): InternalActionResult[A1] =
    InternalActionResult(value, Nil)

  /**
   * Represents a value and output files, used internally by the macro.
   */
  class InternalActionResult[A1] private (
      val value: A1,
      val outputs: Seq[VirtualFile],
  )
  end InternalActionResult
  object InternalActionResult:
    def apply[A1](value: A1, outputs: Seq[VirtualFile]): InternalActionResult[A1] =
      new InternalActionResult(value, outputs)
    private[sbt] def unapply[A1](r: InternalActionResult[A1]): Option[(A1, Seq[VirtualFile])] =
      Some(r.value, r.outputs)
  end InternalActionResult
end ActionCache

class BuildWideCacheConfiguration(
    val store: ActionCacheStore,
    val outputDirectory: Path,
    val fileConverter: FileConverter,
    val logger: Logger,
    val cacheEventLog: CacheEventLog,
    val localDigestCacheByteSize: Long,
    val cacheVersion: Long,
):
  def this(
      store: ActionCacheStore,
      outputDirectory: Path,
      fileConverter: FileConverter,
      logger: Logger,
      cacheEventLog: CacheEventLog,
  ) =
    this(
      store,
      outputDirectory,
      fileConverter,
      logger,
      cacheEventLog,
      CacheImplicits.defaultLocalDigestCacheByteSize,
      0L
    )

  def this(
      store: ActionCacheStore,
      outputDirectory: Path,
      fileConverter: FileConverter,
      logger: Logger,
      cacheEventLog: CacheEventLog,
      localDigestCacheByteSize: Long,
  ) =
    this(store, outputDirectory, fileConverter, logger, cacheEventLog, localDigestCacheByteSize, 0L)

  override def toString(): String =
    s"BuildWideCacheConfiguration(store = $store, outputDirectory = $outputDirectory)"
end BuildWideCacheConfiguration

object Uncached:
  /**
   * Marker function to make the task uncached.
   */
  def apply[A1](a: A1): A1 = a

@meta.getter
class cacheLevel(
    include: Array[CacheLevelTag],
) extends StaticAnnotation

enum CacheLevelTag:
  case Local
  case Remote
end CacheLevelTag

object CacheLevelTag:
  private[sbt] val all: Array[CacheLevelTag] = Array(CacheLevelTag.Local, CacheLevelTag.Remote)

  given CacheLevelTagToExpr: ToExpr[CacheLevelTag] with
    def apply(tag: CacheLevelTag)(using Quotes): Expr[CacheLevelTag] =
      tag match
        case CacheLevelTag.Local  => '{ CacheLevelTag.Local }
        case CacheLevelTag.Remote => '{ CacheLevelTag.Remote }

  given CacheLevelTagFromExpr: FromExpr[CacheLevelTag] with
    def unapply(expr: Expr[CacheLevelTag])(using Quotes): Option[CacheLevelTag] =
      expr match
        case '{ CacheLevelTag.Local }  => Some(CacheLevelTag.Local)
        case '{ CacheLevelTag.Remote } => Some(CacheLevelTag.Remote)
        case _                         => None
end CacheLevelTag
