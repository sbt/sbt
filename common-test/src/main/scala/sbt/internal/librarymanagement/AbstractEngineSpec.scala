package sbt.internal.librarymanagement

import sbt.librarymanagement._

abstract class AbstractEngineSpec extends UnitSpec {
  def cleanCache(): Unit

  def module(moduleId: ModuleID,
             deps: Vector[ModuleID],
             scalaFullVersion: Option[String]): ModuleDescriptor

  def updateEither(module: ModuleDescriptor): Either[UnresolvedWarning, UpdateReport]

  def update(module: ModuleDescriptor) =
    updateEither(module) match {
      case Right(r) => r
      case Left(w)  => throw w.resolveException
    }

  def cleanCachedResolutionCache(@deprecated("unused", "") module: ModuleDescriptor): Unit = ()
}
