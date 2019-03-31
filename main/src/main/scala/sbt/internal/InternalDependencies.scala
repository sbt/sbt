/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Keys._

private[sbt] object InternalDependencies {
  def configurations: Def.Initialize[Seq[(ProjectRef, Set[String])]] = Def.setting {
    val allConfigs = Classpaths.allConfigs(configuration.value).map(_.name).toSet
    val ref = thisProjectRef.value
    val applicableConfigs = allConfigs + "*"
    ((ref -> allConfigs) +: buildDependencies.value.classpath
      .get(ref)
      .toSeq
      .flatMap(_.flatMap {
        case ResolvedClasspathDependency(p, rawConfigs) =>
          val configs = rawConfigs.getOrElse("*->compile").split(";").flatMap { config =>
            config.split("->") match {
              case Array(n, c) if applicableConfigs.contains(n) => Some(c)
              case _                                            => None
            }
          }
          if (configs.isEmpty) None else Some(p -> configs.toSet)
      })).distinct
  }
}
