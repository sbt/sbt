/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class RequestedDependency private (
  val configuration: String,
  val organization: String,
  val name: String,
  val version: String,
  val variantSelector: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RequestedDependency => (this.configuration == x.configuration) && (this.organization == x.organization) && (this.name == x.name) && (this.version == x.version) && (this.variantSelector == x.variantSelector)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.internal.RequestedDependency".##) + configuration.##) + organization.##) + name.##) + version.##) + variantSelector.##)
  }
  override def toString: String = {
    "RequestedDependency(" + configuration + ", " + organization + ", " + name + ", " + version + ", " + variantSelector + ")"
  }
  private def copy(configuration: String = configuration, organization: String = organization, name: String = name, version: String = version, variantSelector: String = variantSelector): RequestedDependency = {
    new RequestedDependency(configuration, organization, name, version, variantSelector)
  }
  def withConfiguration(configuration: String): RequestedDependency = {
    copy(configuration = configuration)
  }
  def withOrganization(organization: String): RequestedDependency = {
    copy(organization = organization)
  }
  def withName(name: String): RequestedDependency = {
    copy(name = name)
  }
  def withVersion(version: String): RequestedDependency = {
    copy(version = version)
  }
  def withVariantSelector(variantSelector: String): RequestedDependency = {
    copy(variantSelector = variantSelector)
  }
}
object RequestedDependency {
  
  def apply(configuration: String, organization: String, name: String, version: String, variantSelector: String): RequestedDependency = new RequestedDependency(configuration, organization, name, version, variantSelector)
}
