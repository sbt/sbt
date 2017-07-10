package sbt
package librarymanagement

/** Represents an Ivy configuration. */
final class Configuration private[sbt] (
    val id: String,
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val extendsConfigs: Vector[sbt.librarymanagement.Configuration],
    val transitive: Boolean)
    extends sbt.librarymanagement.ConfigurationExtra
    with Serializable {

  require(name != null, "name cannot be null")
  require(name.size > 0, "name cannot be empty")
  require(id != null, "id cannot be null")
  require(id.size > 0, "id cannot be empty")
  require(id.head.isUpper, s"id must be capitalized: $id")

  private def this(id: String, name: String) =
    this(id, name, "", true, Vector.empty, true)

  override def equals(o: Any): Boolean = o match {
    case x: Configuration =>
      (this.id == x.id) &&
        (this.name == x.name) &&
        (this.description == x.description) &&
        (this.isPublic == x.isPublic) &&
        (this.extendsConfigs == x.extendsConfigs) &&
        (this.transitive == x.transitive)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + id.##) + name.##) + description.##) + isPublic.##) + extendsConfigs.##) + transitive.##)
  }
  override def toString: String = {
    name
  }
  protected[this] def copy(id: String = id,
                           name: String = name,
                           description: String = description,
                           isPublic: Boolean = isPublic,
                           extendsConfigs: Vector[sbt.librarymanagement.Configuration] =
                             extendsConfigs,
                           transitive: Boolean = transitive): Configuration = {
    new Configuration(id, name, description, isPublic, extendsConfigs, transitive)
  }

  def withDescription(description: String): Configuration = {
    copy(description = description)
  }

  def withIsPublic(isPublic: Boolean): Configuration = {
    copy(isPublic = isPublic)
  }

  def withExtendsConfigs(
      extendsConfigs: Vector[sbt.librarymanagement.Configuration]): Configuration = {
    copy(extendsConfigs = extendsConfigs)
  }

  def withTransitive(transitive: Boolean): Configuration = {
    copy(transitive = transitive)
  }

  def toConfigRef: ConfigRef = ConfigRef(name)
}
object Configuration {
  private[sbt] def apply(id: String, name: String): Configuration =
    new Configuration(id, name, "", true, Vector.empty, true)
  private[sbt] def apply(id: String,
                         name: String,
                         description: String,
                         isPublic: Boolean,
                         extendsConfigs: Vector[sbt.librarymanagement.Configuration],
                         transitive: Boolean): Configuration =
    new Configuration(id, name, description, isPublic, extendsConfigs, transitive)
}
