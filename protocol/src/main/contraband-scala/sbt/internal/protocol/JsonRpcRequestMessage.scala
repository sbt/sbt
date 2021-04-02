/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
/**
 * @param id The request id.
 * @param method The method to be invoked.
 * @param params The method's params.
 */
final class JsonRpcRequestMessage private (
  jsonrpc: String,
  val id: String,
  val method: String,
  val params: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends sbt.internal.protocol.JsonRpcMessage(jsonrpc) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JsonRpcRequestMessage => (this.jsonrpc == x.jsonrpc) && (this.id == x.id) && (this.method == x.method) && (this.params == x.params)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.protocol.JsonRpcRequestMessage".##) + jsonrpc.##) + id.##) + method.##) + params.##)
  }
  override def toString: String = {
    s"""JsonRpcRequestMessage($jsonrpc, $id, $method, ${sbt.protocol.Serialization.compactPrintJsonOpt(params)}})"""
  }
  private[this] def copy(jsonrpc: String = jsonrpc, id: String = id, method: String = method, params: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = params): JsonRpcRequestMessage = {
    new JsonRpcRequestMessage(jsonrpc, id, method, params)
  }
  def withJsonrpc(jsonrpc: String): JsonRpcRequestMessage = {
    copy(jsonrpc = jsonrpc)
  }
  def withId(id: String): JsonRpcRequestMessage = {
    copy(id = id)
  }
  def withMethod(method: String): JsonRpcRequestMessage = {
    copy(method = method)
  }
  def withParams(params: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): JsonRpcRequestMessage = {
    copy(params = params)
  }
  def withParams(params: sjsonnew.shaded.scalajson.ast.unsafe.JValue): JsonRpcRequestMessage = {
    copy(params = Option(params))
  }
}
object JsonRpcRequestMessage {
  
  def apply(jsonrpc: String, id: String, method: String, params: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): JsonRpcRequestMessage = new JsonRpcRequestMessage(jsonrpc, id, method, params)
  def apply(jsonrpc: String, id: String, method: String, params: sjsonnew.shaded.scalajson.ast.unsafe.JValue): JsonRpcRequestMessage = new JsonRpcRequestMessage(jsonrpc, id, method, Option(params))
}
