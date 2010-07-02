/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbt.api.NameChanges
import java.io.File

final case class InitialChanges(internalSrc: Changes[File], removedProducts: Set[File], binaryDeps: Set[File], external: APIChanges[String])
final class APIChanges[T](val modified: Set[T], val names: NameChanges)
{
	override def toString = "API Changes: " + modified + "\n" + names
}

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