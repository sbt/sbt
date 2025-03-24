/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec

import _root_.sjsonnew.JsonFormat
trait SettingQueryResponseFormats { self: sbt.internal.util.codec.JValueFormats & sjsonnew.BasicJsonProtocol & sbt.protocol.codec.SettingQuerySuccessFormats & sbt.protocol.codec.SettingQueryFailureFormats =>
implicit lazy val SettingQueryResponseFormat: JsonFormat[sbt.protocol.SettingQueryResponse] = flatUnionFormat2[sbt.protocol.SettingQueryResponse, sbt.protocol.SettingQuerySuccess, sbt.protocol.SettingQueryFailure]("type")
}
