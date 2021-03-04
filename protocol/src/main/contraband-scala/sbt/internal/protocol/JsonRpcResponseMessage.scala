/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
/**
 * @param id The request id.
 * @param result The result of a request. This can be omitted in
                 the case of an error.
 * @param error The error object in case a request fails.
 */
final class JsonRpcResponseMessage private (
  jsonrpc: String,
  val id: String,
  val result: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue],
  val error: Option[sbt.internal.protocol.JsonRpcResponseError]) extends sbt.internal.protocol.JsonRpcMessage(jsonrpc) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JsonRpcResponseMessage => (this.jsonrpc == x.jsonrpc) && (this.id == x.id) && (this.result == x.result) && (this.error == x.error)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.JsonRpcResponseMessage".##) + jsonrpc.##) + id.##) + result.##) + error.##)
  }
  override def toString: String = {
    s"""JsonRpcResponseMessage($jsonrpc, $id, ${sbt.protocol.Serialization.compactPrintJsonOpt(result)}, $error)"""
  }
  private[this] def copy(jsonrpc: String = jsonrpc, id: String = id, result: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = result, error: Option[sbt.internal.protocol.JsonRpcResponseError] = error): JsonRpcResponseMessage = {
    new JsonRpcResponseMessage(jsonrpc, id, result, error)
  }
  def withJsonrpc(jsonrpc: String): JsonRpcResponseMessage = {
    copy(jsonrpc = jsonrpc)
  }
  def withId(id: String): JsonRpcResponseMessage = {
    copy(id = id)
  }
  def withResult(result: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): JsonRpcResponseMessage = {
    copy(result = result)
  }
  def withResult(result: sjsonnew.shaded.scalajson.ast.unsafe.JValue): JsonRpcResponseMessage = {
    copy(result = Option(result))
  }
  def withError(error: Option[sbt.internal.protocol.JsonRpcResponseError]): JsonRpcResponseMessage = {
    copy(error = error)
  }
  def withError(error: sbt.internal.protocol.JsonRpcResponseError): JsonRpcResponseMessage = {
    copy(error = Option(error))
  }
}
object JsonRpcResponseMessage {
  
  def apply(jsonrpc: String, id: String, result: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue], error: Option[sbt.internal.protocol.JsonRpcResponseError]): JsonRpcResponseMessage = new JsonRpcResponseMessage(jsonrpc, id, result, error)
  def apply(jsonrpc: String, id: String, result: sjsonnew.shaded.scalajson.ast.unsafe.JValue, error: sbt.internal.protocol.JsonRpcResponseError): JsonRpcResponseMessage = new JsonRpcResponseMessage(jsonrpc, id, Option(result), Option(error))
}
