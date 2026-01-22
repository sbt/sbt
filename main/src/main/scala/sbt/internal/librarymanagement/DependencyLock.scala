/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import java.security.MessageDigest
import sbt.librarymanagement.ModuleID

object DependencyLockFile:
  val CurrentLockVersion = "1.0"
  val lockFileName = "deps.lock"

  def computeBuildClock(
      libraryDependencies: Seq[ModuleID],
      resolvers: Seq[String]
  ): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val sortedDeps = libraryDependencies
      .map(m => s"${m.organization}:${m.name}:${m.revision}")
      .sorted
    sortedDeps.foreach(d => digest.update(d.getBytes("UTF-8")))
    resolvers.sorted.foreach(r => digest.update(r.getBytes("UTF-8")))
    digest.digest().map("%02x".format(_)).mkString

  def lockFilePath(baseDirectory: File): File =
    new File(baseDirectory, lockFileName)
