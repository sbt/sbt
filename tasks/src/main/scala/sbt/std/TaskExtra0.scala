package sbt.std

import sbt.internal.Action
import sbt.internal.util.Types.Id
import sbt.internal.util.{ AList, AttributeMap }
import sbt.{ Task, Result, Incomplete, Info }

sealed trait MultiInTask[K[F[_]]] {
  def flatMapN[A](f: K[Id] => Task[A]): Task[A]
  def flatMapR[A](f: K[Result] => Task[A]): Task[A]
  def mapN[A](f: K[Id] => A): Task[A]
  def mapR[A](f: K[Result] => A): Task[A]
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

trait TaskExtra0 {
  final implicit def joinAnyTasks(in: Seq[Task[?]]): JoinTask[Any, Seq] =
    joinTasks0[Any](existToAny(in))
  private[sbt] def existToAny(in: Seq[Task[?]]): Seq[Task[Any]] = in.asInstanceOf[Seq[Task[Any]]]
  private[sbt] def joinTasks0[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
    def join: Task[Seq[S]] =
      Task[Seq[S]](Info(), Action.Join(in, (s: Seq[Result[S]]) => Right(TaskExtra0.all(s))))
    def reduced(f: (S, S) => S): Task[S] = TaskExtra0.reduced(in.toIndexedSeq, f)
  }

  final def nop: Task[Unit] = constant(())
  final def constant[T](t: T): Task[T] = task(t)

  final def task[T](f: => T): Task[T] = toTask(() => f)
  final implicit def toTask[T](f: () => T): Task[T] = Task(Info(), Action.Pure(f, false))
  final def inlineTask[T](value: T): Task[T] = Task(Info(), Action.Pure(() => value, true))

  final implicit def upcastTask[A >: B, B](t: Task[B]): Task[A] = t mapN { x =>
    x: A
  }

  final implicit def toTasks[S](in: Seq[() => S]): Seq[Task[S]] = in.map(toTask)
  final implicit def iterableTask[S](in: Seq[S]): ForkTask[S, Seq] = new ForkTask[S, Seq] {
    def fork[T](f: S => T): Seq[Task[T]] = in.map(x => task(f(x)))
    def tasks: Seq[Task[S]] = fork(identity)
  }

  final implicit def joinTasks[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
    def join: Task[Seq[S]] =
      Task[Seq[S]](Info(), Action.Join(in, (s: Seq[Result[S]]) => Right(TaskExtra0.all(s))))
    def reduced(f: (S, S) => S): Task[S] = TaskExtra0.reduced(in.toIndexedSeq, f)
  }

  final implicit def multT2Task[A1, A2](
      in: (Task[A1], Task[A2])
  ): MultiInTask[[F[_]] =>> Tuple.Map[(A1, A2), F]] =
    given AList[[F[_]] =>> Tuple.Map[(A1, A2), F]] = AList.tuple[(A1, A2)]
    multInputTask[[F[_]] =>> Tuple.Map[(A1, A2), F]](in)

  given multT2TaskConv[A1, A2]
      : Conversion[(Task[A1], Task[A2]), MultiInTask[[F[_]] =>> Tuple.Map[(A1, A2), F]]] =
    multT2Task(_)

  final implicit def multInputTask[K[F[_]]: AList](tasks: K[Task]): MultiInTask[K] =
    new MultiInTask[K]:
      override def flatMapN[A](f: K[Id] => Task[A]): Task[A] =
        Task(Info(), Action.FlatMapped[A, K](tasks, f compose allM, AList[K]))
      override def flatMapR[A](f: K[Result] => Task[A]): Task[A] =
        Task(Info(), Action.FlatMapped[A, K](tasks, f, AList[K]))

      override def mapN[A](f: K[Id] => A): Task[A] =
        Task(Info(), Action.Mapped[A, K](tasks, f compose allM, AList[K]))
      override def mapR[A](f: K[Result] => A): Task[A] =
        Task(Info(), Action.Mapped[A, K](tasks, f, AList[K]))
      override def flatFailure[A](f: Seq[Incomplete] => Task[A]): Task[A] =
        Task(Info(), Action.FlatMapped[A, K](tasks, f compose anyFailM, AList[K]))
      override def mapFailure[A](f: Seq[Incomplete] => A): Task[A] =
        Task(Info(), Action.Mapped[A, K](tasks, f compose anyFailM, AList[K]))

  final implicit def singleInputTask[S](in: Task[S]): SingleInTask[S] =
    new SingleInTask[S]:
      // type K[L[x]] = L[S]
      given alist: AList[[F[_]] =>> Tuple.Map[Tuple1[S], F]] = AList.tuple[Tuple1[S]]

      def failure: Task[Incomplete] = mapFailure(identity)
      def result: Task[Result[S]] = mapR(identity)

      private def newInfo[A]: Info[A] = TaskExtra0.newInfo(in.info)

      override def flatMapR[A](f: Result[S] => Task[A]): Task[A] =
        Task(
          newInfo,
          Action.FlatMapped[A, [F[_]] =>> Tuple.Map[Tuple1[S], F]](
            AList.toTuple(in),
            AList.fromTuple(f),
            alist,
          )
        )

      override def mapR[A](f: Result[S] => A): Task[A] =
        Task(
          newInfo,
          Action.Mapped[A, [F[_]] =>> Tuple.Map[Tuple1[S], F]](
            AList.toTuple(in),
            AList.fromTuple(f),
            alist,
          )
        )

      override def dependsOn(tasks: Task[?]*): Task[S] = Task(newInfo, Action.DependsOn(in, tasks))

      override def flatMapN[T](f: S => Task[T]): Task[T] = flatMapR(f compose successM)

      override inline def flatMap[T](f: S => Task[T]): Task[T] = flatMapN[T](f)

      override def flatFailure[T](f: Incomplete => Task[T]): Task[T] = flatMapR(f compose failM)

      override def mapN[T](f: S => T): Task[T] = mapR(f compose successM)

      override inline def map[T](f: S => T): Task[T] = mapN(f)

      override def mapFailure[T](f: Incomplete => T): Task[T] = mapR(f compose failM)

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

  final implicit def toTaskInfo[S](in: Task[S]): TaskInfo[S] = new TaskInfo[S] {
    def describedAs(s: String): Task[S] = in.copy(info = in.info.setDescription(s))
    def named(s: String): Task[S] = in.copy(info = in.info.setName(s))
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
    given AList[[F[_]] =>> Tuple.Map[(A1, A1), F]] = AList.tuple[(A1, A1)]
    multInputTask[[F[_]] =>> Tuple.Map[(A1, A1), F]]((a, b)) mapN f.tupled

  def anyFailM[K[F[_]]: AList]: K[Result] => Seq[Incomplete] = in => {
    val incs = failuresM[K](AList[K])(in)
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

  def allM[K[F[_]]: AList]: K[Result] => K[Id] = in => {
    val incs = failuresM[K](AList[K])(in)
    if incs.isEmpty then AList[K].transform[Result, Id](in)(Result.tryValue)
    else throw incompleteDeps(incs)
  }
  def failuresM[K[F[_]]: AList]: K[Result] => Seq[Incomplete] = x =>
    failures[Any](AList[K].toList(x))

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
    Task(newInfo(fab.info), Action.Selected[A, B](fab, f))

  // The "taskDefinitionKey" is used, at least, by the ".previous" functionality.
  // But apparently it *cannot* survive a task map/flatMap/etc. See actions/depends-on.
  private[sbt] def newInfo[A](info: Info[_]): Info[A] =
    Info[A](AttributeMap(info.attributes.entries.filter(_.key.label != "taskDefinitionKey")))
}

object TaskExtra0 extends TaskExtra0
