/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class RequestedForceVersion private (
  val module: String,
  val version: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RequestedForceVersion => (this.module == x.module) && (this.version == x.version)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "lmcoursier.internal.RequestedForceVersion".##) + module.##) + version.##)
  }
  override def toString: String = {
    "RequestedForceVersion(" + module + ", " + version + ")"
  }
  private def copy(module: String = module, version: String = version): RequestedForceVersion = {
    new RequestedForceVersion(module, version)
  }
  def withModule(module: String): RequestedForceVersion = {
    copy(module = module)
  }
  def withVersion(version: String): RequestedForceVersion = {
    copy(version = version)
  }
}
object RequestedForceVersion {
  
  def apply(module: String, version: String): RequestedForceVersion = new RequestedForceVersion(module, version)
}
