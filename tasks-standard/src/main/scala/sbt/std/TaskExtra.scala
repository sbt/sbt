/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import scala.sys.process.{ BasicIO, ProcessIO, ProcessBuilder }

import sbt.internal.util.AttributeMap
import sbt.internal.util.TupleMapExtension.*
import java.io.{ BufferedInputStream, BufferedReader, File, InputStream }
import sbt.io.IO
import sbt.internal.Action

sealed trait MultiInTask[Tup <: Tuple] {
  def flatMapN[A](f: Tup => Task[A]): Task[A]
  def flatMapR[A](f: Tuple.Map[Tup, Result] => Task[A]): Task[A]
  def mapN[A](f: Tup => A): Task[A]
  def mapR[A](f: Tuple.Map[Tup, Result] => A): Task[A]
  def flatFailure[A](f: Seq[Incomplete] => Task[A]): Task[A]
  def mapFailure[A](f: Seq[Incomplete] => A): Task[A]
}

sealed trait SingleInTask[S] {
  def flatMapN[T](f: S => Task[T]): Task[T]
  def flatMap[T](f: S => Task[T]): Task[T]
  def mapN[T](f: S => T): Task[T]
  def map[T](f: S => T): Task[T]
  def dependsOn(tasks: Task[?]*): Task[S]
  def andFinally(fin: => Unit): Task[S]
  def doFinally(t: Task[Unit]): Task[S]

  def ||[T >: S](alt: Task[T]): Task[T]
  def &&[T](alt: Task[T]): Task[T]

  def failure: Task[Incomplete]
  def result: Task[Result[S]]

  @deprecated(
    "Use the `result` method to create a task that returns the full Result of this task.  Then, call `map` on the new task.",
    "0.13.0"
  )
  def mapR[T](f: Result[S] => T): Task[T]

  @deprecated(
    "Use the `failure` method to create a task that returns Incomplete when this task fails and then call `flatMap` on the new task.",
    "0.13.0"
  )
  def flatFailure[T](f: Incomplete => Task[T]): Task[T]

  @deprecated(
    "Use the `failure` method to create a task that returns Incomplete when this task fails and then call `mapFailure` on the new task.",
    "0.13.0"
  )
  def mapFailure[T](f: Incomplete => T): Task[T]

  @deprecated(
    "Use the `result` method to create a task that returns the full Result of this task.  Then, call `flatMap` on the new task.",
    "0.13.0"
  )
  def flatMapR[T](f: Result[S] => Task[T]): Task[T]
}
sealed trait ForkTask[S, CC[_]] {
  def fork[T](f: S => T): CC[Task[T]]
  def tasks: Seq[Task[S]]
}
sealed trait JoinTask[S, CC[_]] {
  def join: Task[CC[S]]
  // had to rename from 'reduce' for 2.9.0
  def reduced(f: (S, S) => S): Task[S]
}

trait TaskExtra0 {
  final implicit def joinAnyTasks(in: Seq[Task[?]]): JoinTask[Any, Seq] =
    joinTasks0[Any](existToAny(in))
  private[sbt] def joinTasks0[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
    def join: Task[Seq[S]] =
      Task[Seq[S]](Action.Join(in, (s: Seq[Result[S]]) => Right(TaskExtra.all(s))))
    def reduced(f: (S, S) => S): Task[S] = TaskExtra.reduced(in.toIndexedSeq, f)
  }
  private[sbt] def existToAny(in: Seq[Task[?]]): Seq[Task[Any]] = in.asInstanceOf[Seq[Task[Any]]]
}

trait TaskExtra extends TaskExtra0 {
  final def nop: Task[Unit] = constant(())
  final def constant[T](t: T): Task[T] = task(t)

  final def task[T](f: => T): Task[T] = toTask(() => f)
  final implicit def toTask[T](f: () => T): Task[T] = Task(Action.Pure(f, false))
  final def inlineTask[T](value: T): Task[T] = Task(Action.Pure(() => value, true))

  final implicit def upcastTask[A >: B, B](t: Task[B]): Task[A] = t mapN { x =>
    x: A
  }

  final implicit def toTasks[S](in: Seq[() => S]): Seq[Task[S]] = in.map(toTask)
  final implicit def iterableTask[S](in: Seq[S]): ForkTask[S, Seq] = new ForkTask[S, Seq] {
    def fork[T](f: S => T): Seq[Task[T]] = in.map(x => task(f(x)))
    def tasks: Seq[Task[S]] = fork(identity)
  }

  import TaskExtra.{ allM, anyFailM, failM, successM }

  final implicit def joinTasks[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
    def join: Task[Seq[S]] =
      Task[Seq[S]](Action.Join(in, (s: Seq[Result[S]]) => Right(TaskExtra.all(s))))
    def reduced(f: (S, S) => S): Task[S] = TaskExtra.reduced(in.toIndexedSeq, f)
  }

  final implicit def multT2Task[A1, A2](in: (Task[A1], Task[A2])): MultiInTask[(A1, A2)] =
    multInputTask[(A1, A2)](in)

  given multT2TaskConv[A1, A2]: Conversion[(Task[A1], Task[A2]), MultiInTask[(A1, A2)]] =
    multT2Task(_)

  final implicit def multInputTask[Tup <: Tuple](tasks: Tuple.Map[Tup, Task]): MultiInTask[Tup] =
    new MultiInTask[Tup]:
      override def flatMapN[A](f: Tup => Task[A]): Task[A] =
        Task(Action.FlatMapped(tasks, f.compose(allM)))
      override def flatMapR[A](f: Tuple.Map[Tup, Result] => Task[A]): Task[A] =
        Task(Action.FlatMapped(tasks, f))

      override def mapN[A](f: Tup => A): Task[A] =
        Task(Action.Mapped(tasks, f.compose(allM)))
      override def mapR[A](f: Tuple.Map[Tup, Result] => A): Task[A] =
        Task(Action.Mapped(tasks, f))
      override def flatFailure[A](f: Seq[Incomplete] => Task[A]): Task[A] =
        Task(Action.FlatMapped(tasks, f.compose(anyFailM)))
      override def mapFailure[A](f: Seq[Incomplete] => A): Task[A] =
        Task(Action.Mapped(tasks, f.compose(anyFailM)))

  final implicit def singleInputTask[S](in: Task[S]): SingleInTask[S] =
    new SingleInTask[S]:
      // type K[L[x]] = L[S]

      def failure: Task[Incomplete] = mapFailure(identity)
      def result: Task[Result[S]] = mapR(identity)

      private def newAttributes: AttributeMap = TaskExtra.newAttributes(in.attributes)

      override def flatMapR[A](f: Result[S] => Task[A]): Task[A] =
        Task(
          newAttributes,
          Action.FlatMapped[A, Tuple1[S]](Tuple1(in), { case Tuple1(a) => f(a) })
        )

      override def mapR[A](f: Result[S] => A): Task[A] =
        Task(
          newAttributes,
          Action.Mapped[A, Tuple1[S]](Tuple1(in), { case Tuple1(a) => f(a) })
        )

      override def dependsOn(tasks: Task[?]*): Task[S] =
        Task(newAttributes, Action.DependsOn(in, tasks))

      override def flatMapN[T](f: S => Task[T]): Task[T] = flatMapR(f.compose(successM))

      override inline def flatMap[T](f: S => Task[T]): Task[T] = flatMapN[T](f)

      override def flatFailure[T](f: Incomplete => Task[T]): Task[T] = flatMapR(f.compose(failM))

      override def mapN[T](f: S => T): Task[T] = mapR(f.compose(successM))

      override inline def map[T](f: S => T): Task[T] = mapN(f)

      override def mapFailure[T](f: Incomplete => T): Task[T] = mapR(f.compose(failM))

      def andFinally(fin: => Unit): Task[S] = mapR(x => Result.tryValue[S]({ fin; x }))
      def doFinally(t: Task[Unit]): Task[S] =
        flatMapR(x =>
          t.result.mapN { tx =>
            Result.tryValues[S](tx :: Nil, x)
          }
        )
      def ||[T >: S](alt: Task[T]): Task[T] = flatMapR {
        case Result.Value(v) => task(v: T)
        case Result.Inc(_)   => alt
      }
      def &&[T](alt: Task[T]): Task[T] = flatMapN(_ => alt)

  extension [S](in: Task[S]) {
    def describedAs(s: String): Task[S] = in.setDescription(s)
    def named(s: String): Task[S] = in.setName(s)
  }

  extension [Key](
      t: Task[?]
  )(using streams: Task[TaskStreams[Key]], key: Task[?] => Key) {
    def #|(p: ProcessBuilder): Task[Int] = pipe0(None, p)
    def pipe(sid: String)(p: ProcessBuilder): Task[Int] = pipe0(Some(sid), p)
    private def pipe0(sid: Option[String], p: ProcessBuilder): Task[Int] =
      streams.mapN { s =>
        val in = s.readBinary(key(t), sid)
        val pio = TaskExtra
          .processIO(s)
          .withInput(out => { BasicIO.transferFully(in, out); out.close() })
        (p run pio).exitValue()
      }
  }

  extension [Key](
      in: Task[?]
  )(using streams: Task[TaskStreams[Key]], key: Task[?] => Key) {
    def binary[T](f: BufferedInputStream => T): Task[T] = pipeBinary(None, f)
    def binary[T](sid: String)(f: BufferedInputStream => T): Task[T] = pipeBinary(Some(sid), f)

    def #>(f: File): Task[Unit] = pipeBinary(None, toFile(f))
    def #>(sid: String, f: File): Task[Unit] = pipeBinary(Some(sid), toFile(f))

    private def pipeBinary[T](sid: Option[String], f: BufferedInputStream => T): Task[T] =
      streams.mapN { s =>
        f(s.readBinary(key(in), sid))
      }

    private def toFile(f: File) = (in: InputStream) => IO.transfer(in, f)
  }

  extension [Key](
      in: Task[?]
  )(using streams: Task[TaskStreams[Key]], key: Task[?] => Key) {
    def text[T](f: BufferedReader => T): Task[T] = pipe0(None, f)
    def text[T](sid: String)(f: BufferedReader => T): Task[T] = pipe0(Some(sid), f)

    private def pipe0[T](sid: Option[String], f: BufferedReader => T): Task[T] =
      streams.mapN { s =>
        f(s.readText(key(in), sid))
      }
  }
  extension [Key](
      in: Task[?]
  )(using streams: Task[TaskStreams[Key]], key: Task[?] => Key) {
    def lines: Task[List[String]] = lines0(None)
    def lines(sid: String): Task[List[String]] = lines0(Some(sid))

    private def lines0(sid: Option[String]): Task[List[String]] =
      streams map { s =>
        IO.readLines(s.readText(key(in), sid))
      }
  }
  implicit def processToTask(p: ProcessBuilder)(using streams: Task[TaskStreams[?]]): Task[Int] =
    streams map { s =>
      val pio = TaskExtra.processIO(s)
      (p run pio).exitValue()
    }
}

object TaskExtra extends TaskExtra {
  def processIO(s: TaskStreams[?]): ProcessIO = {
    def transfer(id: String) = (in: InputStream) => BasicIO.transferFully(in, s.binary(id))
    new ProcessIO(_.close(), transfer(s.outID), transfer(s.errorID))
  }
  def reduced[S](i: IndexedSeq[Task[S]], f: (S, S) => S): Task[S] =
    i match {
      case Seq()     => sys.error("Cannot reduce empty sequence")
      case Seq(x)    => x
      case Seq(x, y) => reducePair(x, y, f)
      case _ =>
        val (a, b) = i.splitAt(i.size / 2)
        reducePair(reduced(a, f), reduced(b, f), f)
    }

  def reducePair[A1](a: Task[A1], b: Task[A1], f: (A1, A1) => A1): Task[A1] =
    multInputTask[(A1, A1)]((a, b)).mapN(f.tupled)

  def anyFailM[Tup <: Tuple]: Tuple.Map[Tup, Result] => Seq[Incomplete] = in => {
    val incs = failuresM[Tup](in)
    if incs.isEmpty then expectedFailure
    else incs
  }

  def failM[A]: Result[A] => Incomplete = {
    case Result.Inc(i) => i
    case _             => expectedFailure
  }

  def expectedFailure = throw Incomplete(None, message = Some("Expected dependency to fail."))

  def successM[A]: Result[A] => A = {
    case Result.Inc(i)   => throw i
    case Result.Value(a) => a
  }

  def allM[Tup <: Tuple]: Tuple.Map[Tup, Result] => Tup = in => {
    val incs = failuresM[Tup](in)
    if incs.isEmpty then in.unmap(Result.tryValue)
    else throw incompleteDeps(incs)
  }
  def failuresM[Tup <: Tuple]: Tuple.Map[Tup, Result] => Seq[Incomplete] = x => failures(x.toList0)

  def all[D](in: Seq[Result[D]]): Seq[D] = {
    val incs = failures(in)
    if incs.isEmpty then in.map(Result.tryValue[D])
    else throw incompleteDeps(incs)
  }
  def failures[A](results: Seq[Result[A]]): Seq[Incomplete] = results.collect {
    case Result.Inc(i) => i
  }

  def incompleteDeps(incs: Seq[Incomplete]): Incomplete = Incomplete(None, causes = incs)

  def select[A, B](fab: Task[Either[A, B]], f: Task[A => B]): Task[B] =
    Task(newAttributes(fab.attributes), Action.Selected[A, B](fab, f))

  // The "taskDefinitionKey" is used, at least, by the ".previous" functionality.
  // But apparently it *cannot* survive a task map/flatMap/etc. See actions/depends-on.
  private[sbt] def newAttributes[A](attributes: AttributeMap): AttributeMap =
    AttributeMap(attributes.entries.filter(_.key.label != "taskDefinitionKey"))
}
