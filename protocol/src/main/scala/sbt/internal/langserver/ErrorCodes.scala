package sbt
package internal
package langserver

object ErrorCodes {
  // Defined by JSON RPC
  val ParseError = -32700L
  val InvalidRequest = -32600L
  val MethodNotFound = -32601L
  val InvalidParams = -32602L
  val InternalError = -32603L
  val serverErrorStart = -32099L
  val serverErrorEnd = -32000L
  val ServerNotInitialized = -32002L
  val UnknownErrorCode = -32001L

  // Defined by the protocol.
  val RequestCancelled = -32800L
}
