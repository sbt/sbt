/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.test

import sbt._
import sbt.Def.Initialize
import sbt.TupleSyntax._

object TupleSyntaxTest {
  def t1[T](a: SettingKey[T], b: TaskKey[T], c: Initialize[T], d: Initialize[Task[T]]) = {
    (a, b, c.toTaskable, d.toTaskable).map((x: T, y: T, z: T, w: T) => "" + x + y + z + w)
  }
}
