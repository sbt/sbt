/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.AList

object Test extends std.TaskExtra:
  def t2[A1, A2](a1: Task[A1], a2: Task[A2]) =
    given AList[[F[_]] =>> Tuple.Map[(A1, A2), F]] = AList.tuple[(A1, A2)]
    multInputTask[[F[_]] =>> Tuple.Map[(A1, A2), F]]((a1, a2))
  def t3[A1, A2, A3](a1: Task[A1], a2: Task[A2], a3: Task[A3]) =
    given AList[[F[_]] =>> Tuple.Map[(A1, A2, A3), F]] = AList.tuple[(A1, A2, A3)]
    multInputTask[[F[_]] =>> Tuple.Map[(A1, A2, A3), F]]((a1, a2, a3))

  val a = task(3)
  val b = task[Boolean](sys.error("test"))
  val b2 = task(true)
  val c = task("asdf")

  val h1 = t3(a, b, c).mapN { case (aa, bb, cc) => s"$aa $bb $cc" }
  val h2 = t3(a, b2, c).mapN { case (aa, bb, cc) => s"$aa $bb $cc" }

  type Values = (Result[Int], Result[Boolean], Result[String])

  val f: Values => Any = {
    case (Result.Value(aa), Result.Value(bb), Result.Value(cc)) => s"$aa $bb $cc"
    case x =>
      val cs = x.productIterator.toList.collect { case Result.Inc(x) =>
        x
      } // workaround for double definition bug
      throw Incomplete(None, causes = cs)
  }
  val d2 = t3(a, b2, c) mapR f
  val f2: Values => Task[Any] = {
    case (Result.Value(aa), Result.Value(bb), Result.Value(cc)) => task(s"$aa $bb $cc")
    case _                                                      => d3
  }
  lazy val d = t3(a, b, c) flatMapR f2
  val f3: Values => Task[Any] = {
    case (Result.Value(aa), Result.Value(bb), Result.Value(cc)) => task(s"$aa $bb $cc")
    case _                                                      => d2
  }
  lazy val d3 = t3(a, b, c) flatMapR f3

  def d4(i: Int): Task[Int] = nop flatMap { _ =>
    val x = math.random; if (x < 0.01) task(i); else d4(i + 1)
  }

  def go(): Unit = {
    def run[T](root: Task[T]) =
      println("Result : " + TaskGen.run(root, true, 2))

    run(a)
    run(b)
    run(b2)
    run(c)
    run(d)
    run(d2)
    run(d4(0))
    run(h1)
    run(h2)
  }
end Test
