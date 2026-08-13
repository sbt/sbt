/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import sbt.librarymanagement.*

/**
 * Intern pools for the immutable `UpdateReport` components to save memory.
 *
 * The pools are weak, so an entry lives only as long as some report references it.
 */
object UpdateReportInterner {

  private val configRefs = new WeakInterner[ConfigRef]
  private val rules = new WeakInterner[InclExclRule]
  private val artifacts = new WeakInterner[Artifact]
  private val moduleIds = new WeakInterner[ModuleID]
  private val callers = new WeakInterner[Caller]
  private val files = new WeakInterner[File]
  private val moduleReports = new WeakInterner[ModuleReport]

  def intern(c: ConfigRef): ConfigRef = configRefs.intern(c)

  def intern(r: InclExclRule): InclExclRule = rules.intern(r)

  def intern(f: File): File = files.intern(f)

  def intern(a: Artifact): Artifact =
    artifacts.internWith(a) { artifact =>
      // Canonicalize the nested vectors so value-equal artifacts share their pieces even when only
      // encountered once.
      if (artifact.configurations.isEmpty) artifact
      else artifact.withConfigurations(artifact.configurations.map(intern))
    }

  def intern(m: ModuleID): ModuleID =
    moduleIds.internWith(m) { moduleId =>
      if (
        moduleId.inclusions.isEmpty && moduleId.exclusions.isEmpty &&
        moduleId.explicitArtifacts.isEmpty
      ) moduleId
      else
        moduleId
          .withInclusions(moduleId.inclusions.map(intern))
          .withExclusions(moduleId.exclusions.map(intern))
          .withExplicitArtifacts(moduleId.explicitArtifacts.map(intern))
    }

  def intern(c: Caller): Caller =
    callers.internWith(c) { caller =>
      caller
        .withCaller(intern(caller.caller))
        .withCallerConfigurations(caller.callerConfigurations.map(intern))
    }

  def intern(mr: ModuleReport): ModuleReport =
    // A publicationDate is a mutable Calendar, so canonicalize such a report but never pool it.
    if (mr.publicationDate.isDefined) canonicalize(mr)
    else moduleReports.internWith(mr)(canonicalize)

  private def canonicalize(mr: ModuleReport): ModuleReport =
    mr.withModule(intern(mr.module))
      .withArtifacts(mr.artifacts.map { case (a, f) => (intern(a), intern(f)) })
      .withMissingArtifacts(mr.missingArtifacts.map(intern))
      .withConfigurations(mr.configurations.map(intern))
      .withCallers(mr.callers.map(intern))
}
