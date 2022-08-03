/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class OutputPathItem private (
  val uri: java.net.URI,
  val kind: Int) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: OutputPathItem => (this.uri == x.uri) && (this.kind == x.kind)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.OutputPathItem".##) + uri.##) + kind.##)
  }
  override def toString: String = {
    "OutputPathItem(" + uri + ", " + kind + ")"
  }
  private[this] def copy(uri: java.net.URI = uri, kind: Int = kind): OutputPathItem = {
    new OutputPathItem(uri, kind)
  }
  def withUri(uri: java.net.URI): OutputPathItem = {
    copy(uri = uri)
  }
  def withKind(kind: Int): OutputPathItem = {
    copy(kind = kind)
  }
}
object OutputPathItem {
  
  def apply(uri: java.net.URI, kind: Int): OutputPathItem = new OutputPathItem(uri, kind)
}
