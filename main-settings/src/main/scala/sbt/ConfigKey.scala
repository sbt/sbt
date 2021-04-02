/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

final case class ConfigKey(name: String)
object ConfigKey {
  implicit def configurationToKey(c: sbt.librarymanagement.Configuration): ConfigKey =
    ConfigKey(c.name)
}
