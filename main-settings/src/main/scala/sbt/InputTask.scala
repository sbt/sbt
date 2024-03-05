/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.complete.Parser
import Def.Initialize
import std.TaskExtra._
import sbt.internal.util.Types
import sbt.internal.util.Types._
import sbt.util.Applicative

/** Parses input and produces a task to run.  Constructed using the companion object. */
final class InputTask[A1] private (val parser: State => Parser[Task[A1]]):
  def mapTask[S](f: Task[A1] => Task[S]): InputTask[S] =
    InputTask[S](s => parser(s) map f)

  def partialInput(in: String): InputTask[A1] =
    InputTask[A1](s => Parser(parser(s))(in))

  def fullInput(in: String): InputTask[A1] =
    InputTask[A1](s =>
      Parser.parse(in, parser(s)) match {
        case Right(v) => Parser.success(v)
        case Left(msg) =>
          val indented = msg.linesIterator.map("   " + _).mkString("\n")
          Parser.failure(s"Invalid programmatic input:\n$indented")
      }
    )
end InputTask

object InputTask:
  /*
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
              val indented = msg.linesIterator.map("   " + _).mkString("\n")
              sys.error(s"Invalid programmatic input:\n$indented")
          }
        )
      )
    )
  }

  implicit def inputTaskParsed[T](
      @deprecated("unused", "") in: InputTask[T]
  ): std.ParserInputTask[T] = ???

  implicit def inputTaskInitParsed[T](
      @deprecated("unused", "") in: Initialize[InputTask[T]]
  ): std.ParserInputTask[T] = ???
   */

  def make[A1](p: State => Parser[Task[A1]]): InputTask[A1] = new InputTask[A1](p)

  /*
  def static[T](p: Parser[Task[T]]): InputTask[T] = free(_ => p)

  def static[I, T](p: Parser[I])(c: I => Task[T]): InputTask[T] = static(p map c)
   */

  def free[A1](p: State => Parser[Task[A1]]): InputTask[A1] = make(p)

  def free[A1, A2](p: State => Parser[A1])(c: A1 => Task[A2]): InputTask[A2] =
    free(s => p(s) map c)

  def separate[A1, A2](
      p: State => Parser[A1]
  )(action: Initialize[A1 => Task[A2]]): Initialize[InputTask[A2]] =
    separate(Def.value(p))(action)

  def separate[A1, A2](
      p: Initialize[State => Parser[A1]]
  )(action: Initialize[A1 => Task[A2]]): Initialize[InputTask[A2]] =
    p.zipWith(action)((parser, act) => free(parser)(act))

  /** Constructs an InputTask that accepts no user input. */
  def createFree[T](action: Initialize[Task[T]]): Initialize[InputTask[T]] =
    action { tsk =>
      free(emptyParser)(const(tsk))
    }

  def createFreeFromAction[A1](a: () => A1): InputTask[A1] =
    free(emptyParser)(_ => Task.taskMonad.pure(a))

  /**
   * Constructs an InputTask from:
   *  a) a Parser constructed using other Settings, but not Tasks
   *  b) a dynamically constructed Task that uses Settings, Tasks, and the result of parsing.
   */
  def createDyn[A1, A2](
      p: Initialize[State => Parser[A1]]
  )(action: Initialize[Task[A1 => Initialize[Task[A2]]]]): Initialize[InputTask[A2]] =
    separate(p)(std.FullInstance.flattenFun[A1, A2](action))

  /** A dummy parser that consumes no input and produces nothing useful (unit). */
  def emptyParser: State => Parser[Unit] =
    Types.const(sbt.internal.util.complete.DefaultParsers.success(()))

  /*
  /** Implementation detail that is public because it is used by a macro. */
  def parserAsInput[T](p: Parser[T]): Initialize[State => Parser[T]] =
    Def.valueStrict(Types.const(p))

  /** Implementation detail that is public because it is used by a macro. */
  def initParserAsInput[T](i: Initialize[Parser[T]]): Initialize[State => Parser[T]] =
    i(Types.const[State, Parser[T]])

  @deprecated("Use another InputTask constructor or the `Def.inputTask` macro.", "0.13.0")
  def apply[I, T](
      p: Initialize[State => Parser[I]]
  )(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] = {
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
    val t: Task[I] = Task(Info[I]().set(key, none), Pure(f, false))
    (key, t)
  }

  private[this] def subForDummy[I, T](
      marker: AttributeKey[Option[I]],
      value: I,
      task: Task[T]
  ): Task[T] = {
    val seen = new java.util.IdentityHashMap[Task[_], Task[_]]
    lazy val f: Task ~> Task = new (Task ~> Task) {
      def apply[A](t: Task[A]): Task[A] = {
        val t0 = seen.get(t)
        if (t0 == null) {
          val newAction =
            if (t.info.get(marker).isDefined)
              (Pure[A](() => value.asInstanceOf[A], inline = true): Action[A])
            else
              t.work.mapTask(f)
          val newTask = Task(t.info, newAction)
          seen.put(t, newTask)
          newTask
        } else t0.asInstanceOf[Task[A]]
      }
    }
    f(task)
  }
   */

  given inputTaskApplicative: Applicative[InputTask] with
    type F[a] = InputTask[a]
    override def pure[A1](a: () => A1): InputTask[A1] = InputTask.createFreeFromAction(a)
    override def ap[A1, A2](ff: InputTask[A1 => A2])(in: InputTask[A1]): InputTask[A2] =
      InputTask[A2]((s: State) =>
        (in.parser(s) ~ ff.parser(s)).map { case (ta1, tf) =>
          Task.taskMonad.ap(tf)(ta1)
        }
      )
    override def map[A1, A2](in: InputTask[A1])(f: A1 => A2): InputTask[A2] =
      InputTask[A2]((s: State) =>
        in.parser(s).map { ta1 =>
          ta1.map(f)
        }
      )
end InputTask

class ParserGen[A1](val p: Initialize[State => Parser[A1]]):
  inline def mapTask[A2](inline action: A1 => A2): Initialize[InputTask[A2]] =
    ${ std.InputTaskMacro.parserGenInputTaskMacroImpl[A1, A2]('this, 'action) }

  inline def flatMapTask[A2](inline action: A1 => Initialize[Task[A2]]): Initialize[InputTask[A2]] =
    ${ std.InputTaskMacro.parserGenFlatMapTaskImpl[A1, A2]('this, 'action) }
end ParserGen
