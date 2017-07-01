/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait CrossVersionFormats { self: sjsonnew.BasicJsonProtocol with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats =>
implicit lazy val CrossVersionFormat: JsonFormat[sbt.librarymanagement.CrossVersion] = flatUnionFormat5[sbt.librarymanagement.CrossVersion, sbt.librarymanagement.Disabled, sbt.librarymanagement.Binary, sbt.librarymanagement.Constant, sbt.librarymanagement.Patch, sbt.librarymanagement.Full]("type")
}
