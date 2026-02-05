/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt._, Keys._

/**
 * Reproduces #4976 / Dale's use case: plugin defines the root project via extraProjects.
 * Without the fix, loading fails with "Overlapping output directories" (build root + foo).
 */
object RootFromExtraPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def extraProjects: Seq[Project] =
    Seq(Project("foo", file(".")).settings(name := "foo"))
}
