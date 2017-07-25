/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec

import _root_.sjsonnew.JsonFormat
trait SettingQueryResponseFormats { self: sbt.internal.util.codec.JValueFormats with sjsonnew.BasicJsonProtocol with sbt.internal.protocol.codec.SettingQuerySuccessFormats with sbt.internal.protocol.codec.SettingQueryFailureFormats =>
implicit lazy val SettingQueryResponseFormat: JsonFormat[sbt.internal.protocol.SettingQueryResponse] = flatUnionFormat2[sbt.internal.protocol.SettingQueryResponse, sbt.internal.protocol.SettingQuerySuccess, sbt.internal.protocol.SettingQueryFailure]("type")
}
