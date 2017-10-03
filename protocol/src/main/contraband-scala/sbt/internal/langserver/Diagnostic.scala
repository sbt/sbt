/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/**
 * Represents a diagnostic, such as a compiler error or warning.
 * Diagnostic objects are only valid in the scope of a resource.
 */
final class Diagnostic private (
  /** The range at which the message applies. */
  val range: sbt.internal.langserver.Range,
  /**
   * The diagnostic's severity. Can be omitted. If omitted it is up to the
   * client to interpret diagnostics as error, warning, info or hint.
   */
  val severity: Option[Long],
  /** The diagnostic's code. Can be omitted. */
  val code: Option[String],
  /**
   * A human-readable string describing the source of this
   * diagnostic, e.g. 'typescript' or 'super lint'.
   */
  val source: Option[String],
  /** The diagnostic's message. */
  val message: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Diagnostic => (this.range == x.range) && (this.severity == x.severity) && (this.code == x.code) && (this.source == x.source) && (this.message == x.message)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.langserver.Diagnostic".##) + range.##) + severity.##) + code.##) + source.##) + message.##)
  }
  override def toString: String = {
    "Diagnostic(" + range + ", " + severity + ", " + code + ", " + source + ", " + message + ")"
  }
  protected[this] def copy(range: sbt.internal.langserver.Range = range, severity: Option[Long] = severity, code: Option[String] = code, source: Option[String] = source, message: String = message): Diagnostic = {
    new Diagnostic(range, severity, code, source, message)
  }
  def withRange(range: sbt.internal.langserver.Range): Diagnostic = {
    copy(range = range)
  }
  def withSeverity(severity: Option[Long]): Diagnostic = {
    copy(severity = severity)
  }
  def withSeverity(severity: Long): Diagnostic = {
    copy(severity = Option(severity))
  }
  def withCode(code: Option[String]): Diagnostic = {
    copy(code = code)
  }
  def withCode(code: String): Diagnostic = {
    copy(code = Option(code))
  }
  def withSource(source: Option[String]): Diagnostic = {
    copy(source = source)
  }
  def withSource(source: String): Diagnostic = {
    copy(source = Option(source))
  }
  def withMessage(message: String): Diagnostic = {
    copy(message = message)
  }
}
object Diagnostic {
  
  def apply(range: sbt.internal.langserver.Range, severity: Option[Long], code: Option[String], source: Option[String], message: String): Diagnostic = new Diagnostic(range, severity, code, source, message)
  def apply(range: sbt.internal.langserver.Range, severity: Long, code: String, source: String, message: String): Diagnostic = new Diagnostic(range, Option(severity), Option(code), Option(source), message)
}
