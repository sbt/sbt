package coursier.core

/**
 * Identifies a "module".
 *
 * During resolution, all dependencies having the same module
 * will be given the same version, if there are no version conflicts
 * between them.
 *
 * Using the same terminology as Ivy.
 *
 * Ivy attributes would land here, if support for it is added.
 */
case class Module(
  organization: String,
  name: String
) {

  def trim: Module = copy(
    organization = organization.trim,
    name = name.trim
  )

  override def toString = s"$organization:$name"
}

/**
 * Dependencies with the same @module will typically see their @version-s merged.
 *
 * The remaining fields are left untouched, some being transitively
 * propagated (exclusions, optional, in particular).
 */
case class Dependency(
  module: Module,
  version: String,
  configuration: String,
  exclusions: Set[(String, String)],

  // Maven-specific
  attributes: Attributes,
  optional: Boolean
) {
  def moduleVersion = (module, version)
}

// Maven-specific
case class Attributes(
  `type`: String,
  classifier: String
)

case class Project(
  module: Module,
  version: String,
  // First String is configuration (scope for Maven)
  dependencies: Seq[(String, Dependency)],
  // For Maven, this is the standard scopes as an Ivy configuration
  configurations: Map[String, Seq[String]],

  // Maven-specific
  parent: Option[(Module, String)],
  dependencyManagement: Seq[(String, Dependency)],
  properties: Map[String, String],
  profiles: Seq[Profile],
  versions: Option[Versions],
  snapshotVersioning: Option[SnapshotVersioning],

  // Ivy-specific
  // First String is configuration
  publications: Seq[(String, Publication)]
) {
  def moduleVersion = (module, version)
}

// Maven-specific
case class Activation(properties: Seq[(String, Option[String])])

// Maven-specific
case class Profile(
  id: String,
  activeByDefault: Option[Boolean],
  activation: Activation,
  dependencies: Seq[(String, Dependency)],
  dependencyManagement: Seq[(String, Dependency)],
  properties: Map[String, String]
)

// Maven-specific
case class Versions(
  latest: String,
  release: String,
  available: List[String],
  lastUpdated: Option[Versions.DateTime]
)

object Versions {
  case class DateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int
  )
}

// Maven-specific
case class SnapshotVersion(
  classifier: String,
  extension: String,
  value: String,
  updated: Option[Versions.DateTime]
)

// Maven-specific
case class SnapshotVersioning(
  module: Module,
  version: String,
  latest: String,
  release: String,
  timestamp: String,
  buildNumber: Option[Int],
  localCopy: Option[Boolean],
  lastUpdated: Option[Versions.DateTime],
  snapshotVersions: Seq[SnapshotVersion]
)

// Ivy-specific
case class Publication(
  name: String,
  `type`: String,
  ext: String
)

case class Artifact(
  url: String,
  checksumUrls: Map[String, String],
  extra: Map[String, Artifact],
  attributes: Attributes,
  changing: Boolean
)

object Artifact {
  trait Source {
    def artifacts(dependency: Dependency, project: Project): Seq[Artifact]
  }
}
