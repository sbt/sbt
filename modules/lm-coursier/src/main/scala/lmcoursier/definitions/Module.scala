/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
final class Module private (
  val organization: Organization,
  val name: ModuleName,
  val attributes: Map[String, String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Module => (this.organization == x.organization) && (this.name == x.name) && (this.attributes == x.attributes)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.Module".##) + organization.##) + name.##) + attributes.##)
  }
  override def toString: String = {
    "Module(" + organization + ", " + name + ", " + attributes + ")"
  }
  private[this] def copy(organization: Organization = organization, name: ModuleName = name, attributes: Map[String, String] = attributes): Module = {
    new Module(organization, name, attributes)
  }
  def withOrganization(organization: Organization): Module = {
    copy(organization = organization)
  }
  def withName(name: ModuleName): Module = {
    copy(name = name)
  }
  def withAttributes(attributes: Map[String, String]): Module = {
    copy(attributes = attributes)
  }
}
object Module {
  
  def apply(organization: Organization, name: ModuleName, attributes: Map[String, String]): Module = new Module(organization, name, attributes)
}
