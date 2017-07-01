/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Represents an Ivy configuration. */
final class Configuration private (
  val name: String,
  val description: String,
  val isPublic: Boolean,
  val extendsConfigs: Vector[sbt.librarymanagement.Configuration],
  val transitive: Boolean) extends sbt.librarymanagement.ConfigurationExtra with Serializable {
  
  private def this(name: String) = this(name, "", true, Vector.empty, true)
  
  override def equals(o: Any): Boolean = o match {
    case x: Configuration => (this.name == x.name) && (this.description == x.description) && (this.isPublic == x.isPublic) && (this.extendsConfigs == x.extendsConfigs) && (this.transitive == x.transitive)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "Configuration".##) + name.##) + description.##) + isPublic.##) + extendsConfigs.##) + transitive.##)
  }
  override def toString: String = {
    name
  }
  protected[this] def copy(name: String = name, description: String = description, isPublic: Boolean = isPublic, extendsConfigs: Vector[sbt.librarymanagement.Configuration] = extendsConfigs, transitive: Boolean = transitive): Configuration = {
    new Configuration(name, description, isPublic, extendsConfigs, transitive)
  }
  def withName(name: String): Configuration = {
    copy(name = name)
  }
  def withDescription(description: String): Configuration = {
    copy(description = description)
  }
  def withIsPublic(isPublic: Boolean): Configuration = {
    copy(isPublic = isPublic)
  }
  def withExtendsConfigs(extendsConfigs: Vector[sbt.librarymanagement.Configuration]): Configuration = {
    copy(extendsConfigs = extendsConfigs)
  }
  def withTransitive(transitive: Boolean): Configuration = {
    copy(transitive = transitive)
  }
}
object Configuration {
  
  def apply(name: String): Configuration = new Configuration(name, "", true, Vector.empty, true)
  def apply(name: String, description: String, isPublic: Boolean, extendsConfigs: Vector[sbt.librarymanagement.Configuration], transitive: Boolean): Configuration = new Configuration(name, description, isPublic, extendsConfigs, transitive)
}
