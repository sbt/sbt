/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class HVFRURI private (
  val name: xsbti.HashedVirtualFileRef,
  val value: java.net.URI) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: HVFRURI => (this.name == x.name) && (this.value == x.value)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.worker.HVFRURI".##) + name.##) + value.##)
  }
  override def toString: String = {
    "HVFRURI(" + name + ", " + value + ")"
  }
  private def copy(name: xsbti.HashedVirtualFileRef = name, value: java.net.URI = value): HVFRURI = {
    new HVFRURI(name, value)
  }
  def withName(name: xsbti.HashedVirtualFileRef): HVFRURI = {
    copy(name = name)
  }
  def withValue(value: java.net.URI): HVFRURI = {
    copy(value = value)
  }
}
object HVFRURI {
  
  def apply(name: xsbti.HashedVirtualFileRef, value: java.net.URI): HVFRURI = new HVFRURI(name, value)
}
