/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait ModuleSettingsFormats { self: sbt.librarymanagement.ScalaModuleInfoFormats & sbt.librarymanagement.ConfigurationFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.IvyFileConfigurationFormats & sbt.librarymanagement.PomConfigurationFormats & sbt.librarymanagement.ModuleIDFormats & sbt.librarymanagement.ArtifactFormats & sbt.librarymanagement.ConfigRefFormats & sbt.librarymanagement.ChecksumFormats & sbt.librarymanagement.InclExclRuleFormats & sbt.librarymanagement.CrossVersionFormats & sbt.librarymanagement.DisabledFormats & sbt.librarymanagement.BinaryFormats & sbt.librarymanagement.ConstantFormats & sbt.librarymanagement.PatchFormats & sbt.librarymanagement.FullFormats & sbt.librarymanagement.For3Use2_13Formats & sbt.librarymanagement.For2_13Use3Formats & sbt.librarymanagement.ModuleInfoFormats & sbt.librarymanagement.LicenseFormats & sbt.librarymanagement.ScmInfoFormats & sbt.librarymanagement.DeveloperFormats & sbt.internal.librarymanagement.formats.NodeSeqFormat & sbt.librarymanagement.ConflictManagerFormats & sbt.librarymanagement.ModuleDescriptorConfigurationFormats =>
given ModuleSettingsFormat: JsonFormat[sbt.librarymanagement.ModuleSettings] = flatUnionFormat3[sbt.librarymanagement.ModuleSettings, sbt.librarymanagement.IvyFileConfiguration, sbt.librarymanagement.PomConfiguration, sbt.librarymanagement.ModuleDescriptorConfiguration]("type")
}
