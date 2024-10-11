package sbt.librarymanagement

import java.io.File
import sbt.util.Logger

/**
 * Library management API to publish artifacts.
 */
class Publisher private[sbt] (publisherEngine: PublisherInterface) {

  /**
   * Builds a ModuleDescriptor that describes a subproject with dependencies.
   *
   * @param moduleSetting It contains the information about the module including the dependencies.
   * @return A `ModuleDescriptor` describing a subproject and its dependencies.
   */
  def moduleDescriptor(moduleSetting: ModuleDescriptorConfiguration): ModuleDescriptor =
    publisherEngine.moduleDescriptor(moduleSetting)

  /**
   * Publishes the given module.
   *
   * @param module The module to be published.
   * @param configuration The publish configuration.
   * @param log The logger.
   */
  def publish(module: ModuleDescriptor, configuration: PublishConfiguration, log: Logger): Unit =
    publisherEngine.publish(module, configuration, log)

  /**
   * Makes the `pom.xml` file for the given module.
   *
   * @param module The module for which a `.pom` file is to be created.
   * @param configuration The makePomFile configuration.
   * @param log The logger.
   * @return The `File` containing the POM descriptor.
   */
  def makePomFile(
      module: ModuleDescriptor,
      configuration: MakePomConfiguration,
      log: Logger
  ): File =
    publisherEngine.makePomFile(module, configuration, log)
}

object Publisher {
  def apply(publisherEngine: PublisherInterface): Publisher =
    new Publisher(publisherEngine)
}
