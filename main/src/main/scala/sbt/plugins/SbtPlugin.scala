/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package plugins

import Keys._

object SbtPlugin extends AutoPlugin {
  override def requires = ScriptedPlugin

  override lazy val projectSettings = Seq(
    sbtPlugin := true
  )
}
