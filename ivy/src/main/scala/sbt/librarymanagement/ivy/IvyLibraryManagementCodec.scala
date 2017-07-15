package sbt.librarymanagement
package ivy

trait IvyLibraryManagementCodec
    extends sjsonnew.BasicJsonProtocol
    with LibraryManagementCodec
    with sbt.internal.librarymanagement.formats.GlobalLockFormat
    with sbt.internal.librarymanagement.formats.LoggerFormat
    with sbt.librarymanagement.ivy.formats.UpdateOptionsFormat
    with IvyPathsFormats
    with ResolverFormats
    with ModuleConfigurationFormats
    with InlineIvyConfigurationFormats
    with ExternalIvyConfigurationFormats
    with IvyConfigurationFormats

object IvyLibraryManagementCodec extends IvyLibraryManagementCodec
