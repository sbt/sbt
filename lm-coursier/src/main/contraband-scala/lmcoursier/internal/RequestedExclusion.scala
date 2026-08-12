/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class RequestedExclusion private (
  val organization: String,
  val name: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RequestedExclusion => (this.organization == x.organization) && (this.name == x.name)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "lmcoursier.internal.RequestedExclusion".##) + organization.##) + name.##)
  }
  override def toString: String = {
    "RequestedExclusion(" + organization + ", " + name + ")"
  }
  private def copy(organization: String = organization, name: String = name): RequestedExclusion = {
    new RequestedExclusion(organization, name)
  }
  def withOrganization(organization: String): RequestedExclusion = {
    copy(organization = organization)
  }
  def withName(name: String): RequestedExclusion = {
    copy(name = name)
  }
}
object RequestedExclusion {
  
  def apply(organization: String, name: String): RequestedExclusion = new RequestedExclusion(organization, name)
}
