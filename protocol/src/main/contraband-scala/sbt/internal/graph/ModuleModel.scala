/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.graph
final class ModuleModel private (
  val text: String,
  val children: Vector[sbt.internal.graph.ModuleModel]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ModuleModel => (this.text == x.text) && (this.children == x.children)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.graph.ModuleModel".##) + text.##) + children.##)
  }
  override def toString: String = {
    "ModuleModel(" + text + ", " + children + ")"
  }
  private[this] def copy(text: String = text, children: Vector[sbt.internal.graph.ModuleModel] = children): ModuleModel = {
    new ModuleModel(text, children)
  }
  def withText(text: String): ModuleModel = {
    copy(text = text)
  }
  def withChildren(children: Vector[sbt.internal.graph.ModuleModel]): ModuleModel = {
    copy(children = children)
  }
}
object ModuleModel {
  
  def apply(text: String, children: Vector[sbt.internal.graph.ModuleModel]): ModuleModel = new ModuleModel(text, children)
}
