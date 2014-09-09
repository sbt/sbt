package org.apache.ivy.plugins.parser.m2

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;

/**
 * Helper evil hackery method to ensure Maven configurations + Ivy i
 */
object EvilHackery {

  val REPLACEMENT_MAVEN_MAPPINGS = {
    // Here we copy paste from Ivy
    val REPLACEMENT_MAPPINGS = new java.util.HashMap[String, PomModuleDescriptorBuilder.ConfMapper]

    REPLACEMENT_MAPPINGS.put("compile", new PomModuleDescriptorBuilder.ConfMapper {
      def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean) {
        if (isOptional) {
          dd.addDependencyConfiguration("optional", "compile(*)")
          // NOTE - This is the problematic piece we're dropping.  EVIL!!!!
          dd.addDependencyConfiguration("optional", "master(compile)")
        } else {
          dd.addDependencyConfiguration("compile", "compile(*)")
          // NOTE - This is the problematic piece we're dropping, as `master` is not special cased.
          dd.addDependencyConfiguration("compile", "master(compile)")
          dd.addDependencyConfiguration("runtime", "runtime(*)")
        }
      }
    })

    REPLACEMENT_MAPPINGS.put("provided", new PomModuleDescriptorBuilder.ConfMapper {
      def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean) {
        if (isOptional) {
          dd.addDependencyConfiguration("optional", "compile(*)")
          dd.addDependencyConfiguration("optional", "provided(*)")
          dd.addDependencyConfiguration("optional", "runtime(*)")
          // Remove evil hackery
          dd.addDependencyConfiguration("optional", "master(compile)")
        } else {
          dd.addDependencyConfiguration("provided", "compile(*)")
          dd.addDependencyConfiguration("provided", "provided(*)")
          dd.addDependencyConfiguration("provided", "runtime(*)")
          // Remove evil hackery
          dd.addDependencyConfiguration("provided", "master(compile)")
        }
      }
    })

    REPLACEMENT_MAPPINGS.put("runtime", new PomModuleDescriptorBuilder.ConfMapper {
      def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean) {
        if (isOptional) {
          dd.addDependencyConfiguration("optional", "compile(*)")
          dd.addDependencyConfiguration("optional", "provided(*)")
          // Remove evil hackery
          dd.addDependencyConfiguration("optional", "master(compile)")
        } else {
          dd.addDependencyConfiguration("runtime", "compile(*)")
          dd.addDependencyConfiguration("runtime", "runtime(*)")
          // Remove evil hackery
          dd.addDependencyConfiguration("runtime", "master(compile)")
        }
      }
    })

    REPLACEMENT_MAPPINGS.put("test", new PomModuleDescriptorBuilder.ConfMapper {
      def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean) {
        dd.addDependencyConfiguration("test", "runtime(*)")
        // Remove evil hackery
        dd.addDependencyConfiguration("test", "master(compile)")
      }
    })

    REPLACEMENT_MAPPINGS.put("system", new PomModuleDescriptorBuilder.ConfMapper {
      def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean) {
        // Hacked
        dd.addDependencyConfiguration("system", "master(compile)")
      }
    })

    REPLACEMENT_MAPPINGS
  }

  def init(): Unit = {
    // SUPER EVIL INITIALIZATION
    try {
      val map = PomModuleDescriptorBuilder.MAVEN2_CONF_MAPPING.asInstanceOf[java.util.Map[String, PomModuleDescriptorBuilder.ConfMapper]]
      map.clear()
      map.putAll(REPLACEMENT_MAVEN_MAPPINGS)
    } catch {
      case e: Exception =>
        // TODO - Log that Ivy may not be configured correctly and you could have maven/ivy issues.
        throw new RuntimeException("FAILURE to install Ivy maven hooks.  Your ivy-maven interaction may suffer resolution errors", e)
    }
  }
}