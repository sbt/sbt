/* sbt -- Simple Build Tool
 * Copyright 2009,2010  Mark Harrah
 */
package sbt.librarymanagement

import sbt.internal.librarymanagement.StringUtilities.nonEmpty

/**
 * DependencyBuilders implements the implicits for % and %% DSL.
 */
trait DependencyBuilders {
  // See http://www.scala-lang.org/news/2.12.0#traits-compile-to-interfaces
  // Avoid defining fields (val or var, but a constant is ok – final val without result type)
  // Avoid calling super
  // Avoid initializer statements in the body

  import DependencyBuilders._

  implicit def stringToOrganization(organization: String): Organization = {
    nonEmpty(organization, "Organization")
    new Organization(organization)
  }

  implicit def toRepositoryName(name: String): RepositoryName = {
    nonEmpty(name, "Repository name")
    new RepositoryName(name)
  }

  implicit def moduleIDConfigurable(m: ModuleID): ModuleIDConfigurable = {
    require(m.configurations.isEmpty, "Configurations already specified for module " + m)
    new ModuleIDConfigurable(m)
  }
}

object DependencyBuilders {
  final class Organization private[sbt] (private[sbt] val organization: String) {
    def %(name: String) = organizationArtifact(name, Disabled())
    def %%(name: String): OrganizationArtifactName =
      organizationArtifact(name, CrossVersion.binary)

    private def organizationArtifact(name: String, cross: CrossVersion) = {
      nonEmpty(name, "Name")
      new OrganizationArtifactName(organization, name, cross)
    }
  }

  final class OrganizationArtifactName private[sbt] (
      private[sbt] val organization: String,
      private[sbt] val name: String,
      private[sbt] val crossVersion: CrossVersion
  ) {
    def %(revision: String): ModuleID = {
      nonEmpty(revision, "Revision")
      ModuleID(organization, name, revision).cross(crossVersion)
    }
  }

  final class ModuleIDConfigurable private[sbt] (moduleID: ModuleID) {
    def %(configuration: Configuration): ModuleID = %(configuration.name)
    def %(configuration: ConfigRef): ModuleID = %(configuration.name)

    def %(configurations: String): ModuleID = {
      nonEmpty(configurations, "Configurations")
      val c = configurations
      moduleID.withConfigurations(configurations = Some(c))
    }
  }

  final class RepositoryName private[sbt] (name: String) {
    def at(location: String): MavenRepository = {
      nonEmpty(location, "Repository location")
      MavenRepository(name, location)
    }
  }
}
