/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.librarymanagement.Configuration

object OverrideConfigs {
  def apply(cs: Configuration*)(
      configurations: Seq[Configuration]
  ): Seq[Configuration] = {
    val existingName = configurations.map(_.name).toSet
    val newByName = cs.map(c => (c.name, c)).toMap
    val overridden = configurations map { conf =>
      newByName.getOrElse(conf.name, conf)
    }
    val newConfigs = cs filter { c =>
      !existingName(c.name)
    }
    overridden ++ newConfigs
  }
}
