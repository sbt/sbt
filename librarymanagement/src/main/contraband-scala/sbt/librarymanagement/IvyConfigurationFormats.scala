/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait IvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat with sbt.internal.librarymanagement.formats.LoggerFormat with sbt.internal.librarymanagement.formats.UpdateOptionsFormat with sbt.librarymanagement.IvyPathsFormats with sbt.librarymanagement.ResolverFormats with sbt.librarymanagement.ModuleConfigurationFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.InlineIvyConfigurationFormats with sbt.librarymanagement.ExternalIvyConfigurationFormats =>
implicit lazy val IvyConfigurationFormat: JsonFormat[sbt.internal.librarymanagement.IvyConfiguration] = flatUnionFormat2[sbt.internal.librarymanagement.IvyConfiguration, sbt.internal.librarymanagement.InlineIvyConfiguration, sbt.internal.librarymanagement.ExternalIvyConfiguration]("type")
}
