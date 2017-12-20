/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class TextDocumentSyncOptions private (
  val openClose: Option[Boolean],
  val change: Option[Long],
  val willSave: Option[Boolean],
  val willSaveWaitUntil: Option[Boolean],
  val save: Option[sbt.internal.langserver.SaveOptions]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TextDocumentSyncOptions => (this.openClose == x.openClose) && (this.change == x.change) && (this.willSave == x.willSave) && (this.willSaveWaitUntil == x.willSaveWaitUntil) && (this.save == x.save)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.langserver.TextDocumentSyncOptions".##) + openClose.##) + change.##) + willSave.##) + willSaveWaitUntil.##) + save.##)
  }
  override def toString: String = {
    "TextDocumentSyncOptions(" + openClose + ", " + change + ", " + willSave + ", " + willSaveWaitUntil + ", " + save + ")"
  }
  protected[this] def copy(openClose: Option[Boolean] = openClose, change: Option[Long] = change, willSave: Option[Boolean] = willSave, willSaveWaitUntil: Option[Boolean] = willSaveWaitUntil, save: Option[sbt.internal.langserver.SaveOptions] = save): TextDocumentSyncOptions = {
    new TextDocumentSyncOptions(openClose, change, willSave, willSaveWaitUntil, save)
  }
  def withOpenClose(openClose: Option[Boolean]): TextDocumentSyncOptions = {
    copy(openClose = openClose)
  }
  def withOpenClose(openClose: Boolean): TextDocumentSyncOptions = {
    copy(openClose = Option(openClose))
  }
  def withChange(change: Option[Long]): TextDocumentSyncOptions = {
    copy(change = change)
  }
  def withChange(change: Long): TextDocumentSyncOptions = {
    copy(change = Option(change))
  }
  def withWillSave(willSave: Option[Boolean]): TextDocumentSyncOptions = {
    copy(willSave = willSave)
  }
  def withWillSave(willSave: Boolean): TextDocumentSyncOptions = {
    copy(willSave = Option(willSave))
  }
  def withWillSaveWaitUntil(willSaveWaitUntil: Option[Boolean]): TextDocumentSyncOptions = {
    copy(willSaveWaitUntil = willSaveWaitUntil)
  }
  def withWillSaveWaitUntil(willSaveWaitUntil: Boolean): TextDocumentSyncOptions = {
    copy(willSaveWaitUntil = Option(willSaveWaitUntil))
  }
  def withSave(save: Option[sbt.internal.langserver.SaveOptions]): TextDocumentSyncOptions = {
    copy(save = save)
  }
  def withSave(save: sbt.internal.langserver.SaveOptions): TextDocumentSyncOptions = {
    copy(save = Option(save))
  }
}
object TextDocumentSyncOptions {
  
  def apply(openClose: Option[Boolean], change: Option[Long], willSave: Option[Boolean], willSaveWaitUntil: Option[Boolean], save: Option[sbt.internal.langserver.SaveOptions]): TextDocumentSyncOptions = new TextDocumentSyncOptions(openClose, change, willSave, willSaveWaitUntil, save)
  def apply(openClose: Boolean, change: Long, willSave: Boolean, willSaveWaitUntil: Boolean, save: sbt.internal.langserver.SaveOptions): TextDocumentSyncOptions = new TextDocumentSyncOptions(Option(openClose), Option(change), Option(willSave), Option(willSaveWaitUntil), Option(save))
}
