package sbt
package internal
package inc

import xsbti.compile.IncOptions
import java.io.File
import xsbti.api.Source

private final class IncrementalAntStyle(log: sbt.util.Logger, options: IncOptions) extends IncrementalCommon(log, options) {

  /** Ant-style mode doesn't do anything special with package objects */
  override protected def invalidatedPackageObjects(invalidated: Set[File], relations: Relations, apis: APIs): Set[File] = Set.empty

  /** In Ant-style mode we don't need to compare APIs because we don't perform any invalidation */
  override protected def sameAPI[T](src: T, a: Source, b: Source): Option[APIChange[T]] = None

  /** In Ant-style mode we don't perform any invalidation */
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange[String]): Set[File] = Set.empty

  /** In Ant-style mode we don't perform any invalidation */
  override protected def invalidateSource(relations: Relations, change: APIChange[File]): Set[File] = Set.empty

  /** In Ant-style mode we don't need to perform any dependency analysis hence we can always return an empty set. */
  override protected def allDeps(relations: Relations): File => Set[File] = _ => Set.empty

}
