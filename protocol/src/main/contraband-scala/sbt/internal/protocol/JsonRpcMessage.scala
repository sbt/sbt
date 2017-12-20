/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
/** A general message as defined by JSON-RPC. */
abstract class JsonRpcMessage(
  val jsonrpc: String) extends Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: JsonRpcMessage => (this.jsonrpc == x.jsonrpc)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.protocol.JsonRpcMessage".##) + jsonrpc.##)
  }
  override def toString: String = {
    "JsonRpcMessage(" + jsonrpc + ")"
  }
}
object JsonRpcMessage {
  
}
