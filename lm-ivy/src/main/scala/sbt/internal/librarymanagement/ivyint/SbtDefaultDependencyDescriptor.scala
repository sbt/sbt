package sbt.internal.librarymanagement
package ivyint

import org.apache.ivy.core
import core.module.descriptor.DefaultDependencyDescriptor
import sbt.librarymanagement._

trait SbtDefaultDependencyDescriptor { self: DefaultDependencyDescriptor =>
  def dependencyModuleId: ModuleID
}
