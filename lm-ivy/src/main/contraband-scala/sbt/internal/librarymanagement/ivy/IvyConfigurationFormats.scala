/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement.ivy

import _root_.sjsonnew.JsonFormat
trait IvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat & sbt.internal.librarymanagement.formats.LoggerFormat & sbt.internal.librarymanagement.ivy.formats.UpdateOptionsFormat & sbt.librarymanagement.IvyPathsFormats & sbt.librarymanagement.ResolverFormats & sbt.librarymanagement.ModuleConfigurationFormats & sjsonnew.BasicJsonProtocol & sbt.internal.librarymanagement.ivy.InlineIvyConfigurationFormats & sbt.internal.librarymanagement.ivy.ExternalIvyConfigurationFormats =>
given IvyConfigurationFormat: JsonFormat[sbt.internal.librarymanagement.ivy.IvyConfiguration] = flatUnionFormat2[sbt.internal.librarymanagement.ivy.IvyConfiguration, sbt.internal.librarymanagement.ivy.InlineIvyConfiguration, sbt.internal.librarymanagement.ivy.ExternalIvyConfiguration]("type")
}
