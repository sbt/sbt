/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package std

import scala.sys.process.{ BasicIO, ProcessIO, ProcessBuilder }

import sbt.internal.util.{ AList, AttributeMap }
import sbt.internal.util.Types._
import java.io.{ BufferedInputStream, BufferedReader, File, InputStream }
import sbt.io.IO

sealed trait MultiInTask[K[L[x]]] {
  def flatMap[T](f: K[Id] => Task[T]): Task[T]
  def flatMapR[T](f: K[Result] => Task[T]): Task[T]
  def map[T](f: K[Id] => T): Task[T]
  def mapR[T](f: K[Result] => T): Task[T]
  def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T]
  def mapFailure[T](f: Seq[Incomplete] => T): Task[T]
}

sealed trait SingleInTask[S] {
  def flatMap[T](f: S => Task[T]): Task[T]
  def map[T](f: S => T): Task[T]
  def dependsOn(tasks: Task[_]*): Task[S]
  def andFinally(fin: => Unit): Task[S]
  def doFinally(t: Task[Unit]): Task[S]

  def ||[T >: S](alt: Task[T]): Task[T]
  def &&[T](alt: Task[T]): Task[T]

  def failure: Task[Incomplete]
  def result: Task[Result[S]]

  @deprecated(
    "Use the `result` method to create a task that returns the full Result of this task.  Then, call `map` on the new task.",
    "0.13.0")
  def mapR[T](f: Result[S] => T): Task[T]

  @deprecated(
    "Use the `failure` method to create a task that returns Incomplete when this task fails and then call `flatMap` on the new task.",
    "0.13.0")
  def flatFailure[T](f: Incomplete => Task[T]): Task[T]

  @deprecated(
    "Use the `failure` method to create a task that returns Incomplete when this task fails and then call `mapFailure` on the new task.",
    "0.13.0")
  def mapFailure[T](f: Incomplete => T): Task[T]

  @deprecated(
    "Use the `result` method to create a task that returns the full Result of this task.  Then, call `flatMap` on the new task.",
    "0.13.0")
  def flatMapR[T](f: Result[S] => Task[T]): Task[T]
}
sealed trait TaskInfo[S] {
  def describedAs(s: String): Task[S]
  def named(s: String): Task[S]
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

sealed trait BinaryPipe {
  def binary[T](f: BufferedInputStream => T): Task[T]
  def binary[T](sid: String)(f: BufferedInputStream => T): Task[T]
  def #>(f: File): Task[Unit]
  def #>(sid: String, f: File): Task[Unit]
}
sealed trait TextPipe {
  def text[T](f: BufferedReader => T): Task[T]
  def text[T](sid: String)(f: BufferedReader => T): Task[T]
}
sealed trait TaskLines {
  def lines: Task[List[String]]
  def lines(sid: String): Task[List[String]]
}
sealed trait ProcessPipe {
  def #|(p: ProcessBuilder): Task[Int]
  def pipe(sid: String)(p: ProcessBuilder): Task[Int]
}

trait TaskExtra {
  final def nop: Task[Unit] = constant(())
  final def constant[T](t: T): Task[T] = task(t)

  final def task[T](f: => T): Task[T] = toTask(f _)
  final implicit def toTask[T](f: () => T): Task[T] = Task(Info(), new Pure(f, false))
  final def inlineTask[T](value: T): Task[T] = Task(Info(), new Pure(() => value, true))

  final implicit def upcastTask[A >: B, B](t: Task[B]): Task[A] = t map { x =>
    x: A
  }
  final implicit def toTasks[S](in: Seq[() => S]): Seq[Task[S]] = in.map(toTask)
  final implicit def iterableTask[S](in: Seq[S]): ForkTask[S, Seq] = new ForkTask[S, Seq] {
    def fork[T](f: S => T): Seq[Task[T]] = in.map(x => task(f(x)))
    def tasks: Seq[Task[S]] = fork(idFun)
  }

  import TaskExtra.{ allM, anyFailM, existToAny, failM, successM }

  final implicit def joinAnyTasks(in: Seq[Task[_]]): JoinTask[Any, Seq] =
    joinTasks[Any](existToAny(in))
  final implicit def joinTasks[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
    def join: Task[Seq[S]] =
      Task[Seq[S]](Info(), new Join(in, (s: Seq[Result[S]]) => Right(TaskExtra.all(s))))
    def reduced(f: (S, S) => S): Task[S] = TaskExtra.reduced(in.toIndexedSeq, f)
  }

  final implicit def multT2Task[A, B](in: (Task[A], Task[B])) =
    multInputTask[λ[L[x] => (L[A], L[B])]](in)(AList.tuple2[A, B])

  final implicit def multInputTask[K[L[X]]](tasks: K[Task])(implicit a: AList[K]): MultiInTask[K] =
    new MultiInTask[K] {
      def flatMapR[T](f: K[Result] => Task[T]): Task[T] =
        Task(Info(), new FlatMapped[T, K](tasks, f, a))
      def flatMap[T](f: K[Id] => Task[T]): Task[T] =
        Task(Info(), new FlatMapped[T, K](tasks, f compose allM, a))
      def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T] =
        Task(Info(), new FlatMapped[T, K](tasks, f compose anyFailM, a))

      def mapR[T](f: K[Result] => T): Task[T] = Task(Info(), new Mapped[T, K](tasks, f, a))
      def map[T](f: K[Id] => T): Task[T] = Task(Info(), new Mapped[T, K](tasks, f compose allM, a))
      def mapFailure[T](f: Seq[Incomplete] => T): Task[T] =
        Task(Info(), new Mapped[T, K](tasks, f compose anyFailM, a))
    }

  final implicit def singleInputTask[S](in: Task[S]): SingleInTask[S] = new SingleInTask[S] {
    type K[L[x]] = L[S]
    private def ml = AList.single[S]

    def failure: Task[Incomplete] = mapFailure(idFun)
    def result: Task[Result[S]] = mapR(idFun)

    // The "taskDefinitionKey" is used, at least, by the ".previous" functionality.
    // But apparently it *cannot* survive a task map/flatMap/etc. See actions/depends-on.
    private def newInfo[A]: Info[A] =
      Info[A](AttributeMap(in.info.attributes.entries.filter(_.key.label != "taskDefinitionKey")))

    def flatMapR[T](f: Result[S] => Task[T]): Task[T] =
      Task(newInfo, new FlatMapped[T, K](in, f, ml))
    def mapR[T](f: Result[S] => T): Task[T] = Task(newInfo, new Mapped[T, K](in, f, ml))
    def dependsOn(tasks: Task[_]*): Task[S] = Task(newInfo, new DependsOn(in, tasks))

    def flatMap[T](f: S => Task[T]): Task[T] = flatMapR(f compose successM)
    def flatFailure[T](f: Incomplete => Task[T]): Task[T] = flatMapR(f compose failM)

    def map[T](f: S => T): Task[T] = mapR(f compose successM)
    def mapFailure[T](f: Incomplete => T): Task[T] = mapR(f compose failM)

    def andFinally(fin: => Unit): Task[S] = mapR(x => Result.tryValue[S]({ fin; x }))
    def doFinally(t: Task[Unit]): Task[S] =
      flatMapR(x =>
        t.result.map { tx =>
          Result.tryValues[S](tx :: Nil, x)
      })
    def ||[T >: S](alt: Task[T]): Task[T] = flatMapR {
      case Value(v) => task(v); case Inc(_) => alt
    }
    def &&[T](alt: Task[T]): Task[T] = flatMap(_ => alt)
  }

  final implicit def toTaskInfo[S](in: Task[S]): TaskInfo[S] = new TaskInfo[S] {
    def describedAs(s: String): Task[S] = in.copy(info = in.info.setDescription(s))
    def named(s: String): Task[S] = in.copy(info = in.info.setName(s))
  }

  final implicit def pipeToProcess[Key](t: Task[_])(implicit streams: Task[TaskStreams[Key]],
                                                    key: Task[_] => Key): ProcessPipe =
    new ProcessPipe {
      def #|(p: ProcessBuilder): Task[Int] = pipe0(None, p)
      def pipe(sid: String)(p: ProcessBuilder): Task[Int] = pipe0(Some(sid), p)
      private def pipe0(sid: Option[String], p: ProcessBuilder): Task[Int] =
        for (s <- streams) yield {
          val in = s.readBinary(key(t), sid)
          val pio = TaskExtra
            .processIO(s)
            .withInput(out => { BasicIO.transferFully(in, out); out.close() })
          (p run pio).exitValue
        }
    }

  final implicit def binaryPipeTask[Key](in: Task[_])(implicit streams: Task[TaskStreams[Key]],
                                                      key: Task[_] => Key): BinaryPipe =
    new BinaryPipe {
      def binary[T](f: BufferedInputStream => T): Task[T] = pipe0(None, f)
      def binary[T](sid: String)(f: BufferedInputStream => T): Task[T] = pipe0(Some(sid), f)

      def #>(f: File): Task[Unit] = pipe0(None, toFile(f))
      def #>(sid: String, f: File): Task[Unit] = pipe0(Some(sid), toFile(f))

      private def pipe0[T](sid: Option[String], f: BufferedInputStream => T): Task[T] =
        streams map { s =>
          f(s.readBinary(key(in), sid))
        }

      private def toFile(f: File) = (in: InputStream) => IO.transfer(in, f)
    }
  final implicit def textPipeTask[Key](in: Task[_])(implicit streams: Task[TaskStreams[Key]],
                                                    key: Task[_] => Key): TextPipe = new TextPipe {
    def text[T](f: BufferedReader => T): Task[T] = pipe0(None, f)
    def text[T](sid: String)(f: BufferedReader => T): Task[T] = pipe0(Some(sid), f)

    private def pipe0[T](sid: Option[String], f: BufferedReader => T): Task[T] =
      streams map { s =>
        f(s.readText(key(in), sid))
      }
  }
  final implicit def linesTask[Key](in: Task[_])(implicit streams: Task[TaskStreams[Key]],
                                                 key: Task[_] => Key): TaskLines = new TaskLines {
    def lines: Task[List[String]] = lines0(None)
    def lines(sid: String): Task[List[String]] = lines0(Some(sid))

    private def lines0[T](sid: Option[String]): Task[List[String]] =
      streams map { s =>
        IO.readLines(s.readText(key(in), sid))
      }
  }
  implicit def processToTask(p: ProcessBuilder)(implicit streams: Task[TaskStreams[_]]): Task[Int] =
    streams map { s =>
      val pio = TaskExtra.processIO(s)
      (p run pio).exitValue
    }
}
object TaskExtra extends TaskExtra {
  def processIO(s: TaskStreams[_]): ProcessIO = {
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

  def reducePair[S](a: Task[S], b: Task[S], f: (S, S) => S): Task[S] =
    multInputTask[λ[L[x] => (L[S], L[S])]]((a, b))(AList.tuple2[S, S]) map f.tupled

  def anyFailM[K[L[x]]](implicit a: AList[K]): K[Result] => Seq[Incomplete] = in => {
    val incs = failuresM(a)(in)
    if (incs.isEmpty) expectedFailure else incs
  }
  def failM[T]: Result[T] => Incomplete = { case Inc(i) => i; case _ => expectedFailure }

  def expectedFailure = throw Incomplete(None, message = Some("Expected dependency to fail."))

  def successM[T]: Result[T] => T = { case Inc(i) => throw i; case Value(t) => t }
  def allM[K[L[x]]](implicit a: AList[K]): K[Result] => K[Id] = in => {
    val incs = failuresM(a)(in)
    if (incs.isEmpty) a.transform(in, Result.tryValue) else throw incompleteDeps(incs)
  }
  def failuresM[K[L[x]]](implicit a: AList[K]): K[Result] => Seq[Incomplete] =
    x => failures[Any](a.toList(x))

  def all[D](in: Seq[Result[D]]): Seq[D] = {
    val incs = failures(in)
    if (incs.isEmpty) in.map(Result.tryValue.fn[D]) else throw incompleteDeps(incs)
  }
  def failures[A](results: Seq[Result[A]]): Seq[Incomplete] = results.collect { case Inc(i) => i }

  def incompleteDeps(incs: Seq[Incomplete]): Incomplete = Incomplete(None, causes = incs)

  private[sbt] def existToAny(in: Seq[Task[_]]): Seq[Task[Any]] = in.asInstanceOf[Seq[Task[Any]]]
}
