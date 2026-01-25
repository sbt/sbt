/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class StringURI private (
  val name: String,
  val value: java.net.URI) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: StringURI => (this.name == x.name) && (this.value == x.value)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.worker.StringURI".##) + name.##) + value.##)
  }
  override def toString: String = {
    "StringURI(" + name + ", " + value + ")"
  }
  private def copy(name: String = name, value: java.net.URI = value): StringURI = {
    new StringURI(name, value)
  }
  def withName(name: String): StringURI = {
    copy(name = name)
  }
  def withValue(value: java.net.URI): StringURI = {
    copy(value = value)
  }
}
object StringURI {
  
  def apply(name: String, value: java.net.URI): StringURI = new StringURI(name, value)
}
