/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */
package sbt.internal.protocol.codec
trait JsonRPCProtocol
    extends sbt.internal.util.codec.JValueFormats
    with sjsonnew.BasicJsonProtocol
    with sbt.internal.protocol.codec.JsonRpcRequestMessageFormats
    with sbt.internal.protocol.codec.JsonRpcResponseErrorFormats
    with sbt.internal.protocol.codec.JsonRpcResponseMessageFormats
    with sbt.internal.protocol.codec.JsonRpcNotificationMessageFormats

object JsonRPCProtocol extends JsonRPCProtocol
