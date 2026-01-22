/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class DependencyLock private (
  val organization: String,
  val name: String,
  val version: String,
  val configuration: String,
  val classifier: Option[String],
  val tpe: String,
  val transitives: Vector[String],
  val artifacts: Vector[lmcoursier.internal.ArtifactLock]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: DependencyLock => (this.organization == x.organization) && (this.name == x.name) && (this.version == x.version) && (this.configuration == x.configuration) && (this.classifier == x.classifier) && (this.tpe == x.tpe) && (this.transitives == x.transitives) && (this.artifacts == x.artifacts)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.internal.DependencyLock".##) + organization.##) + name.##) + version.##) + configuration.##) + classifier.##) + tpe.##) + transitives.##) + artifacts.##)
  }
  override def toString: String = {
    "DependencyLock(" + organization + ", " + name + ", " + version + ", " + configuration + ", " + classifier + ", " + tpe + ", " + transitives + ", " + artifacts + ")"
  }
  private def copy(organization: String = organization, name: String = name, version: String = version, configuration: String = configuration, classifier: Option[String] = classifier, tpe: String = tpe, transitives: Vector[String] = transitives, artifacts: Vector[lmcoursier.internal.ArtifactLock] = artifacts): DependencyLock = {
    new DependencyLock(organization, name, version, configuration, classifier, tpe, transitives, artifacts)
  }
  def withOrganization(organization: String): DependencyLock = {
    copy(organization = organization)
  }
  def withName(name: String): DependencyLock = {
    copy(name = name)
  }
  def withVersion(version: String): DependencyLock = {
    copy(version = version)
  }
  def withConfiguration(configuration: String): DependencyLock = {
    copy(configuration = configuration)
  }
  def withClassifier(classifier: Option[String]): DependencyLock = {
    copy(classifier = classifier)
  }
  def withClassifier(classifier: String): DependencyLock = {
    copy(classifier = Option(classifier))
  }
  def withTpe(tpe: String): DependencyLock = {
    copy(tpe = tpe)
  }
  def withTransitives(transitives: Vector[String]): DependencyLock = {
    copy(transitives = transitives)
  }
  def withArtifacts(artifacts: Vector[lmcoursier.internal.ArtifactLock]): DependencyLock = {
    copy(artifacts = artifacts)
  }
}
object DependencyLock {
  
  def apply(organization: String, name: String, version: String, configuration: String, classifier: Option[String], tpe: String, transitives: Vector[String], artifacts: Vector[lmcoursier.internal.ArtifactLock]): DependencyLock = new DependencyLock(organization, name, version, configuration, classifier, tpe, transitives, artifacts)
  def apply(organization: String, name: String, version: String, configuration: String, classifier: String, tpe: String, transitives: Vector[String], artifacts: Vector[lmcoursier.internal.ArtifactLock]): DependencyLock = new DependencyLock(organization, name, version, configuration, Option(classifier), tpe, transitives, artifacts)
}
