package org.apache.ivy.plugins.parser.m2

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor

object Run {
  def main(args: Array[String]): Unit = {
    new PomModuleDescriptorBuilder.ConfMapper {
      override def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean): Unit = {}
    }
    ()
  }
}