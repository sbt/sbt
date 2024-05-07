/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Represents a related message and source code location for a diagnostic.
 * This should be used to point to code locations that cause or are related to
 * a diagnostics, e.g when duplicating a symbol in a scope.
 * @param location The location of this related diagnostic information.
 * @param message The message of this related diagnostic information.
 */
final class DiagnosticRelatedInformation private (
  val location: sbt.internal.bsp.Location,
  val message: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: DiagnosticRelatedInformation => (this.location == x.location) && (this.message == x.message)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.DiagnosticRelatedInformation".##) + location.##) + message.##)
  }
  override def toString: String = {
    "DiagnosticRelatedInformation(" + location + ", " + message + ")"
  }
  private[this] def copy(location: sbt.internal.bsp.Location = location, message: String = message): DiagnosticRelatedInformation = {
    new DiagnosticRelatedInformation(location, message)
  }
  def withLocation(location: sbt.internal.bsp.Location): DiagnosticRelatedInformation = {
    copy(location = location)
  }
  def withMessage(message: String): DiagnosticRelatedInformation = {
    copy(message = message)
  }
}
object DiagnosticRelatedInformation {
  
  def apply(location: sbt.internal.bsp.Location, message: String): DiagnosticRelatedInformation = new DiagnosticRelatedInformation(location, message)
}
