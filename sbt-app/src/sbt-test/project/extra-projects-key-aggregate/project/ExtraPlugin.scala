/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt._, Keys._

object ExtraPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def extraProjects: Seq[Project] =
    Seq(Project("z", file("z")).settings(name := "z"))
}
