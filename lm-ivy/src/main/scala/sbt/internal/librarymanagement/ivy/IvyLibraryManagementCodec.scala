package sbt.internal.librarymanagement
package ivy

trait IvyLibraryManagementCodec
    extends sjsonnew.BasicJsonProtocol
    with sbt.librarymanagement.LibraryManagementCodec
    with sbt.internal.librarymanagement.formats.GlobalLockFormat
    with sbt.internal.librarymanagement.formats.LoggerFormat
    with sbt.internal.librarymanagement.ivy.formats.UpdateOptionsFormat
    with sbt.librarymanagement.IvyPathsFormats
    with sbt.librarymanagement.ResolverFormats
    with sbt.librarymanagement.ModuleConfigurationFormats
    with InlineIvyConfigurationFormats
    with ExternalIvyConfigurationFormats
    with IvyConfigurationFormats

object IvyLibraryManagementCodec extends IvyLibraryManagementCodec
