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
case class Module(organization: String,
                  name: String) {

  def trim: Module = copy(
    organization = organization.trim,
    name = name.trim
  )
  override def toString = s"$organization:$name"
}

sealed abstract class Scope(val name: String)

/**
 * Dependencies with the same @module will typically see their @version-s merged.
 *
 * The remaining fields are left untouched, some being transitively
 * propagated (exclusions, optional, in particular).
 */
case class Dependency(module: Module,
                      version: String,
                      scope: Scope,
                      artifacts: Artifacts,
                      exclusions: Set[(String, String)],
                      optional: Boolean) {
  def moduleVersion = (module, version)
}

sealed trait Artifacts

object Artifacts {
  /**
   * May become a bit more complicated with Ivy support,
   * but should still point at one single artifact.
   */
  case class Artifact(`type`: String,
                      classifier: String)

  sealed trait WithProject extends Artifacts {
    def artifacts(project: Project): Seq[Artifact]
  }

  sealed trait Sufficient extends Artifacts {
    def artifacts: Seq[Artifact]
  }

  case class Maven(`type`: String,
                   classifier: String) extends Sufficient {
    def artifacts: Seq[Artifact] = Seq(Artifact(`type`, classifier))
  }
}

case class Project(module: Module,
                   version: String,
                   dependencies: Seq[Dependency],
                   parent: Option[(Module, String)],
                   dependencyManagement: Seq[Dependency],
                   properties: Map[String, String],
                   profiles: Seq[Profile],
                   versions: Option[Versions]) {
  def moduleVersion = (module, version)
}

object Scope {
  case object Compile extends Scope("compile")
  case object Runtime extends Scope("runtime")
  case object Test extends Scope("test")
  case object Provided extends Scope("provided")
  case object Import extends Scope("import")
  case class Other(override val name: String) extends Scope(name)
}

case class Activation(properties: Seq[(String, Option[String])])

case class Profile(id: String,
                   activeByDefault: Option[Boolean],
                   activation: Activation,
                   dependencies: Seq[Dependency],
                   dependencyManagement: Seq[Dependency],
                   properties: Map[String, String])

case class Versions(latest: String,
                    release: String,
                    available: List[String],
                    lastUpdated: Option[Versions.DateTime])

object Versions {
  case class DateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int)
}
