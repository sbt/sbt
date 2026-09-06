/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*

object GlobalPluginSpec extends Properties:
  override def tests: List[Test] =
    List(
      example("forced update report depends on update directly", forced),
      example("cached update report keeps update out of the dependencies", cached),
    )

  private def dependsOnUpdate[A](init: Def.Initialize[Task[A]]): Boolean =
    init.dependencies.exists(_.key == Keys.update.key)

  def forced: Result =
    Result.assert(dependsOnUpdate(GlobalPlugin.updateReportInit(force = true)))

  def cached: Result =
    Result.assert(!dependsOnUpdate(GlobalPlugin.updateReportInit(force = false)))
end GlobalPluginSpec
