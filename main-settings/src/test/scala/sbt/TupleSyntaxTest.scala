/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.test

import sbt.*

object TupleSyntaxTest:
  def t1[A](a: SettingKey[A], b: TaskKey[A], c: Def.Initialize[A], d: Def.Initialize[Task[A]]) = {
    import sbt.TupleSyntax.*
    (a, b, c.toTaskable, d.toTaskable).mapN { (x: A, y: A, z: A, w: A) =>
      "" + x + y + z + w
    }
  }

  def t2[A](a: SettingKey[A], b: TaskKey[A], c: Def.Initialize[A], d: Def.Initialize[Task[A]]) =
    TupleWrap[(A, A, A, A)]((a, b, c.toTaskable, d)).mapN { case (x: A, y: A, z: A, w: A) =>
      "" + x + y + z + w
    }
end TupleSyntaxTest
