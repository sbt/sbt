package sbt
package librarymanagement

/** Represents an Ivy configuration. */
final class Configuration private[sbt] (
    val id: String,
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val extendsConfigs: Vector[Configuration],
    val transitive: Boolean
) extends ConfigurationExtra
    with Serializable {

  require(name != null, "name cannot be null")
  require(name.nonEmpty, "name cannot be empty")
  require(id != null, "id cannot be null")
  require(id.nonEmpty, "id cannot be empty")
  require(id.head.isUpper, s"id must be capitalized: $id")

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

  override val hashCode: Int =
    37 * (37 * (37 * (37 * (37 * (37 * (17 +
      id.##) + name.##) + description.##) + isPublic.##) + extendsConfigs.##) + transitive.##)

  override def toString: String = name

  protected[this] def copy(
      id: String = id,
      name: String = name,
      description: String = description,
      isPublic: Boolean = isPublic,
      extendsConfigs: Vector[Configuration] = extendsConfigs,
      transitive: Boolean = transitive
  ): Configuration =
    new Configuration(id, name, description, isPublic, extendsConfigs, transitive)

  def withDescription(description: String): Configuration = copy(description = description)

  def withIsPublic(isPublic: Boolean): Configuration = copy(isPublic = isPublic)

  def withExtendsConfigs(extendsConfigs: Vector[Configuration]): Configuration =
    copy(extendsConfigs = extendsConfigs)

  def withTransitive(transitive: Boolean): Configuration =
    copy(transitive = transitive)

  def toConfigRef: ConfigRef = ConfigRef(name)
}

object Configuration {
  // Don't call this directly. It's intended to be used from config macro.
  def of(id: String, name: String): Configuration =
    new Configuration(id, name, "", true, Vector.empty, true)

  def of(
      id: String,
      name: String,
      description: String,
      isPublic: Boolean,
      extendsConfigs: Vector[Configuration],
      transitive: Boolean
  ): Configuration =
    new Configuration(id, name, description, isPublic, extendsConfigs, transitive)
}
