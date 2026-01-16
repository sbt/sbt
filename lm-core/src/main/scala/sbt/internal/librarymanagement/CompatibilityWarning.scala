package sbt.internal.librarymanagement

import sbt.librarymanagement.*
import sbt.util.{ Level, Logger }

import Configurations.*

final class CompatibilityWarningOptions private[sbt] (
    val configurations: Seq[Configuration],
    val level: Level.Value
)

object CompatibilityWarningOptions {
  def default: CompatibilityWarningOptions =
    apply(configurations = List(Compile, Runtime), level = Level.Warn)
  def apply(
      configurations: List[Configuration],
      level: Level.Value
  ): CompatibilityWarningOptions =
    new CompatibilityWarningOptions(
      configurations = configurations,
      level = level
    )
}

private[sbt] object CompatibilityWarning {
  def run(
      config: CompatibilityWarningOptions,
      module: ModuleDescriptor,
      mavenStyle: Boolean,
      log: Logger
  ): Unit = {
    if (mavenStyle) {
      processIntransitive(config, module, log)
    }
  }
  def processIntransitive(
      config: CompatibilityWarningOptions,
      module: ModuleDescriptor,
      log: Logger
  ): Unit = {
    val monitoredConfigsStr: Set[String] = (config.configurations map { _.name }).toSet
    def inMonitoredConfigs(configOpt: Option[String]): Boolean =
      configOpt match {
        case Some(c) => (c.split(",").toSet intersect monitoredConfigsStr).nonEmpty
        case None    => monitoredConfigsStr contains "compile"
      }
    module.directDependencies foreach { m =>
      if (!m.isTransitive && inMonitoredConfigs(m.configurations)) {
        log.warn(
          s"""Found intransitive dependency ($m) while publishMavenStyle is true, but Maven repositories
             |  do not support intransitive dependencies. Use exclusions instead so transitive dependencies
             |  will be correctly excluded in dependent projects.
           """.stripMargin
        )
      } else ()
    }
  }
}
