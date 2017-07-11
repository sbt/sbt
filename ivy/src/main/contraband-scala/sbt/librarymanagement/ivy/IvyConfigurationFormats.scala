/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy

import _root_.sjsonnew.JsonFormat
trait IvyConfigurationFormats { self: sbt.internal.librarymanagement.formats.GlobalLockFormat with sbt.internal.librarymanagement.formats.LoggerFormat with sbt.librarymanagement.ivy.formats.UpdateOptionsFormat with sbt.librarymanagement.ivy.IvyPathsFormats with sbt.librarymanagement.ResolverFormats with sbt.librarymanagement.ModuleConfigurationFormats with sjsonnew.BasicJsonProtocol with sbt.librarymanagement.ivy.InlineIvyConfigurationFormats with sbt.librarymanagement.ivy.ExternalIvyConfigurationFormats =>
implicit lazy val IvyConfigurationFormat: JsonFormat[sbt.librarymanagement.ivy.IvyConfiguration] = flatUnionFormat2[sbt.librarymanagement.ivy.IvyConfiguration, sbt.librarymanagement.ivy.InlineIvyConfiguration, sbt.librarymanagement.ivy.ExternalIvyConfiguration]("type")
}
