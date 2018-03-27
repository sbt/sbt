/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import sbt.librarymanagement.DependencyBuilders.{ Organization, OrganizationArtifactName }

private[librarymanagement] abstract class InclExclRuleFunctions {
  def everything = InclExclRule("*", "*", "*", Vector.empty, Disabled())

  def apply(organization: String, name: String): InclExclRule =
    InclExclRule(organization, name, "*", Vector.empty, Disabled())

  def apply(organization: String): InclExclRule = apply(organization, "*")

  implicit def organizationToExclusionRule(organization: Organization): InclExclRule =
    apply(organization.organization)
  implicit def stringToExclusionRule(organization: String): InclExclRule = apply(organization)

  implicit def organizationArtifactNameToExclusionRule(oa: OrganizationArtifactName): InclExclRule =
    InclExclRule(oa.organization, oa.name, "*", Vector.empty, oa.crossVersion)

  implicit def moduleIDToExclusionRule(moduleID: ModuleID): InclExclRule = {
    val org = moduleID.organization
    val name = moduleID.name
    val version = moduleID.revision
    val crossVersion = moduleID.crossVersion
    InclExclRule(org, name, version, Vector.empty, crossVersion)
  }
}

private[librarymanagement] abstract class ArtifactTypeFilterExtra {
  def inverted: Boolean

  def withInverted(inverted: Boolean): ArtifactTypeFilter

  def invert = withInverted(!inverted)
}

private[librarymanagement] abstract class ArtifactTypeFilterFunctions {
  def allow(types: Set[String]) = ArtifactTypeFilter(types, false)
  def forbid(types: Set[String]) = ArtifactTypeFilter(types, true)
}

private[librarymanagement] abstract class ConflictManagerFunctions {
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
