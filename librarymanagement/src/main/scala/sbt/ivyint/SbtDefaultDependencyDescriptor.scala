package sbt
package ivyint

import org.apache.ivy.core
import core.module.descriptor.DefaultDependencyDescriptor

trait SbtDefaultDependencyDescriptor { self: DefaultDependencyDescriptor =>
  def dependencyModuleId: ModuleID
}
