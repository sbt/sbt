/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._
import Task._
import Execute._

object Test extends std.TaskExtra {
  def t2[A, B](a: Task[A], b: Task[B]) = multInputTask[({ type l[L[x]] = (L[A], L[B]) })#l]((a, b))(AList.tuple2)
  def t3[A, B, C](a: Task[A], b: Task[B], c: Task[C]) = multInputTask[({ type l[L[x]] = (L[A], L[B], L[C]) })#l]((a, b, c))(AList.tuple3)

  val a = task(3)
  val b = task[Boolean](error("test"))
  val b2 = task(true)
  val c = task("asdf")

  val h1 = t3(a, b, c).map { case (aa, bb, cc) => aa + " " + bb + " " + cc }
  val h2 = t3(a, b2, c).map { case (aa, bb, cc) => aa + " " + bb + " " + cc }

  type Values = (Result[Int], Result[Boolean], Result[String])

  val f: Values => Any = {
    case (Value(aa), Value(bb), Value(cc)) => aa + " " + bb + " " + cc
    case x =>
      val cs = x.productIterator.toList.collect { case Inc(x) => x } // workaround for double definition bug
      throw Incomplete(None, causes = cs)
  }
  val d2 = t3(a, b2, c) mapR f
  val f2: Values => Task[Any] = {
    case (Value(aa), Value(bb), Value(cc)) => task(aa + " " + bb + " " + cc)
    case x                                 => d3
  }
  lazy val d = t3(a, b, c) flatMapR f2
  val f3: Values => Task[Any] = {
    case (Value(aa), Value(bb), Value(cc)) => task(aa + " " + bb + " " + cc)
    case x                                 => d2
  }
  lazy val d3 = t3(a, b, c) flatMapR f3

  def d4(i: Int): Task[Int] = nop flatMap { _ => val x = math.random; if (x < 0.01) task(i); else d4(i + 1) }

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
}
