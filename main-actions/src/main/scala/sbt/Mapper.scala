package sbt

import sbt.io.{ AllPassFilter, FileFilter, PathFinder }
import sbt.io.Path.*
import xsbti.{ FileConverter, VirtualFile }

import java.io.File

object Mapper:

  /**
   * Selects all descendants of `base` directory and maps them to a path relative to `base`.
   * `base` itself is not included.
   */
  def allSubpaths(base: File)(using conv: FileConverter): Seq[(VirtualFile, String)] =
    selectSubpaths(base, AllPassFilter)

  /**
   * Selects descendants of `base` directory matching `filter` and maps them to a path relative to `base`.
   * `base` itself is not included.
   */
  def selectSubpaths(base: File, filter: FileFilter)(using
      conv: FileConverter
  ): Seq[(VirtualFile, String)] =
    PathFinder(base).globRecursive(filter).get().collect {
      case f if f != base =>
        conv.toVirtualFile(f.toPath) -> base.toPath.relativize(f.toPath).toString
    }

  /**
   * return a Seq of mappings which effect is to add a whole directory in the generated package
   *
   * @example In order to create mappings for a static directory "extra" add
   * {{{
   * mappings ++= directory(baseDirectory.value / "extra")
   * }}}
   *
   * The resulting mappings sequence will look something like this
   *
   * {{{
   * File(baseDirectory/extras) -> "extras"
   * File(baseDirectory/extras/file1) -> "extras/file1"
   * File(baseDirectory/extras/file2) -> "extras/file2"
   * ...
   * }}}
   *
   * @param baseDirectory The directory that should be turned into a mappings sequence.
   * @return mappings The `baseDirectory` and all of its contents
   */
  def directory(baseDirectory: File)(using conv: FileConverter): Seq[(VirtualFile, String)] =
    Option(baseDirectory.getParentFile)
      .map(parent => PathFinder(baseDirectory).allPaths.pair(relativeTo(parent)))
      .getOrElse(PathFinder(baseDirectory).allPaths.pair(basic))
      .map { (f, s) => conv.toVirtualFile(f.toPath) -> s }

  /**
   * return a Seq of mappings  excluding the directory itself.
   *
   * @example In order to create mappings for a static directory "extra" add
   * {{{
   * mappings ++= contentOf(baseDirectory.value / "extra")
   * }}}
   *
   * The resulting mappings sequence will look something like this
   *
   * {{{
   * File(baseDirectory/extras/file1) -> "file1"
   * File(baseDirectory/extras/file2) -> "file2"
   * ...
   * }}}
   *
   * @example Add a static directory "extra" and re-map the destination to a different path
   * {{{
   * mappings ++= contentOf(baseDirectory.value / "extra").map {
   *   case (src, destination) => src -> s"new/path/destination"
   * }
   * }}}
   *
   * @param baseDirectory The directory that should be turned into a mappings sequence.
   * @return mappings - The `basicDirectory`'s contents excluding `basicDirectory` itself
   */
  def contentOf(baseDirectory: File)(using conv: FileConverter): Seq[(VirtualFile, String)] =
    (PathFinder(baseDirectory).allPaths --- PathFinder(baseDirectory))
      .pair(relativeTo(baseDirectory))
      .map { (f, s) => conv.toVirtualFile(f.toPath) -> s }
end Mapper
