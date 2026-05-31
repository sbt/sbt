/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.complete.Parser
import Def.Initialize
import std.TaskExtra.*
import sbt.internal.util.Types
import sbt.internal.util.Types.*
import sbt.util.Applicative

/** Parses input and produces a task to run.  Constructed using the companion object. */
final class InputTask[A1] private (val parser: State => Parser[Task[A1]]):
  def mapTask[S](f: Task[A1] => Task[S]): InputTask[S] =
    InputTask[S](s => parser(s).map(f))

  def partialInput(in: String): InputTask[A1] =
    InputTask[A1](s => Parser(parser(s))(in))

  def fullInput(in: String): InputTask[A1] =
    InputTask[A1](s =>
      Parser.parse(in, parser(s)) match {
        case Right(v)  => Parser.success(v)
        case Left(msg) =>
          val indented = msg.linesIterator.map("   " + _).mkString("\n")
          Parser.failure(s"Invalid programmatic input:\n$indented")
      }
    )
end InputTask

object InputTask:
  def make[A1](p: State => Parser[Task[A1]]): InputTask[A1] = new InputTask[A1](p)

  def free[A1](p: State => Parser[Task[A1]]): InputTask[A1] = make(p)

  def free[A1, A2](p: State => Parser[A1])(c: A1 => Task[A2]): InputTask[A2] =
    free(s => p(s).map(c))

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

  given inputTaskApplicative: Applicative[InputTask] with
    type F[a] = InputTask[a]
    override def pure[A1](a: () => A1): InputTask[A1] = InputTask.createFreeFromAction(a)
    override def ap[A1, A2](ff: InputTask[A1 => A2])(in: InputTask[A1]): InputTask[A2] =
      InputTask[A2]((s: State) =>
        (in.parser(s) ~ ff.parser(s)).map { (ta1, tf) =>
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
