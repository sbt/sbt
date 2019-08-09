/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
final class Dependency private (
  val module: Module,
  val version: String,
  val configuration: Configuration,
  val exclusions: Set[(Organization, ModuleName)],
  val publication: Publication,
  val optional: Boolean,
  val transitive: Boolean) extends Serializable {
  def attributes: Attributes = publication.attributes
  def withAttributes(attributes: Attributes): Dependency = withPublication(publication.withType(attributes.`type`).withClassifier(attributes.classifier))
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Dependency => (this.module == x.module) && (this.version == x.version) && (this.configuration == x.configuration) && (this.exclusions == x.exclusions) && (this.publication == x.publication) && (this.optional == x.optional) && (this.transitive == x.transitive)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.Dependency".##) + module.##) + version.##) + configuration.##) + exclusions.##) + publication.##) + optional.##) + transitive.##)
  }
  override def toString: String = {
    "Dependency(" + module + ", " + version + ", " + configuration + ", " + exclusions + ", " + publication + ", " + optional + ", " + transitive + ")"
  }
  private[this] def copy(module: Module = module, version: String = version, configuration: Configuration = configuration, exclusions: Set[(Organization, ModuleName)] = exclusions, publication: Publication = publication, optional: Boolean = optional, transitive: Boolean = transitive): Dependency = {
    new Dependency(module, version, configuration, exclusions, publication, optional, transitive)
  }
  def withModule(module: Module): Dependency = {
    copy(module = module)
  }
  def withVersion(version: String): Dependency = {
    copy(version = version)
  }
  def withConfiguration(configuration: Configuration): Dependency = {
    copy(configuration = configuration)
  }
  def withExclusions(exclusions: Set[(Organization, ModuleName)]): Dependency = {
    copy(exclusions = exclusions)
  }
  def withPublication(publication: Publication): Dependency = {
    copy(publication = publication)
  }
  def withOptional(optional: Boolean): Dependency = {
    copy(optional = optional)
  }
  def withTransitive(transitive: Boolean): Dependency = {
    copy(transitive = transitive)
  }
}
object Dependency {
  def apply(module: Module, version: String, configuration: Configuration, exclusions: Set[(Organization, ModuleName)], attributes: Attributes, optional: Boolean, transitive: Boolean): Dependency = new Dependency(module, version, configuration, exclusions, Publication("", attributes.`type`, Extension(""), attributes.classifier), optional, transitive)
  def apply(module: Module, version: String, configuration: Configuration, exclusions: Set[(Organization, ModuleName)], publication: Publication, optional: Boolean, transitive: Boolean): Dependency = new Dependency(module, version, configuration, exclusions, publication, optional, transitive)
}
