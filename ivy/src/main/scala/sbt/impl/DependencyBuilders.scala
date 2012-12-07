/* sbt -- Simple Build Tool
 * Copyright 2009,2010  Mark Harrah
 */
package sbt
package impl

import StringUtilities.nonEmpty

trait DependencyBuilders
{
	final implicit def toGroupID(groupID: String): GroupID =
	{
		nonEmpty(groupID, "Group ID")
		new GroupID(groupID)
	}
	final implicit def toRepositoryName(name: String): RepositoryName =
	{
		nonEmpty(name, "Repository name")
		new RepositoryName(name)
	}
	final implicit def moduleIDConfigurable(m: ModuleID): ModuleIDConfigurable =
	{
		require(m.configurations.isEmpty, "Configurations already specified for module " + m)
		new ModuleIDConfigurable(m)
	}
}

final class GroupID private[sbt] (groupID: String)
{
	def % (artifactID: String) = groupArtifact(artifactID, CrossVersion.Disabled)
	def %% (artifactID: String): GroupArtifactID = groupArtifact(artifactID, CrossVersion.binary)

	@deprecated(deprecationMessage, "0.12.0")
	def %% (artifactID: String, crossVersion: String => String) = groupArtifact(artifactID, CrossVersion.binaryMapped(crossVersion))
	@deprecated(deprecationMessage, "0.12.0")
	def %% (artifactID: String, alternatives: (String, String)*) = groupArtifact(artifactID, CrossVersion.binaryMapped(Map(alternatives: _*) orElse { case s => s }))

	private def groupArtifact(artifactID: String, cross: CrossVersion) =
	{
		nonEmpty(artifactID, "Artifact ID")
		new GroupArtifactID(groupID, artifactID, cross)
	}

	private[this] def deprecationMessage = """Use the cross method on the constructed ModuleID.  For example: ("a" % "b" % "1").cross(...)"""
}
final class GroupArtifactID private[sbt] (groupID: String, artifactID: String, crossVersion: CrossVersion)
{
	def % (revision: String): ModuleID =
	{
		nonEmpty(revision, "Revision")
		ModuleID(groupID, artifactID, revision).cross(crossVersion)
	}
}
final class ModuleIDConfigurable private[sbt] (moduleID: ModuleID)
{
	def % (configuration: Configuration): ModuleID = %(configuration.name)

	def % (configurations: String): ModuleID =
	{
		nonEmpty(configurations, "Configurations")
		val c = configurations
		moduleID.copy(configurations = Some(c))
	}
}
final class RepositoryName private[sbt] (name: String)
{
	def at (location: String) =
	{
		nonEmpty(location, "Repository location")
		new MavenRepository(name, location)
	}
}
