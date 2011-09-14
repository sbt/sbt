package sbt.extra.dsl

import sbt._
import Scoped._
import Project.{richInitializeTask,richInitialize}

object SimpleTasks {
  final def task(name: String) = new TaskId(name)
}

/** Represents the new 'id' of a task to define on a project. */
final class TaskId(name: String) {
  /** Creates a Task that has no dependencies. */
  final def is[R: Manifest](f: => R) =
    TaskKey[R](name) := f
  /*final def on[A1](a: ScopedTaskable[A1]): TaskDepend1[A1] = 
    new TaskDepend1[A1](name, a)*/
  final def on[A1, A2](a1: ScopedTaskable[A1], a2: ScopedTaskable[A2]): TaskDepend2[A1, A2] =
    new TaskDepend2[A1, A2](name, a1, a2)
  final def on[A1, A2, A3](a1: ScopedTaskable[A1],
                           a2: ScopedTaskable[A2],
                           a3: ScopedTaskable[A3]) =
    new TaskDepend3[A1, A2, A3](name, a1, a2, a3)
  final def on[A1, A2, A3, A4](a1: ScopedTaskable[A1],
                              a2: ScopedTaskable[A2],
                              a3: ScopedTaskable[A3],
                              a4: ScopedTaskable[A4]) =
    new TaskDepend4[A1, A2, A3, A4](name, a1, a2, a3, a4)
  final def on[A1, A2, A3, A4, A5](a1: ScopedTaskable[A1],
                                   a2: ScopedTaskable[A2],
                                   a3: ScopedTaskable[A3],
                                   a4: ScopedTaskable[A4],
                                   a5: ScopedTaskable[A5]) =
    new TaskDepend5[A1, A2, A3, A4, A5](name, a1, a2, a3, a4, a5)
  final def on[A1, A2, A3, A4, A5, A6](a1: ScopedTaskable[A1],
                                       a2: ScopedTaskable[A2],
                                       a3: ScopedTaskable[A3],
                                       a4: ScopedTaskable[A4],
                                       a5: ScopedTaskable[A5],
                                       a6: ScopedTaskable[A6]) =
    new TaskDepend6[A1, A2, A3, A4, A5, A6](name, a1, a2, a3, a4, a5, a6)
  final def on[A1, A2, A3, A4, A5, A6, A7](a1: ScopedTaskable[A1],
                                           a2: ScopedTaskable[A2],
                                           a3: ScopedTaskable[A3],
                                           a4: ScopedTaskable[A4],
                                           a5: ScopedTaskable[A5],
                                           a6: ScopedTaskable[A6],
                                           a7: ScopedTaskable[A7]) =
    new TaskDepend7[A1, A2, A3, A4, A5, A6, A7](name, a1, a2, a3, a4, a5, a6, a7)
  final def on[A1, A2, A3, A4, A5, A6, A7, A8](a1: ScopedTaskable[A1],
                                               a2: ScopedTaskable[A2],
                                               a3: ScopedTaskable[A3],
                                               a4: ScopedTaskable[A4],
                                               a5: ScopedTaskable[A5],
                                               a6: ScopedTaskable[A6],
                                               a7: ScopedTaskable[A7],
                                               a8: ScopedTaskable[A8]) =
    new TaskDepend8[A1, A2, A3, A4, A5, A6, A7, A8](name, a1, a2, a3, a4, a5, a6, a7, a8)
  final def on[A1, A2, A3, A4, A5, A6, A7, A8, A9](a1: ScopedTaskable[A1],
                                                   a2: ScopedTaskable[A2],
                                                   a3: ScopedTaskable[A3],
                                                   a4: ScopedTaskable[A4],
                                                   a5: ScopedTaskable[A5],
                                                   a6: ScopedTaskable[A6],
                                                   a7: ScopedTaskable[A7],
                                                   a8: ScopedTaskable[A8],
                                                   a9: ScopedTaskable[A9]) =
    new TaskDepend9[A1, A2, A3, A4, A5, A6, A7, A8, A9](name, a1, a2, a3, a4, a5, a6, a7, a8, a9)
}

/** Represents a not-yet-defined task that has one dependency */
/*final class TaskDepend1[A1](name: String, a1: ScopedTaskable[A1]) {
  final def is[R: Manifest](f: A1 => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= a1 map f
  }
}*/

/** Represents a not-yet-defined task that has two dependencies */
final class TaskDepend2[A1, A2](name: String,
                                a1: ScopedTaskable[A1],
                                a2: ScopedTaskable[A2]) {
  final def is[R: Manifest](f: (A1, A2) => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= (a1, a2) map f
  }
}
/** Represents a not-yet-defined task that has two dependencies */
final class TaskDepend3[A1, A2, A3](name: String,
                                    a1: ScopedTaskable[A1],
                                    a2: ScopedTaskable[A2],
                                    a3: ScopedTaskable[A3]) {
  final def is[R: Manifest](f: (A1, A2, A3) => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= (a1, a2, a3) map f
  }
}
/** Represents a not-yet-defined task that has two dependencies */
final class TaskDepend4[A1, A2, A3, A4](name: String,
                                        a1: ScopedTaskable[A1],
                                        a2: ScopedTaskable[A2],
                                        a3: ScopedTaskable[A3],
                                        a4: ScopedTaskable[A4]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4) => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= (a1, a2, a3, a4) map f
  }
}
/** Represents a not-yet-defined task that has two dependencies */
final class TaskDepend5[A1, A2, A3, A4, A5](name: String,
                                            a1: ScopedTaskable[A1],
                                            a2: ScopedTaskable[A2],
                                            a3: ScopedTaskable[A3],
                                            a4: ScopedTaskable[A4],
                                            a5: ScopedTaskable[A5]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5) => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= (a1, a2, a3, a4, a5) map f
  }
}
/** Represents a not-yet-defined task that has two dependencies */
final class TaskDepend6[A1, A2, A3, A4, A5, A6](name: String,
                                                a1: ScopedTaskable[A1],
                                                a2: ScopedTaskable[A2],
                                                a3: ScopedTaskable[A3],
                                                a4: ScopedTaskable[A4],
                                                a5: ScopedTaskable[A5],
                                                a6: ScopedTaskable[A6]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5, A6) => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= (a1, a2, a3, a4, a5, a6) map f
  }
}
/** Represents a not-yet-defined task that has two dependencies */
final class TaskDepend7[A1, A2, A3, A4, A5, A6, A7](name: String,
                                                    a1: ScopedTaskable[A1],
                                                    a2: ScopedTaskable[A2],
                                                    a3: ScopedTaskable[A3],
                                                    a4: ScopedTaskable[A4],
                                                    a5: ScopedTaskable[A5],
                                                    a6: ScopedTaskable[A6],
                                                    a7: ScopedTaskable[A7]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5, A6, A7) => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= (a1, a2, a3, a4, a5, a6, a7) map f
  }
}
/** Represents a not-yet-defined task that has two dependencies */
final class TaskDepend8[A1, A2, A3, A4, A5, A6, A7, A8](name: String,
                                                        a1: ScopedTaskable[A1],
                                                        a2: ScopedTaskable[A2],
                                                        a3: ScopedTaskable[A3],
                                                        a4: ScopedTaskable[A4],
                                                        a5: ScopedTaskable[A5],
                                                        a6: ScopedTaskable[A6],
                                                        a7: ScopedTaskable[A7],
                                                        a8: ScopedTaskable[A8]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5, A6, A7, A8) => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= (a1, a2, a3, a4, a5, a6, a7, a8) map f
  }
}
/** Represents a not-yet-defined task that has two dependencies */
final class TaskDepend9[A1, A2, A3, A4, A5, A6, A7, A8, A9](name: String,
                                                            a1: ScopedTaskable[A1],
                                                            a2: ScopedTaskable[A2],
                                                            a3: ScopedTaskable[A3],
                                                            a4: ScopedTaskable[A4],
                                                            a5: ScopedTaskable[A5],
                                                            a6: ScopedTaskable[A6],
                                                            a7: ScopedTaskable[A7],
                                                            a8: ScopedTaskable[A8],
                                                            a9: ScopedTaskable[A9]) {
  final def is[R: Manifest](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9) => R): Setting[Task[R]] = {
    TaskKey[R](name) <<= (a1, a2, a3, a4, a5, a6, a7, a8, a9) map f
  }
}



