/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy

import _root_.sjsonnew.JsonFormat
trait IvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat & sbt.internal.librarymanagement.formats.LoggerFormat & sbt.librarymanagement.ivy.formats.UpdateOptionsFormat & sbt.librarymanagement.ivy.IvyPathsFormats & sjsonnew.BasicJsonProtocol & sbt.librarymanagement.ResolverFormats & sbt.librarymanagement.ModuleConfigurationFormats & sbt.librarymanagement.ivy.InlineIvyConfigurationFormats & sbt.librarymanagement.ivy.ExternalIvyConfigurationFormats =>
implicit lazy val IvyConfigurationFormat: JsonFormat[sbt.librarymanagement.ivy.IvyConfiguration] = flatUnionFormat2[sbt.librarymanagement.ivy.IvyConfiguration, sbt.librarymanagement.ivy.InlineIvyConfiguration, sbt.librarymanagement.ivy.ExternalIvyConfiguration]("type")
}
