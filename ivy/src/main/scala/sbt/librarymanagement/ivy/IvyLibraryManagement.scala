package sbt
package librarymanagement
package ivy

import sbt.internal.librarymanagement._
import sbt.util.Logger

class IvyLibraryManagement private[sbt] (val ivySbt: IvySbt) extends LibraryManagementInterface {
  type Module = ivySbt.Module

  override def moduleDescriptor(moduleSetting: ModuleDescriptorConfiguration): ModuleDescriptor = {
    new Module(moduleSetting)
  }

  override def update(module: ModuleDescriptor,
                      configuration: UpdateConfiguration,
                      uwconfig: UnresolvedWarningConfiguration,
                      log: Logger): Either[UnresolvedWarning, UpdateReport] =
    IvyActions.updateEither(toModule(module), configuration, uwconfig, log)

  private[sbt] def toModule(module: ModuleDescriptor): Module =
    module match {
      case m: Module => m
    }
}

object IvyLibraryManagement {
  def apply(ivyConfiguration: IvyConfiguration): LibraryManagement =
    LibraryManagement(new IvyLibraryManagement(new IvySbt(ivyConfiguration)))
}
