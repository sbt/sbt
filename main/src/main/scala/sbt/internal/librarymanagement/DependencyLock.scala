/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import lmcoursier.internal.{ RequestedDependency, RequestedInputs }
import sbt.librarymanagement.ModuleID

object DependencyLockFile:
  val CurrentLockVersion = "1.0"
  val lockFileName = "deps.lock"

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
