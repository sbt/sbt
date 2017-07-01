/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait ModuleSettingsFormats { self: sbt.librarymanagement.IvyScalaFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.IvyFileConfigurationFormats with sbt.librarymanagement.PomConfigurationFormats with sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ModuleInfoFormats with sbt.librarymanagement.InclExclRuleFormats with sbt.internal.librarymanagement.formats.NodeSeqFormat with sbt.librarymanagement.ConfigurationFormats with sbt.librarymanagement.ConflictManagerFormats with sbt.librarymanagement.InlineConfigurationFormats =>
implicit lazy val ModuleSettingsFormat: JsonFormat[sbt.librarymanagement.ModuleSettings] = flatUnionFormat3[sbt.librarymanagement.ModuleSettings, sbt.librarymanagement.IvyFileConfiguration, sbt.librarymanagement.PomConfiguration, sbt.internal.librarymanagement.InlineConfiguration]("type")
}
