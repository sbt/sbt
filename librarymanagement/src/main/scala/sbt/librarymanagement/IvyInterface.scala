/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import org.apache.ivy.core.module.descriptor
import org.apache.ivy.util.filter.{ Filter => IvyFilter }
import sbt.internal.librarymanagement.impl.{ GroupArtifactID, GroupID }

abstract class InclExclRuleFunctions {
  def everything = InclExclRule("*", "*", "*", Vector.empty, Disabled())

  def apply(organization: String, name: String): InclExclRule =
    InclExclRule(organization, name, "*", Vector.empty, Disabled())

  def apply(organization: String): InclExclRule = apply(organization, "*")

  implicit def groupIdToExclusionRule(organization: GroupID): InclExclRule =
    apply(organization.groupID)
  implicit def stringToExclusionRule(organization: String): InclExclRule = apply(organization)

  implicit def groupArtifactIDToExclusionRule(gaid: GroupArtifactID): InclExclRule =
    InclExclRule(gaid.groupID, gaid.artifactID, "*", Vector.empty, gaid.crossVersion)

  implicit def moduleIDToExclusionRule(moduleID: ModuleID): InclExclRule = {
    val org = moduleID.organization
    val name = moduleID.name
    val version = moduleID.revision
    val crossVersion = moduleID.crossVersion
    InclExclRule(org, name, version, Vector.empty, crossVersion)
  }
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
