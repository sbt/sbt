/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbt.api.NameChanges
import java.io.File

final case class InitialChanges(internalSrc: Changes[File], removedProducts: Set[File], binaryDeps: Set[File], external: APIChanges[String])
final class APIChanges[T](val apiChanges: Iterable[APIChange[T]])
{
	override def toString = "API Changes: " + apiChanges
	def allModified: Iterable[T] = apiChanges.map(_.modified)
}

sealed abstract class APIChange[T](val modified: T)
/**
 * If we recompile a source file that contains a macro definition then we always assume that it's
 * api has changed. The reason is that there's no way to determine if changes to macros implementation
 * are affecting its users or not. Therefore we err on the side of caution.
 */
case class APIChangeDueToMacroDefinition[T](modified0: T) extends APIChange(modified0)
case class SourceAPIChange[T](modified0: T) extends APIChange(modified0)

trait Changes[A]
{
	def added: Set[A]
	def removed: Set[A]
	def changed: Set[A]
	def unmodified: Set[A]
}

sealed abstract class Change(val file: File)
final class Removed(f: File) extends Change(f)
final class Added(f: File, newStamp: Stamp) extends Change(f)
final class Modified(f: File, oldStamp: Stamp, newStamp: Stamp) extends Change(f)
