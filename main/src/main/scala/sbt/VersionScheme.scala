/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.librarymanagement.VersionSchemes

object VersionScheme {
  val Always = VersionSchemes.Always
  val EarlySemVer = VersionSchemes.EarlySemVer
  val PVP = VersionSchemes.PackVer
  val SemVerSpec = VersionSchemes.SemVerSpec
  val Strict = VersionSchemes.Strict
}
