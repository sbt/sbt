package sbt
package librarymanagement
package ivy

import sbt.internal.librarymanagement.*
import sbt.util.Logger
import java.io.File

class IvyPublisher private[sbt] (val ivySbt: IvySbt) extends PublisherInterface {
  type Module = ivySbt.Module

  override def moduleDescriptor(moduleSetting: ModuleDescriptorConfiguration): ModuleDescriptor = {
    new Module(moduleSetting)
  }

  override def makePomFile(
      module: ModuleDescriptor,
      configuration: MakePomConfiguration,
      log: Logger
  ): File =
    IvyActions.makePomFile(toModule(module), configuration, log)

  override def publish(
      module: ModuleDescriptor,
      configuration: PublishConfiguration,
      log: Logger
  ): Unit =
    IvyActions.publish(toModule(module), configuration, log)

  private[sbt] def toModule(module: ModuleDescriptor): Module =
    module.asInstanceOf[Module]
}

object IvyPublisher {
  def apply(ivyConfiguration: IvyConfiguration): Publisher =
    Publisher(new IvyPublisher(new IvySbt(ivyConfiguration)))
}
