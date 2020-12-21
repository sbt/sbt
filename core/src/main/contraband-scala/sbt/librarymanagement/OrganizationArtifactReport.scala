/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * OrganizationArtifactReport represents an organization+name entry in Ivy resolution report.
 * In sbt's terminology, "module" consists of organization, name, and version.
 * In Ivy's, "module" means just organization and name, and the one including version numbers
 * are called revisions.
 * 
 * A sequence of OrganizationArtifactReport called details is newly added to ConfigurationReport, replacing evicted.
 * (Note old evicted was just a seq of ModuleIDs).
 * OrganizationArtifactReport groups the ModuleReport of both winners and evicted reports by their organization and name,
 * which can be used to calculate detailed eviction warning etc.
 */
final class OrganizationArtifactReport private (
  val organization: String,
  val name: String,
  val modules: Vector[sbt.librarymanagement.ModuleReport]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: OrganizationArtifactReport => (this.organization == x.organization) && (this.name == x.name) && (this.modules == x.modules)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.OrganizationArtifactReport".##) + organization.##) + name.##) + modules.##)
  }
  override def toString: String = {
    val details = modules map { _.detailReport }
    s"\t$organization:$name\n${details.mkString}\n"
  }
  private[this] def copy(organization: String = organization, name: String = name, modules: Vector[sbt.librarymanagement.ModuleReport] = modules): OrganizationArtifactReport = {
    new OrganizationArtifactReport(organization, name, modules)
  }
  def withOrganization(organization: String): OrganizationArtifactReport = {
    copy(organization = organization)
  }
  def withName(name: String): OrganizationArtifactReport = {
    copy(name = name)
  }
  def withModules(modules: Vector[sbt.librarymanagement.ModuleReport]): OrganizationArtifactReport = {
    copy(modules = modules)
  }
}
object OrganizationArtifactReport {
  
  def apply(organization: String, name: String, modules: Vector[sbt.librarymanagement.ModuleReport]): OrganizationArtifactReport = new OrganizationArtifactReport(organization, name, modules)
}
