/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
final class JsonRpcResponseError private (
  /** A number indicating the error type that occurred. */
  val code: Long,
  /** A string providing a short description of the error. */
  val message: String,
  /**
   * A Primitive or Structured value that contains additional
   * information about the error. Can be omitted.
   */
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: JsonRpcResponseError => (this.code == x.code) && (this.message == x.message) && (this.data == x.data)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.JsonRpcResponseError".##) + code.##) + message.##) + data.##)
  }
  override def toString: String = {
    s"""JsonRpcResponseError($code, $message, ${sbt.protocol.Serialization.compactPrintJsonOpt(data)})"""
  }
  protected[this] def copy(code: Long = code, message: String = message, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): JsonRpcResponseError = {
    new JsonRpcResponseError(code, message, data)
  }
  def withCode(code: Long): JsonRpcResponseError = {
    copy(code = code)
  }
  def withMessage(message: String): JsonRpcResponseError = {
    copy(message = message)
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): JsonRpcResponseError = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): JsonRpcResponseError = {
    copy(data = Option(data))
  }
}
object JsonRpcResponseError {
  
  def apply(code: Long, message: String, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): JsonRpcResponseError = new JsonRpcResponseError(code, message, data)
  def apply(code: Long, message: String, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): JsonRpcResponseError = new JsonRpcResponseError(code, message, Option(data))
}
