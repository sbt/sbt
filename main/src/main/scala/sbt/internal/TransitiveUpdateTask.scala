/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.librarymanagement.UpdateReport
import sbt.Keys.{ globalPluginUpdate, update }

private[sbt] object TransitiveUpdateTask {
  val get: Def.Initialize[Task[Seq[UpdateReport]]] = {
    import ScopeFilter.Make.{ inDependencies => inDeps, _ }
    val selectDeps = ScopeFilter(inDeps(ThisProject, includeRoot = false))
    val allUpdates = update.?.all(selectDeps)
    // If I am a "build" (a project inside project/) then I have a globalPluginUpdate.
    Def.task { allUpdates.value.flatten ++ globalPluginUpdate.?.value }
  }
}
