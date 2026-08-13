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
import lmcoursier.internal.{ RequestedDependency, RequestedInputs }
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

  def computeRequestedInputs(
      libraryDependencies: Seq[ModuleID],
      resolvers: Seq[String]
  ): RequestedInputs = {
    val dependencies = libraryDependencies
      .map { m =>
        RequestedDependency(
          configuration = m.configurations.getOrElse(""),
          organization = m.organization,
          name = m.name,
          version = m.revision,
          variantSelector = ""
        )
      }
      .sortBy(d => (d.configuration, d.organization, d.name, d.version))
      .toVector

    RequestedInputs(
      dependencies = dependencies,
      repositories = resolvers.sorted.toVector,
      scalaVersion = None,
      maxIterations = 0,
      forceVersions = Vector.empty,
      exclusions = Vector.empty,
      strict = None
    )

  }

  def lockFilePath(baseDirectory: File): File =
    new File(baseDirectory, lockFileName)
