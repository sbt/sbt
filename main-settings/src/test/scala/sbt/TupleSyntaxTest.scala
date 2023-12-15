/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.test

import sbt._
import sbt.Def.Initialize
import sbt.internal.util.Types.Id

object TupleSyntaxTest:
  def t1[A](a: SettingKey[A], b: TaskKey[A], c: Def.Initialize[A], d: Def.Initialize[Task[A]]) = {
    import sbt.TupleSyntax._
    (a, b, c.toTaskable, d.toTaskable).mapN { (x: A, y: A, z: A, w: A) =>
      "" + x + y + z + w
    }
  }

  def t2[A](a: SettingKey[A], b: TaskKey[A], c: Def.Initialize[A], d: Def.Initialize[Task[A]]) =
    TupleWrap[(A, A, A, A)]((a, b, c.toTaskable, d)).mapN { case (x: A, y: A, z: A, w: A) =>
      "" + x + y + z + w
    }
end TupleSyntaxTest
