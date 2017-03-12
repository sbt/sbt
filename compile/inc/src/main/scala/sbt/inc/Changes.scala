/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbt.api.NameChanges
import java.io.File
import xsbti.api.{ _internalOnly_NameHashes => NameHashes }
import xsbti.api.{ _internalOnly_NameHash => NameHash }

final case class InitialChanges(internalSrc: Changes[File],
                                removedProducts: Set[File],
                                binaryDeps: Set[File],
                                external: APIChanges[String])
final class APIChanges[T](val apiChanges: Iterable[APIChange[T]]) {
  override def toString = "API Changes: " + apiChanges
  def allModified: Iterable[T] = apiChanges.map(_.modified)
}

sealed abstract class APIChange[T](val modified: T)

/**
  * If we recompile a source file that contains a macro definition then we always assume that it's
  * api has changed. The reason is that there's no way to determine if changes to macros implementation
  * are affecting its users or not. Therefore we err on the side of caution.
  */
final case class APIChangeDueToMacroDefinition[T](modified0: T) extends APIChange(modified0)
final case class SourceAPIChange[T](modified0: T) extends APIChange(modified0)

/**
  * An APIChange that carries information about modified names.
  *
  * This class is used only when name hashing algorithm is enabled.
  */
final case class NamesChange[T](modified0: T, modifiedNames: ModifiedNames) extends APIChange(modified0)

/**
  * ModifiedNames are determined by comparing name hashes in two versions of an API representation.
  *
  * Note that we distinguish between sets of regular (non-implicit) and implicit modified names.
  * This distinction is needed because the name hashing algorithm makes different decisions based
  * on whether modified name is implicit or not. Implicit names are much more difficult to handle
  * due to difficulty of reasoning about the implicit scope.
  */
final case class ModifiedNames(regularNames: Set[String], implicitNames: Set[String]) {
  override def toString: String =
    s"ModifiedNames(regularNames = ${regularNames mkString ", "}, implicitNames = ${implicitNames mkString ", "})"
}
object ModifiedNames {
  def compareTwoNameHashes(a: NameHashes, b: NameHashes): ModifiedNames = {
    val modifiedRegularNames = calculateModifiedNames(a.regularMembers.toSet, b.regularMembers.toSet)
    val modifiedImplicitNames = calculateModifiedNames(a.implicitMembers.toSet, b.implicitMembers.toSet)
    ModifiedNames(modifiedRegularNames, modifiedImplicitNames)
  }
  private def calculateModifiedNames(xs: Set[NameHash], ys: Set[NameHash]): Set[String] = {
    val differentNameHashes = (xs union ys) diff (xs intersect ys)
    differentNameHashes.map(_.name)
  }
}

trait Changes[A] {
  def added: Set[A]
  def removed: Set[A]
  def changed: Set[A]
  def unmodified: Set[A]
}

sealed abstract class Change(val file: File)
final class Removed(f: File) extends Change(f)
final class Added(f: File, newStamp: Stamp) extends Change(f)
final class Modified(f: File, oldStamp: Stamp, newStamp: Stamp) extends Change(f)
