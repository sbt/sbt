package sbt.lsp

object ErrorCodes {
  // Defined by JSON RPC
  val ParseError: Long = -32700
  val InvalidRequest: Long = -32600
  val MethodNotFound: Long = -32601
  val InvalidParams: Long = -32602
  val InternalError: Long = -32603
  val serverErrorStart: Long = -32099
  val serverErrorEnd: Long = -32000
  val ServerNotInitialized: Long = -32002
  val UnknownErrorCode: Long = -32001

  // Defined by the protocol.
  val RequestCancelled: Long = -32800
}
