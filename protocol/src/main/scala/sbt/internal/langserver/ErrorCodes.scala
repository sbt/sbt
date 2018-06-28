/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package langserver

/** Holds the error codes for the LSP implementation here. */
object ErrorCodes {
  // this is essentially a lookup table encoded in Scala,
  // so heavy usage of vertical alignment is beneficial
  // format: off

  // Defined by the JSON-RPC 2.0 Specification
  // http://www.jsonrpc.org/specification#error_object
  //
  // The error codes from and including -32768 to -32000 are reserved for pre-defined errors.
  // Any code within this range, but not defined explicitly below is reserved for future use.
  //
  // The error codes are nearly the same as those suggested for XML-RPC at the following url:
  // http://xmlrpc-epi.sourceforge.net/specs/rfc.fault_codes.php
  //
  val ParseError     = -32700L       // Invalid JSON was received by the server.
                                     // An error occurred on the server while parsing the JSON text.
  val InvalidRequest = -32600L       // The JSON sent is not a valid Request object.
  val MethodNotFound = -32601L       // The method does not exist / is not available.
  val InvalidParams  = -32602L       // Invalid method parameter(s).
  val InternalError  = -32603L       // Internal JSON-RPC error.


  // The range -32000 to -32099 are reserved for implementation-defined server-errors.
  val serverErrorStart = -32099L     // from LSP's spec code snippet
  val serverErrorEnd   = -32000L     // from LSP's spec code snippet

  val UnknownServerError   = -32001L // Defined by LSP
  val ServerNotInitialized = -32002L // Defined by LSP


  // The remainder of the space is available for application defined errors.
  val RequestCancelled = -32800L     // Defined by LSP
  val UnknownError     = -33000L     // A generic error, unknown if the user or server is at fault.

  // format: on
}
