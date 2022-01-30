package org.apache.ivy.plugins.parser.m2

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;

/**
 * It turns out there was a very subtle, and evil, issue sitting the Ivy/maven configuration, and it
 * related to dependency mapping.    A mapping of `foo->bar(*)` means that the local configuration
 * `foo` depends on the remote configuration `bar`, if it exists, or *ALL CONFIGURATIONS* if `bar`
 * does not exist.   Since the default Ivy configuration mapping was using the random `master`
 * configuration, which AFAICT is NEVER specified, just an assumed default, this would cause leaks
 * between maven + ivy projects.
 *
 * i.e. if  a maven POM depends on a module denoted by an ivy.xml file, then you'd wind up accidentally
 * bleeding ALL the ivy module's configurations into the maven module's configurations.
 *
 * This fix works around the issue, by assuming that if there is no `master` configuration, than the
 * maven default of `compile` is intended.   As sbt forces generated `ivy.xml` files to abide by
 * maven conventions, this works in all of our test cases.   The only scenario where it wouldn't work
 * is those who have custom ivy.xml files *and* have pom.xml files which rely on those custom ivy.xml files,
 * a very unlikely situation where the workaround is:  "define a master configuration".
 *
 * Also see: http://ant.apache.org/ivy/history/2.3.0/ivyfile/dependency.html
 * and: http://svn.apache.org/repos/asf/ant/ivy/core/tags/2.3.0/src/java/org/apache/ivy/plugins/parser/m2/PomModuleDescriptorBuilder.java
 */
object ReplaceMavenConfigurationMappings {

  def addMappings(dd: DefaultDependencyDescriptor, scope: String, isOptional: Boolean) = {
    val mapping = ReplaceMavenConfigurationMappings.REPLACEMENT_MAVEN_MAPPINGS.get(scope)
    mapping.addMappingConfs(dd, isOptional)
  }

  val REPLACEMENT_MAVEN_MAPPINGS = {
    // Here we copy paste from Ivy
    val REPLACEMENT_MAPPINGS = new java.util.HashMap[String, PomModuleDescriptorBuilder.ConfMapper]

    // NOTE - This code is copied from org.apache.ivy.plugins.parser.m2.PomModuleDescriptorBuilder
    // except with altered default configurations...
    REPLACEMENT_MAPPINGS.put(
      "compile",
      new PomModuleDescriptorBuilder.ConfMapper {
        def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean): Unit = {
          if (isOptional) {
            dd.addDependencyConfiguration("optional", "compile(*)")
            // FIX - Here we take a mroe conservative approach of depending on the compile configuration if master isn't there.
            dd.addDependencyConfiguration("optional", "master(compile)")
          } else {
            dd.addDependencyConfiguration("compile", "compile(*)")
            // FIX - Here we take a mroe conservative approach of depending on the compile configuration if master isn't there.
            dd.addDependencyConfiguration("compile", "master(compile)")
            dd.addDependencyConfiguration("runtime", "runtime(*)")
          }
        }
      }
    )
    REPLACEMENT_MAPPINGS.put(
      "provided",
      new PomModuleDescriptorBuilder.ConfMapper {
        def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean): Unit = {
          if (isOptional) {
            dd.addDependencyConfiguration("optional", "compile(*)")
            dd.addDependencyConfiguration("optional", "provided(*)")
            dd.addDependencyConfiguration("optional", "runtime(*)")
            // FIX - Here we take a mroe conservative approach of depending on the compile configuration if master isn't there.
            dd.addDependencyConfiguration("optional", "master(compile)")
          } else {
            dd.addDependencyConfiguration("provided", "compile(*)")
            dd.addDependencyConfiguration("provided", "provided(*)")
            dd.addDependencyConfiguration("provided", "runtime(*)")
            // FIX - Here we take a mroe conservative approach of depending on the compile configuration if master isn't there.
            dd.addDependencyConfiguration("provided", "master(compile)")
          }
        }
      }
    )

    REPLACEMENT_MAPPINGS.put(
      "runtime",
      new PomModuleDescriptorBuilder.ConfMapper {
        def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean): Unit = {
          if (isOptional) {
            dd.addDependencyConfiguration("optional", "compile(*)")
            dd.addDependencyConfiguration("optional", "provided(*)")
            // FIX - Here we take a mroe conservative approach of depending on the compile configuration if master isn't there.
            dd.addDependencyConfiguration("optional", "master(compile)")
          } else {
            dd.addDependencyConfiguration("runtime", "compile(*)")
            dd.addDependencyConfiguration("runtime", "runtime(*)")
            // FIX - Here we take a mroe conservative approach of depending on the compile configuration if master isn't there.
            dd.addDependencyConfiguration("runtime", "master(compile)")
          }
        }
      }
    )

    REPLACEMENT_MAPPINGS.put(
      "test",
      new PomModuleDescriptorBuilder.ConfMapper {
        def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean): Unit = {
          dd.addDependencyConfiguration("test", "runtime(*)")
          // FIX - Here we take a mroe conservative approach of depending on the compile configuration if master isn't there.
          dd.addDependencyConfiguration("test", "master(compile)")
        }
      }
    )

    REPLACEMENT_MAPPINGS.put(
      "system",
      new PomModuleDescriptorBuilder.ConfMapper {
        def addMappingConfs(dd: DefaultDependencyDescriptor, isOptional: Boolean): Unit = {
          // FIX - Here we take a mroe conservative approach of depending on the compile configuration if master isn't there.
          dd.addDependencyConfiguration("system", "master(compile)")
        }
      }
    )

    REPLACEMENT_MAPPINGS
  }

  def init(): Unit = {
    // Here we mutate a static final field, because we have to AND because it's evil.
    try {
      val map = PomModuleDescriptorBuilder.MAVEN2_CONF_MAPPING
        .asInstanceOf[java.util.Map[String, PomModuleDescriptorBuilder.ConfMapper]]
      map.clear()
      map.putAll(REPLACEMENT_MAVEN_MAPPINGS)
    } catch {
      case e: Exception =>
        // TODO - Log that Ivy may not be configured correctly and you could have maven/ivy issues.
        throw new RuntimeException(
          "FAILURE to install Ivy maven hooks.  Your ivy-maven interaction may suffer resolution errors",
          e
        )
    }
  }
}
