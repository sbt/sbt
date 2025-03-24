/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec

import _root_.sjsonnew.JsonFormat
trait TextDocumentPositionParamsInterfaceFormats { self: sbt.internal.langserver.codec.TextDocumentIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.langserver.codec.PositionFormats & sbt.internal.langserver.codec.TextDocumentPositionParamsFormats =>
implicit lazy val TextDocumentPositionParamsInterfaceFormat: JsonFormat[sbt.internal.langserver.TextDocumentPositionParamsInterface] = flatUnionFormat1[sbt.internal.langserver.TextDocumentPositionParamsInterface, sbt.internal.langserver.TextDocumentPositionParams]("type")
}
