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
  attributes: Attributes,
  exclusions: Set[(String, String)],
  optional: Boolean
) {
  def moduleVersion = (module, version)
}

case class Attributes(
  `type`: String,
  classifier: String
)

case class Project(
  module: Module,
  version: String,
  dependencies: Seq[(String, Dependency)],
  parent: Option[(Module, String)],
  dependencyManagement: Seq[(String, Dependency)],
  configurations: Map[String, Seq[String]],
  properties: Map[String, String],
  profiles: Seq[Profile],
  versions: Option[Versions],
  snapshotVersioning: Option[SnapshotVersioning]
) {
  def moduleVersion = (module, version)
}

case class Activation(properties: Seq[(String, Option[String])])

case class Profile(
  id: String,
  activeByDefault: Option[Boolean],
  activation: Activation,
  dependencies: Seq[(String, Dependency)],
  dependencyManagement: Seq[(String, Dependency)],
  properties: Map[String, String]
)

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

case class SnapshotVersion(
  classifier: String,
  extension: String,
  value: String,
  updated: Option[Versions.DateTime]
)

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

case class Artifact(
  url: String,
  checksumUrls: Map[String, String],
  extra: Map[String, Artifact],
  attributes: Attributes
)

object Artifact {
  trait Source {
    def artifacts(dependency: Dependency, project: Project): Seq[Artifact]
  }
}
