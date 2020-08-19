/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Keys.{ scalaHome, scalaInstance }
import sbt.internal.inc.ScalaInstance

private[sbt] object UnmanagedScalaInstanceOnly {
  val get: Def.Initialize[Task[Option[ScalaInstance]]] = Def.taskDyn {
    if (scalaHome.value.isDefined) Def.task(Some(scalaInstance.value)) else Def.task(None)
  }
}
