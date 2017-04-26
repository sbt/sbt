/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import org.apache.ivy.core.module.descriptor
import org.apache.ivy.util.filter.{ Filter => IvyFilter }

abstract class InclExclRuleFunctions {
  def everything = InclExclRule("*", "*", "*", Vector.empty)
}

abstract class ArtifactTypeFilterExtra {
  def types: Set[String]
  def inverted: Boolean

  protected[this] def copy(
      types: Set[String] = types,
      inverted: Boolean = inverted
  ): ArtifactTypeFilter

  def invert = copy(inverted = !inverted)
  def apply(a: descriptor.Artifact): Boolean = (types contains a.getType) ^ inverted
}

abstract class ArtifactTypeFilterFunctions {
  def allow(types: Set[String]) = ArtifactTypeFilter(types, false)
  def forbid(types: Set[String]) = ArtifactTypeFilter(types, true)

  implicit def toIvyFilter(f: ArtifactTypeFilter): IvyFilter = new IvyFilter {
    override def accept(o: Object): Boolean = Option(o) exists {
      case a: descriptor.Artifact => f.apply(a)
    }
  }
}

abstract class ConflictManagerFunctions {
  // To avoid NPE (or making the val's below lazy)
  // For case classes refchecks rewrites apply calls to constructor calls, we have to do it manually
  def apply(name: String, organization: String = "*", module: String = "*"): ConflictManager
  def ConflictManager(name: String) = apply(name)

  val all = ConflictManager("all")
  val latestTime = ConflictManager("latest-time")
  val latestRevision = ConflictManager("latest-revision")
  val latestCompatible = ConflictManager("latest-compatible")
  val strict = ConflictManager("strict")
  val default = latestRevision
}
