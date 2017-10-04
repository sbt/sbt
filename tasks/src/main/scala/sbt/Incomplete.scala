/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import scala.collection.mutable.ListBuffer

import sbt.internal.util.IDSet
import Incomplete.{ Error, Value => IValue }

/**
 * Describes why a task did not complete.
 *
 * @param node the task that did not complete that is described by this Incomplete instance
 * @param tpe whether the task was incomplete because of an error or because it was skipped.  Only Error is actually used and Skipped may be removed in the future.
 * @param message an optional error message describing this incompletion
 * @param causes a list of incompletions that prevented `node` from completing
 * @param directCause the exception that caused `node` to not complete
 */
final case class Incomplete(node: Option[AnyRef],
                            tpe: IValue = Error,
                            message: Option[String] = None,
                            causes: Seq[Incomplete] = Nil,
                            directCause: Option[Throwable] = None)
    extends Exception(message.orNull, directCause.orNull)
    with sbt.internal.util.UnprintableException {
  override def toString =
    "Incomplete(node=" + node + ", tpe=" + tpe + ", msg=" + message + ", causes=" + causes + ", directCause=" + directCause + ")"
}

object Incomplete extends Enumeration {
  val Skipped, Error = Value

  def transformTD(i: Incomplete)(f: Incomplete => Incomplete): Incomplete = transform(i, true)(f)
  def transformBU(i: Incomplete)(f: Incomplete => Incomplete): Incomplete = transform(i, false)(f)
  def transform(i: Incomplete, topDown: Boolean)(f: Incomplete => Incomplete): Incomplete = {
    import collection.JavaConverters._
    val visited: collection.mutable.Map[Incomplete, Incomplete] =
      (new java.util.IdentityHashMap[Incomplete, Incomplete]).asScala
    def visit(inc: Incomplete): Incomplete =
      visited.getOrElseUpdate(inc, if (topDown) visitCauses(f(inc)) else f(visitCauses(inc)))
    def visitCauses(inc: Incomplete): Incomplete =
      inc.copy(causes = inc.causes.map(visit))

    visit(i)
  }
  def visitAll(i: Incomplete)(f: Incomplete => Unit): Unit = {
    val visited = IDSet.create[Incomplete]
    def visit(inc: Incomplete): Unit =
      visited.process(inc)(()) {
        f(inc)
        inc.causes.foreach(visit)
      }
    visit(i)
  }
  def linearize(i: Incomplete): Seq[Incomplete] = {
    val ordered = ListBuffer[Incomplete]()
    visitAll(i) { ordered += _ }
    ordered.toList
  }
  def allExceptions(is: Seq[Incomplete]): Iterable[Throwable] =
    allExceptions(new Incomplete(None, causes = is))
  def allExceptions(i: Incomplete): Iterable[Throwable] = {
    val exceptions = IDSet.create[Throwable]
    visitAll(i) { exceptions ++= _.directCause.toList }
    exceptions.all
  }
  def show(tpe: Value) = tpe match { case Skipped => "skipped"; case Error => "error" }
}
