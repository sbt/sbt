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
                      attributes: Attributes,
                      exclusions: Set[(String, String)],
                      optional: Boolean) {
  def moduleVersion = (module, version)
}

case class Attributes(`type`: String,
                      classifier: String)

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

// FIXME Move to MavenRepository?
case class Versions(latest: String,
                    release: String,
                    available: List[String],
                    lastUpdated: Option[Versions.DateTime])

object Versions {
  case class DateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int)
}

case class Artifact(url: String,
                    extra: Map[String, String],
                    attributes: Attributes)

object Artifact {
  val md5 = "md5"
  val sha1 = "sha1"
  val sig = "pgp"
  val sigMd5 = "md5-pgp"
  val sigSha1 = "sha1-pgp"
  val sources = "src"
  val sourcesMd5 = "md5-src"
  val sourcesSha1 = "sha1-src"
  val sourcesSig = "src-pgp"
  val sourcesSigMd5 = "md5-src-pgp"
  val sourcesSigSha1 = "sha1-src-pgp"
  val javadoc = "javadoc"
  val javadocMd5 = "md5-javadoc"
  val javadocSha1 = "sha1-javadoc"
  val javadocSig = "javadoc-pgp"
  val javadocSigMd5 = "md5-javadoc-pgp"
  val javadocSigSha1 = "sha1-javadoc-pgp"

  trait Source {
    def artifacts(dependency: Dependency,
                  project: Project): Seq[Artifact]
  }
}
