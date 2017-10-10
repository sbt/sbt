/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.util.complete.Parser
import Def.{ Initialize, ScopedKey }
import std.TaskExtra._
import sbt.internal.util.{ ~>, AttributeKey, Types }
import sbt.internal.util.Types._

/** Parses input and produces a task to run.  Constructed using the companion object. */
final class InputTask[T] private (val parser: State => Parser[Task[T]]) {
  def mapTask[S](f: Task[T] => Task[S]): InputTask[S] =
    new InputTask[S](s => parser(s) map f)

  def partialInput(in: String): InputTask[T] =
    new InputTask[T](s => Parser(parser(s))(in))

  def fullInput(in: String): InputTask[T] =
    new InputTask[T](s =>
      Parser.parse(in, parser(s)) match {
        case Right(v) => Parser.success(v)
        case Left(msg) =>
          val indented = msg.lines.map("   " + _).mkString("\n")
          Parser.failure(s"Invalid programmatic input:\n$indented")
    })
}

object InputTask {
  implicit class InitializeInput[T](i: Initialize[InputTask[T]]) {
    def partialInput(in: String): Initialize[InputTask[T]] = i(_ partialInput in)
    def fullInput(in: String): Initialize[InputTask[T]] = i(_ fullInput in)

    import std.FullInstance._
    def toTask(in: String): Initialize[Task[T]] = flatten(
      (Def.stateKey zipWith i)((sTask, it) =>
        sTask map (s =>
          Parser.parse(in, it.parser(s)) match {
            case Right(t) => Def.value(t)
            case Left(msg) =>
              val indented = msg.lines.map("   " + _).mkString("\n")
              sys.error(s"Invalid programmatic input:\n$indented")
          }))
    )
  }

  implicit def inputTaskParsed[T](in: InputTask[T]): std.ParserInputTask[T] = ???
  implicit def inputTaskInitParsed[T](in: Initialize[InputTask[T]]): std.ParserInputTask[T] = ???

  def make[T](p: State => Parser[Task[T]]): InputTask[T] = new InputTask[T](p)

  def static[T](p: Parser[Task[T]]): InputTask[T] = free(_ => p)

  def static[I, T](p: Parser[I])(c: I => Task[T]): InputTask[T] = static(p map c)

  def free[T](p: State => Parser[Task[T]]): InputTask[T] = make(p)

  def free[I, T](p: State => Parser[I])(c: I => Task[T]): InputTask[T] = free(s => p(s) map c)

  def separate[I, T](p: State => Parser[I])(
      action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
    separate(Def value p)(action)

  def separate[I, T](p: Initialize[State => Parser[I]])(
      action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
    p.zipWith(action)((parser, act) => free(parser)(act))

  /** Constructs an InputTask that accepts no user input. */
  def createFree[T](action: Initialize[Task[T]]): Initialize[InputTask[T]] =
    action { tsk =>
      free(emptyParser)(const(tsk))
    }

  /**
   * Constructs an InputTask from:
   *  a) a Parser constructed using other Settings, but not Tasks
   *  b) a dynamically constructed Task that uses Settings, Tasks, and the result of parsing.
   */
  def createDyn[I, T](p: Initialize[State => Parser[I]])(
      action: Initialize[Task[I => Initialize[Task[T]]]]): Initialize[InputTask[T]] =
    separate(p)(std.FullInstance.flattenFun[I, T](action))

  /** A dummy parser that consumes no input and produces nothing useful (unit).*/
  def emptyParser: State => Parser[Unit] =
    Types.const(sbt.internal.util.complete.DefaultParsers.success(()))

  /** Implementation detail that is public because it is used by a macro.*/
  def parserAsInput[T](p: Parser[T]): Initialize[State => Parser[T]] =
    Def.valueStrict(Types.const(p))

  /** Implementation detail that is public because it is used by a macro.*/
  def initParserAsInput[T](i: Initialize[Parser[T]]): Initialize[State => Parser[T]] =
    i(Types.const)

  @deprecated("Use another InputTask constructor or the `Def.inputTask` macro.", "0.13.0")
  def apply[I, T](p: Initialize[State => Parser[I]])(
      action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] = {
    val dummyKey = localKey[Task[I]]
    val (marker, dummy) = dummyTask[I]
    val it = action(TaskKey(dummyKey)) mapConstant subResultForDummy(dummyKey, dummy)
    val act = it { tsk => (value: I) =>
      subForDummy(marker, value, tsk)
    }
    separate(p)(act)
  }

  /**
   * The proper solution is to have a Manifest context bound and accept slight source incompatibility,
   * The affected InputTask construction methods are all deprecated and so it is better to keep complete
   * compatibility.  Because the AttributeKey is local, it uses object equality and the manifest is not used.
   */
  private[this] def localKey[T]: AttributeKey[T] =
    AttributeKey.local[Unit].asInstanceOf[AttributeKey[T]]

  private[this] def subResultForDummy[I](dummyKey: AttributeKey[Task[I]], dummyTask: Task[I]) =
    new (ScopedKey ~> Option) {
      def apply[T](sk: ScopedKey[T]) =
        if (sk.key eq dummyKey) {
          // sk.key: AttributeKey[T], dummy.key: AttributeKey[Task[I]]
          // (sk.key eq dummy.key) ==> T == Task[I] because AttributeKey is invariant
          Some(dummyTask.asInstanceOf[T])
        } else
          None
    }

  private[this] def dummyTask[I]: (AttributeKey[Option[I]], Task[I]) = {
    val key = localKey[Option[I]]
    val f: () => I = () =>
      sys.error(s"Internal sbt error: InputTask stub was not substituted properly.")
    val t: Task[I] = Task(Info[I]().set(key, None), Pure(f, false))
    (key, t)
  }

  private[this] def subForDummy[I, T](marker: AttributeKey[Option[I]],
                                      value: I,
                                      task: Task[T]): Task[T] = {
    val seen = new java.util.IdentityHashMap[Task[_], Task[_]]
    lazy val f: Task ~> Task = new (Task ~> Task) {
      def apply[A](t: Task[A]): Task[A] = {
        val t0 = seen.get(t)
        if (t0 == null) {
          val newAction =
            if (t.info.get(marker).isDefined)
              Pure(() => value.asInstanceOf[A], inline = true)
            else
              t.work.mapTask(f)
          val newTask = Task(t.info, newAction)
          seen.put(t, newTask)
          newTask
        } else
          t0.asInstanceOf[Task[A]]
      }
    }
    f(task)
  }
}
