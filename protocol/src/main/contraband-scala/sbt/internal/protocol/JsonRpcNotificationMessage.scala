/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
final class JsonRpcNotificationMessage private (
  jsonrpc: String,
  /** The method to be invoked. */
  val method: String,
  /** The method's params. */
  val params: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends sbt.internal.protocol.JsonRpcMessage(jsonrpc) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: JsonRpcNotificationMessage => (this.jsonrpc == x.jsonrpc) && (this.method == x.method) && (this.params == x.params)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.JsonRpcNotificationMessage".##) + jsonrpc.##) + method.##) + params.##)
  }
  override def toString: String = {
    s"""JsonRpcNotificationMessage($jsonrpc, $method, ${sbt.protocol.Serialization.compactPrintJsonOpt(params)})"""
  }
  protected[this] def copy(jsonrpc: String = jsonrpc, method: String = method, params: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = params): JsonRpcNotificationMessage = {
    new JsonRpcNotificationMessage(jsonrpc, method, params)
  }
  def withJsonrpc(jsonrpc: String): JsonRpcNotificationMessage = {
    copy(jsonrpc = jsonrpc)
  }
  def withMethod(method: String): JsonRpcNotificationMessage = {
    copy(method = method)
  }
  def withParams(params: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): JsonRpcNotificationMessage = {
    copy(params = params)
  }
  def withParams(params: sjsonnew.shaded.scalajson.ast.unsafe.JValue): JsonRpcNotificationMessage = {
    copy(params = Option(params))
  }
}
object JsonRpcNotificationMessage {
  
  def apply(jsonrpc: String, method: String, params: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): JsonRpcNotificationMessage = new JsonRpcNotificationMessage(jsonrpc, method, params)
  def apply(jsonrpc: String, method: String, params: sjsonnew.shaded.scalajson.ast.unsafe.JValue): JsonRpcNotificationMessage = new JsonRpcNotificationMessage(jsonrpc, method, Option(params))
}
