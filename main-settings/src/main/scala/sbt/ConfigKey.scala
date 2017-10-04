/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

final case class ConfigKey(name: String)
object ConfigKey {
  implicit def configurationToKey(c: sbt.librarymanagement.Configuration): ConfigKey =
    ConfigKey(c.name)
}
