/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Represents a diagnostic, such as a compiler error or warning.
 * Diagnostic objects are only valid in the scope of a resource.
 * @param range The range at which the message applies.
 * @param severity The diagnostic's severity. Can be omitted. If omitted it is up to the
                   client to interpret diagnostics as error, warning, info or hint.
 * @param code The diagnostic's code. Can be omitted.
 * @param source A human-readable string describing the source of this
                 diagnostic, e.g. 'typescript' or 'super lint'.
 * @param message The diagnostic's message.
 * @param relatedInformation A list of related diagnostic information, e.g. when symbol-names within
                             a scope collide all definitions can be marked via this property.
 * @param dataKind Kind of data to expect in the `data` field. If this field is not set,
                   the kind of data is not specified.
 * @param data A data entry field.
 */
final class Diagnostic private (
  val range: sbt.internal.bsp.Range,
  val severity: Option[Long],
  val code: Option[String],
  val source: Option[String],
  val message: String,
  val relatedInformation: Vector[sbt.internal.bsp.DiagnosticRelatedInformation],
  val dataKind: Option[String],
  val data: Option[sbt.internal.bsp.ScalaDiagnostic]) extends Serializable {
  
  private def this(range: sbt.internal.bsp.Range, severity: Option[Long], code: Option[String], source: Option[String], message: String) = this(range, severity, code, source, message, Vector(), None, None)
  private def this(range: sbt.internal.bsp.Range, severity: Option[Long], code: Option[String], source: Option[String], message: String, relatedInformation: Vector[sbt.internal.bsp.DiagnosticRelatedInformation]) = this(range, severity, code, source, message, relatedInformation, None, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: Diagnostic => (this.range == x.range) && (this.severity == x.severity) && (this.code == x.code) && (this.source == x.source) && (this.message == x.message) && (this.relatedInformation == x.relatedInformation) && (this.dataKind == x.dataKind) && (this.data == x.data)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.Diagnostic".##) + range.##) + severity.##) + code.##) + source.##) + message.##) + relatedInformation.##) + dataKind.##) + data.##)
  }
  override def toString: String = {
    "Diagnostic(" + range + ", " + severity + ", " + code + ", " + source + ", " + message + ", " + relatedInformation + ", " + dataKind + ", " + data + ")"
  }
  private def copy(range: sbt.internal.bsp.Range = range, severity: Option[Long] = severity, code: Option[String] = code, source: Option[String] = source, message: String = message, relatedInformation: Vector[sbt.internal.bsp.DiagnosticRelatedInformation] = relatedInformation, dataKind: Option[String] = dataKind, data: Option[sbt.internal.bsp.ScalaDiagnostic] = data): Diagnostic = {
    new Diagnostic(range, severity, code, source, message, relatedInformation, dataKind, data)
  }
  def withRange(range: sbt.internal.bsp.Range): Diagnostic = {
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
  def withRelatedInformation(relatedInformation: Vector[sbt.internal.bsp.DiagnosticRelatedInformation]): Diagnostic = {
    copy(relatedInformation = relatedInformation)
  }
  def withDataKind(dataKind: Option[String]): Diagnostic = {
    copy(dataKind = dataKind)
  }
  def withDataKind(dataKind: String): Diagnostic = {
    copy(dataKind = Option(dataKind))
  }
  def withData(data: Option[sbt.internal.bsp.ScalaDiagnostic]): Diagnostic = {
    copy(data = data)
  }
  def withData(data: sbt.internal.bsp.ScalaDiagnostic): Diagnostic = {
    copy(data = Option(data))
  }
}
object Diagnostic {
  
  def apply(range: sbt.internal.bsp.Range, severity: Option[Long], code: Option[String], source: Option[String], message: String): Diagnostic = new Diagnostic(range, severity, code, source, message)
  def apply(range: sbt.internal.bsp.Range, severity: Long, code: String, source: String, message: String): Diagnostic = new Diagnostic(range, Option(severity), Option(code), Option(source), message)
  def apply(range: sbt.internal.bsp.Range, severity: Option[Long], code: Option[String], source: Option[String], message: String, relatedInformation: Vector[sbt.internal.bsp.DiagnosticRelatedInformation]): Diagnostic = new Diagnostic(range, severity, code, source, message, relatedInformation)
  def apply(range: sbt.internal.bsp.Range, severity: Long, code: String, source: String, message: String, relatedInformation: Vector[sbt.internal.bsp.DiagnosticRelatedInformation]): Diagnostic = new Diagnostic(range, Option(severity), Option(code), Option(source), message, relatedInformation)
  def apply(range: sbt.internal.bsp.Range, severity: Option[Long], code: Option[String], source: Option[String], message: String, relatedInformation: Vector[sbt.internal.bsp.DiagnosticRelatedInformation], dataKind: Option[String], data: Option[sbt.internal.bsp.ScalaDiagnostic]): Diagnostic = new Diagnostic(range, severity, code, source, message, relatedInformation, dataKind, data)
  def apply(range: sbt.internal.bsp.Range, severity: Long, code: String, source: String, message: String, relatedInformation: Vector[sbt.internal.bsp.DiagnosticRelatedInformation], dataKind: String, data: sbt.internal.bsp.ScalaDiagnostic): Diagnostic = new Diagnostic(range, Option(severity), Option(code), Option(source), message, relatedInformation, Option(dataKind), Option(data))
}
