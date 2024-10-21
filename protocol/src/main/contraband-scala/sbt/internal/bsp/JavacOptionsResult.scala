/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class JavacOptionsResult private (
  val items: Vector[sbt.internal.bsp.JavacOptionsItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JavacOptionsResult => (this.items == x.items)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.JavacOptionsResult".##) + items.##)
  }
  override def toString: String = {
    "JavacOptionsResult(" + items + ")"
  }
  private def copy(items: Vector[sbt.internal.bsp.JavacOptionsItem] = items): JavacOptionsResult = {
    new JavacOptionsResult(items)
  }
  def withItems(items: Vector[sbt.internal.bsp.JavacOptionsItem]): JavacOptionsResult = {
    copy(items = items)
  }
}
object JavacOptionsResult {
  
  def apply(items: Vector[sbt.internal.bsp.JavacOptionsItem]): JavacOptionsResult = new JavacOptionsResult(items)
}
