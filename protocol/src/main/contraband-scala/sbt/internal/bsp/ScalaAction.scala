/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * A Scala action represents a change that can be performed in code.
 * See also LSP: Code Action Request (https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction).
 * @param title A short, human-readable, title for this code action.
 * @param description A description that may be shown to the user client side to explain the action.
 * @param edit The workspace edit this code action performs.
 */
final class ScalaAction private (
  val title: String,
  val description: Option[String],
  val edit: Option[sbt.internal.bsp.ScalaWorkspaceEdit]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaAction => (this.title == x.title) && (this.description == x.description) && (this.edit == x.edit)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaAction".##) + title.##) + description.##) + edit.##)
  }
  override def toString: String = {
    "ScalaAction(" + title + ", " + description + ", " + edit + ")"
  }
  private[this] def copy(title: String = title, description: Option[String] = description, edit: Option[sbt.internal.bsp.ScalaWorkspaceEdit] = edit): ScalaAction = {
    new ScalaAction(title, description, edit)
  }
  def withTitle(title: String): ScalaAction = {
    copy(title = title)
  }
  def withDescription(description: Option[String]): ScalaAction = {
    copy(description = description)
  }
  def withDescription(description: String): ScalaAction = {
    copy(description = Option(description))
  }
  def withEdit(edit: Option[sbt.internal.bsp.ScalaWorkspaceEdit]): ScalaAction = {
    copy(edit = edit)
  }
  def withEdit(edit: sbt.internal.bsp.ScalaWorkspaceEdit): ScalaAction = {
    copy(edit = Option(edit))
  }
}
object ScalaAction {
  
  def apply(title: String, description: Option[String], edit: Option[sbt.internal.bsp.ScalaWorkspaceEdit]): ScalaAction = new ScalaAction(title, description, edit)
  def apply(title: String, description: String, edit: sbt.internal.bsp.ScalaWorkspaceEdit): ScalaAction = new ScalaAction(title, Option(description), Option(edit))
}
