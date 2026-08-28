package sbt.internal.librarymanagement

import sbt.librarymanagement.ModuleSettings

/**
 * for sbt-native-packager compatibility
 * [[https://github.com/sbt/sbt/issues/9676]]
 */
@deprecated("will be removed", "2.1.0")
private[librarymanagement] final class IvySbt {
  final class Module(rawModuleSettings: ModuleSettings)
}
