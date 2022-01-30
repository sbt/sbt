/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait ModuleSettingsFormats { self: sbt.librarymanagement.ScalaModuleInfoFormats with sbt.librarymanagement.ConfigurationFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.IvyFileConfigurationFormats with sbt.librarymanagement.PomConfigurationFormats with sbt.librarymanagement.ModuleIDFormats with sbt.librarymanagement.ArtifactFormats with sbt.librarymanagement.ConfigRefFormats with sbt.librarymanagement.ChecksumFormats with sbt.librarymanagement.InclExclRuleFormats with sbt.librarymanagement.CrossVersionFormats with sbt.librarymanagement.DisabledFormats with sbt.librarymanagement.BinaryFormats with sbt.librarymanagement.ConstantFormats with sbt.librarymanagement.PatchFormats with sbt.librarymanagement.FullFormats with sbt.librarymanagement.For3Use2_13Formats with sbt.librarymanagement.For2_13Use3Formats with sbt.librarymanagement.ModuleInfoFormats with sbt.librarymanagement.ScmInfoFormats with sbt.librarymanagement.DeveloperFormats with sbt.internal.librarymanagement.formats.NodeSeqFormat with sbt.librarymanagement.ConflictManagerFormats with sbt.librarymanagement.ModuleDescriptorConfigurationFormats =>
implicit lazy val ModuleSettingsFormat: JsonFormat[sbt.librarymanagement.ModuleSettings] = flatUnionFormat3[sbt.librarymanagement.ModuleSettings, sbt.librarymanagement.IvyFileConfiguration, sbt.librarymanagement.PomConfiguration, sbt.librarymanagement.ModuleDescriptorConfiguration]("type")
}
