package sbt

import sbt.impl.{ GroupArtifactID, GroupID }

final class SbtExclusionRule(val organization: String,
                             val name: String,
                             val artifact: String,
                             val configurations: Seq[String],
                             val crossVersion: CrossVersion) {

  def copy(organization: String = this.organization,
           name: String = this.name,
           artifact: String = this.artifact,
           configurations: Seq[String] = this.configurations,
           crossVersion: CrossVersion = this.crossVersion): SbtExclusionRule =
    SbtExclusionRule(
      organization = organization,
      name = name,
      artifact = artifact,
      configurations = configurations,
      crossVersion = crossVersion
    )
}

object SbtExclusionRule {
  def apply(organization: String): SbtExclusionRule =
    new SbtExclusionRule(organization, "*", "*", Nil, CrossVersion.Disabled)

  def apply(organization: String, name: String): SbtExclusionRule =
    new SbtExclusionRule(organization, name, "*", Nil, CrossVersion.Disabled)

  def apply(organization: String,
            name: String,
            artifact: String,
            configurations: Seq[String],
            crossVersion: CrossVersion): SbtExclusionRule =
    new SbtExclusionRule(organization, name, artifact, configurations, crossVersion)

  implicit def groupIdToExclusionRule(organization: GroupID): SbtExclusionRule =
    SbtExclusionRule(organization.groupID)
  implicit def stringToExclusionRule(organization: String): SbtExclusionRule =
    SbtExclusionRule(organization)
  implicit def groupArtifactIDToExcludsionRule(gaid: GroupArtifactID): SbtExclusionRule =
    SbtExclusionRule(gaid.groupID, gaid.artifactID, "*", Nil, gaid.crossVersion)
}
