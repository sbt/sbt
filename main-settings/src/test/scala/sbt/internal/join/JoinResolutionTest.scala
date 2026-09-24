/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.join

import sbt.{ Def, Task, TaskKey }

object JoinResolutionTest:
  // `.join` on a seq of task initializes must resolve to Initialize[Task[Seq[Int]]] (task-flattening),
  // not the generic Initialize[Seq[Task[Int]]]. Each result is inferred with NO expected type first
  // (`val joined = ...`) and only then checked against the declared return type, so the test fails if
  // resolution depends on an expected type to pick the right form. This package does not import
  // `sbt.Scoped`, so it pins that the resolution holds via implicit scope alone. Guards issue #899.

  // The originally reported shape: a plain `Seq[Initialize[Task[A]]]` (no `Scoped` base type).
  def check(in: Seq[Def.Initialize[Task[Int]]]): Def.Initialize[Task[Seq[Int]]] =
    val joined = in.join
    joined

  // `TaskKey` extends `Scoped`, so this also pins that the task join wins without becoming ambiguous
  // with the `Scoped.richTaskSeq` conversion that is in implicit scope here.
  def checkTaskKey(in: Seq[TaskKey[Int]]): Def.Initialize[Task[Seq[Int]]] =
    val joined = in.join
    joined
end JoinResolutionTest
