/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Keys.{ compile, loadedTestFrameworks, streams }

private[sbt] object DetectTests {
  val task: Def.Initialize[Task[Seq[TestDefinition]]] = Def.task {
    Tests.discover(loadedTestFrameworks.value.values.toList, compile.value, streams.value.log)._1
  }
}
