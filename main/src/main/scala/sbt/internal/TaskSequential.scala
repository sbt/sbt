/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import Def.*

/**
 * This trait injected to `Def` object to provide `sequential` functions for tasks.
 */
trait TaskSequential {
  def sequential[B](last: Initialize[Task[B]]): Initialize[Task[B]] =
    sequential(Nil, last)
  def sequential[A0, B](
      task0: Initialize[Task[A0]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(List(unitTask(task0)), last)
  def sequential[A0, A1, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(List(unitTask(task0), unitTask(task1)), last)
  def sequential[A0, A1, A2, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(List(unitTask(task0), unitTask(task1), unitTask(task2)), last)
  def sequential[A0, A1, A2, A3, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(List(unitTask(task0), unitTask(task1), unitTask(task2), unitTask(task3)), last)
  def sequential[A0, A1, A2, A3, A4, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(unitTask(task0), unitTask(task1), unitTask(task2), unitTask(task3), unitTask(task4)),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      task14: Initialize[Task[A14]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13),
        unitTask(task14)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      task14: Initialize[Task[A14]],
      task15: Initialize[Task[A15]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13),
        unitTask(task14),
        unitTask(task15)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      task14: Initialize[Task[A14]],
      task15: Initialize[Task[A15]],
      task16: Initialize[Task[A16]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13),
        unitTask(task14),
        unitTask(task15),
        unitTask(task16)
      ),
      last
    )
  def sequential[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, B](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      task14: Initialize[Task[A14]],
      task15: Initialize[Task[A15]],
      task16: Initialize[Task[A16]],
      task17: Initialize[Task[A17]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13),
        unitTask(task14),
        unitTask(task15),
        unitTask(task16),
        unitTask(task17)
      ),
      last
    )
  def sequential[
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      B
  ](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      task14: Initialize[Task[A14]],
      task15: Initialize[Task[A15]],
      task16: Initialize[Task[A16]],
      task17: Initialize[Task[A17]],
      task18: Initialize[Task[A18]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13),
        unitTask(task14),
        unitTask(task15),
        unitTask(task16),
        unitTask(task17),
        unitTask(task18)
      ),
      last
    )
  def sequential[
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      B
  ](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      task14: Initialize[Task[A14]],
      task15: Initialize[Task[A15]],
      task16: Initialize[Task[A16]],
      task17: Initialize[Task[A17]],
      task18: Initialize[Task[A18]],
      task19: Initialize[Task[A19]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13),
        unitTask(task14),
        unitTask(task15),
        unitTask(task16),
        unitTask(task17),
        unitTask(task18),
        unitTask(task19)
      ),
      last
    )
  def sequential[
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20,
      B
  ](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      task14: Initialize[Task[A14]],
      task15: Initialize[Task[A15]],
      task16: Initialize[Task[A16]],
      task17: Initialize[Task[A17]],
      task18: Initialize[Task[A18]],
      task19: Initialize[Task[A19]],
      task20: Initialize[Task[A20]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13),
        unitTask(task14),
        unitTask(task15),
        unitTask(task16),
        unitTask(task17),
        unitTask(task18),
        unitTask(task19),
        unitTask(task20)
      ),
      last
    )
  def sequential[
      A0,
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20,
      A21,
      B
  ](
      task0: Initialize[Task[A0]],
      task1: Initialize[Task[A1]],
      task2: Initialize[Task[A2]],
      task3: Initialize[Task[A3]],
      task4: Initialize[Task[A4]],
      task5: Initialize[Task[A5]],
      task6: Initialize[Task[A6]],
      task7: Initialize[Task[A7]],
      task8: Initialize[Task[A8]],
      task9: Initialize[Task[A9]],
      task10: Initialize[Task[A10]],
      task11: Initialize[Task[A11]],
      task12: Initialize[Task[A12]],
      task13: Initialize[Task[A13]],
      task14: Initialize[Task[A14]],
      task15: Initialize[Task[A15]],
      task16: Initialize[Task[A16]],
      task17: Initialize[Task[A17]],
      task18: Initialize[Task[A18]],
      task19: Initialize[Task[A19]],
      task20: Initialize[Task[A20]],
      task21: Initialize[Task[A21]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    sequential(
      List(
        unitTask(task0),
        unitTask(task1),
        unitTask(task2),
        unitTask(task3),
        unitTask(task4),
        unitTask(task5),
        unitTask(task6),
        unitTask(task7),
        unitTask(task8),
        unitTask(task9),
        unitTask(task10),
        unitTask(task11),
        unitTask(task12),
        unitTask(task13),
        unitTask(task14),
        unitTask(task15),
        unitTask(task16),
        unitTask(task17),
        unitTask(task18),
        unitTask(task19),
        unitTask(task20),
        unitTask(task21)
      ),
      last
    )

  def sequential[B](tasks: Seq[Initialize[Task[B]]]): Initialize[Task[B]] = {
    val initTasks: Seq[Initialize[Task[B]]] = tasks.init
    val lastTask: Initialize[Task[B]] = tasks.last
    sequential(initTasks.map(unitTask), lastTask)
  }

  def sequential[B](
      tasks: Seq[Initialize[Task[Unit]]],
      last: Initialize[Task[B]]
  ): Initialize[Task[B]] =
    tasks.toList match {
      case Nil => Def.task { last.value }
      case x :: xs =>
        Def.task { Def.unit(x.value) }.flatMapTask { case _ =>
          sequential(xs, last)
        }
    }
  private def unitTask[A](task: Initialize[Task[A]]): Initialize[Task[Unit]] =
    Def.task {
      Def.unit(task.value)
      ()
    }
}
