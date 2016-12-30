package sbt.internal.librarymanagement

import sbt.internal.librarymanagement.impl._
import sbt.librarymanagement._

abstract class SbtExclusionRuleFunctions {
  def apply(organization: String, name: String): SbtExclusionRule =
    SbtExclusionRule(organization, name, "*", Vector.empty, Disabled())

  def apply(organization: String): SbtExclusionRule = apply(organization, "*")

  implicit def groupIdToExclusionRule(organization: GroupID): SbtExclusionRule = apply(organization.groupID)
  implicit def stringToExclusionRule(organization: String): SbtExclusionRule = apply(organization)

  implicit def groupArtifactIDToExclusionRule(gaid: GroupArtifactID): SbtExclusionRule =
    SbtExclusionRule(gaid.groupID, gaid.artifactID, "*", Vector.empty, gaid.crossVersion)
}
