package coursier.core

/**
 * Identifies a "module".
 *
 * During resolution, all dependencies having the same module
 * will be given the same version, if there are no version conflicts
 * between them.
 *
 * Using the same terminology as Ivy.
 */
final case class Module(
  organization: String,
  name: String,
  attributes: Map[String, String]
) {

  def trim: Module = copy(
    organization = organization.trim,
    name = name.trim
  )

  private def attributesStr = attributes.toSeq
    .sortBy { case (k, _) => k }
    .map { case (k, v) => s"$k=$v" }
    .mkString(";")

  def nameWithAttributes: String =
    name + (if (attributes.nonEmpty) s";$attributesStr" else "")

  override def toString: String =
    s"$organization:$nameWithAttributes"

  override final lazy val hashCode = Module.unapply(this).get.hashCode()
}

/**
 * Dependencies with the same @module will typically see their @version-s merged.
 *
 * The remaining fields are left untouched, some being transitively
 * propagated (exclusions, optional, in particular).
 */
final case class Dependency(
  module: Module,
  version: String,
  configuration: String,
  exclusions: Set[(String, String)],

  // Maven-specific
  attributes: Attributes,
  optional: Boolean,

  transitive: Boolean
) {
  lazy val moduleVersion = (module, version)

  override lazy val hashCode = Dependency.unapply(this).get.hashCode()
}

// Maven-specific
final case class Attributes(
  `type`: String,
  classifier: String
) {
  def publication(name: String, ext: String): Publication =
    Publication(name, `type`, ext, classifier)
}

final case class Project(
  module: Module,
  version: String,
  // First String is configuration (scope for Maven)
  dependencies: Seq[(String, Dependency)],
  // For Maven, this is the standard scopes as an Ivy configuration
  configurations: Map[String, Seq[String]],

  // Maven-specific
  parent: Option[(Module, String)],
  dependencyManagement: Seq[(String, Dependency)],
  properties: Seq[(String, String)],
  profiles: Seq[Profile],
  versions: Option[Versions],
  snapshotVersioning: Option[SnapshotVersioning],

  // Ivy-specific
  // First String is configuration
  publications: Seq[(String, Publication)],

  // Extra infos, not used during resolution
  info: Info
) {
  lazy val moduleVersion = (module, version)

  /** All configurations that each configuration extends, including the ones it extends transitively */
  lazy val allConfigurations: Map[String, Set[String]] =
    Orders.allConfigurations(configurations)
}

/** Extra project info, not used during resolution */
final case class Info(
  description: String,
  homePage: String,
  licenses: Seq[(String, Option[String])],
  developers: Seq[Info.Developer],
  publication: Option[Versions.DateTime]
)

object Info {
  final case class Developer(
    id: String,
    name: String,
    url: String
  )

  val empty = Info("", "", Nil, Nil, None)
}

// Maven-specific
final case class Activation(properties: Seq[(String, Option[String])])

// Maven-specific
final case class Profile(
  id: String,
  activeByDefault: Option[Boolean],
  activation: Activation,
  dependencies: Seq[(String, Dependency)],
  dependencyManagement: Seq[(String, Dependency)],
  properties: Map[String, String]
)

// Maven-specific
final case class Versions(
  latest: String,
  release: String,
  available: List[String],
  lastUpdated: Option[Versions.DateTime]
)

object Versions {
  final case class DateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int
  )
}

// Maven-specific
final case class SnapshotVersion(
  classifier: String,
  extension: String,
  value: String,
  updated: Option[Versions.DateTime]
)

// Maven-specific
final case class SnapshotVersioning(
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
final case class Publication(
  name: String,
  `type`: String,
  ext: String,
  classifier: String
) {
  def attributes: Attributes = Attributes(`type`, classifier)
}

final case class Artifact(
  url: String,
  checksumUrls: Map[String, String],
  extra: Map[String, Artifact],
  attributes: Attributes,
  changing: Boolean
)

object Artifact {
  trait Source {
    def artifacts(
      dependency: Dependency,
      project: Project,
      overrideClassifiers: Option[Seq[String]]
    ): Seq[Artifact]
  }

  object Source {
    val empty: Source = new Source {
      def artifacts(
        dependency: Dependency,
        project: Project,
        overrideClassifiers: Option[Seq[String]]
      ): Seq[Artifact] = Nil
    }
  }
}
