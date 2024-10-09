/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class ModuleConfiguration private (
  val organization: String,
  val name: String,
  val revision: String,
  val resolver: sbt.librarymanagement.Resolver) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ModuleConfiguration => (this.organization == x.organization) && (this.name == x.name) && (this.revision == x.revision) && (this.resolver == x.resolver)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ModuleConfiguration".##) + organization.##) + name.##) + revision.##) + resolver.##)
  }
  override def toString: String = {
    "ModuleConfiguration(" + organization + ", " + name + ", " + revision + ", " + resolver + ")"
  }
  private[this] def copy(organization: String = organization, name: String = name, revision: String = revision, resolver: sbt.librarymanagement.Resolver = resolver): ModuleConfiguration = {
    new ModuleConfiguration(organization, name, revision, resolver)
  }
  def withOrganization(organization: String): ModuleConfiguration = {
    copy(organization = organization)
  }
  def withName(name: String): ModuleConfiguration = {
    copy(name = name)
  }
  def withRevision(revision: String): ModuleConfiguration = {
    copy(revision = revision)
  }
  def withResolver(resolver: sbt.librarymanagement.Resolver): ModuleConfiguration = {
    copy(resolver = resolver)
  }
}
object ModuleConfiguration {
  def apply(org: String, resolver: sbt.librarymanagement.Resolver): ModuleConfiguration = apply(org, "*", "*", resolver)
  def apply(org: String, name: String, resolver: sbt.librarymanagement.Resolver): ModuleConfiguration = ModuleConfiguration(org, name, "*", resolver)
  def apply(organization: String, name: String, revision: String, resolver: sbt.librarymanagement.Resolver): ModuleConfiguration = new ModuleConfiguration(organization, name, revision, resolver)
}
