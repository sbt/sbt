/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Keys.*

private[sbt] object InternalDependencies {
  def configurations: Def.Initialize[Seq[(ProjectRef, Set[String])]] = Def.setting {
    val configMap = internalConfigurationMap.value
    val config = configMap(configuration.value)
    val allConfigs = Classpaths.allConfigs(config).map(_.name).toSet
    val ref = thisProjectRef.value
    val projectDependencies = buildDependencies.value.classpath.get(ref).toSeq.flatten
    val applicableConfigs = allConfigs + "*"
    ((ref -> allConfigs) +:
      projectDependencies.flatMap { case ClasspathDep.ResolvedClasspathDependency(p, rawConfigs) =>
        val configs = rawConfigs.getOrElse("*->compile").split(";").flatMap { config =>
          config.split("->") match {
            case Array(n, c) if applicableConfigs.contains(n) => Some(c)
            case Array(n) if applicableConfigs.contains(n)    =>
              // "test" is equivalent to "compile->test"
              Some("compile")
            case _ => None
          }
        }
        if (configs.isEmpty) None else Some(p -> configs.toSet)
      }).distinct
  }
}
