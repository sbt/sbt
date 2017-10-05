/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import java.text.DateFormat
import Def.ScopedKey
import Keys.{ showSuccess, showTiming, timingFormat }
import sbt.internal.util.complete.Parser
import sbt.internal.util.{ Dag, HList, Settings, Util }
import sbt.util.{ Logger, Show }
import Parser.{ failure, seq, success }
import std.Transform.DummyTaskMap

sealed trait Aggregation
object Aggregation {
  final case class ShowConfig(
      settingValues: Boolean,
      taskValues: Boolean,
      print: String => Unit,
      success: Boolean
  )

  final case class Complete[T](
      start: Long,
      stop: Long,
      results: sbt.Result[Seq[KeyValue[T]]],
      state: State
  )

  final case class KeyValue[+T](key: ScopedKey[_], value: T)

  def defaultShow(state: State, showTasks: Boolean): ShowConfig =
    ShowConfig(
      settingValues = true,
      taskValues = showTasks,
      s => state.log.info(s),
      success = true
    )

  def printSettings(xs: Seq[KeyValue[_]], print: String => Unit)(
      implicit display: Show[ScopedKey[_]]
  ): Unit =
    xs match {
      case KeyValue(_, x: Seq[_]) :: Nil => print(x.mkString("* ", "\n* ", ""))
      case KeyValue(_, x) :: Nil         => print(x.toString)
      case _ =>
        xs foreach (kv => print(display.show(kv.key) + "\n\t" + kv.value.toString))
    }

  type Values[T] = Seq[KeyValue[T]]
  type AnyKeys = Values[_]

  def seqParser[T](ps: Values[Parser[T]]): Parser[Seq[KeyValue[T]]] =
    seq(ps.map { case KeyValue(k, p) => p.map(v => KeyValue(k, v)) })

  def applyTasks[T](
      s: State,
      structure: BuildStructure,
      ps: Values[Parser[Task[T]]],
      show: ShowConfig
  )(implicit display: Show[ScopedKey[_]]): Parser[() => State] =
    Command.applyEffect(seqParser(ps))(ts => runTasks(s, structure, ts, DummyTaskMap(Nil), show))

  private def showRun[T](complete: Complete[T], show: ShowConfig)(
      implicit display: Show[ScopedKey[_]]
  ): Unit = {
    import complete._
    val log = state.log
    val extracted = Project.extract(state)
    val success = results match { case Value(_) => true; case Inc(_) => false }
    results.toEither.right.foreach { r =>
      if (show.taskValues) printSettings(r, show.print)
    }
    if (show.success) printSuccess(start, stop, extracted, success, log)
  }

  def timedRun[T](
      s: State,
      ts: Values[Task[T]],
      extra: DummyTaskMap
  ): Complete[T] = {
    import EvaluateTask._
    import std.TaskExtra._

    val extracted = Project extract s
    import extracted.structure
    val toRun = ts map { case KeyValue(k, t) => t.map(v => KeyValue(k, v)) } join;
    val roots = ts map { case KeyValue(k, _) => k }
    val config = extractedTaskConfig(extracted, structure, s)

    val start = System.currentTimeMillis
    val (newS, result) = withStreams(structure, s) { str =>
      val transform = nodeView(s, str, roots, extra)
      runTask(toRun, s, str, structure.index.triggers, config)(transform)
    }
    val stop = System.currentTimeMillis
    Complete(start, stop, result, newS)
  }

  def runTasks[HL <: HList, T](s: State,
                               structure: BuildStructure,
                               ts: Values[Task[T]],
                               extra: DummyTaskMap,
                               show: ShowConfig)(implicit display: Show[ScopedKey[_]]): State = {
    val complete = timedRun[T](s, ts, extra)
    showRun(complete, show)
    complete.results match {
      case Inc(i)   => complete.state.handleError(i)
      case Value(_) => complete.state
    }
  }

  def printSuccess(
      start: Long,
      stop: Long,
      extracted: Extracted,
      success: Boolean,
      log: Logger
  ): Unit = {
    import extracted._
    def get(key: SettingKey[Boolean]): Boolean =
      key in currentRef get structure.data getOrElse true
    if (get(showSuccess)) {
      if (get(showTiming)) {
        val msg = timingString(start, stop, "", structure.data, currentRef, log)
        if (success) log.success(msg) else log.error(msg)
      } else if (success)
        log.success("")
    }
  }
  private def timingString(
      startTime: Long,
      endTime: Long,
      s: String,
      data: Settings[Scope],
      currentRef: ProjectRef,
      log: Logger
  ): String = {
    val format = timingFormat in currentRef get data getOrElse defaultFormat
    timing(format, startTime, endTime, "", log)
  }
  def timing(
      format: java.text.DateFormat,
      startTime: Long,
      endTime: Long,
      s: String,
      log: Logger
  ): String = {
    val ss = if (s.isEmpty) "" else s + " "
    val nowString = format.format(new java.util.Date(endTime))
    "Total " + ss + "time: " + (endTime - startTime + 500) / 1000 + " s, completed " + nowString
  }

  def defaultFormat: DateFormat = {
    import java.text.DateFormat
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
  }

  def applyDynamicTasks[I](
      s: State,
      structure: BuildStructure,
      inputs: Values[InputTask[I]],
      show: ShowConfig
  )(implicit display: Show[ScopedKey[_]]): Parser[() => State] = {
    val parsers = for (KeyValue(k, it) <- inputs)
      yield it.parser(s).map(v => KeyValue(k, v))
    Command.applyEffect(seq(parsers)) { roots =>
      runTasks(s, structure, roots, DummyTaskMap(Nil), show)
    }
  }

  def evaluatingParser(s: State, structure: BuildStructure, show: ShowConfig)(
      keys: Seq[KeyValue[_]]
  )(implicit display: Show[ScopedKey[_]]): Parser[() => State] = {

    // to make the call sites clearer
    def separate[L](in: Seq[KeyValue[_]])(
        f: KeyValue[_] => Either[KeyValue[L], KeyValue[_]]
    ): (Seq[KeyValue[L]], Seq[KeyValue[_]]) =
      Util.separate(in)(f)

    val kvs = keys.toList
    if (kvs.isEmpty)
      failure("No such setting/task")
    else {
      val (inputTasks, other) = separate[InputTask[_]](kvs) {
        case KeyValue(k, v: InputTask[_]) => Left(KeyValue(k, v))
        case kv                           => Right(kv)
      }
      val (tasks, settings) = separate[Task[_]](other) {
        case KeyValue(k, v: Task[_]) => Left(KeyValue(k, v))
        case kv                      => Right(kv)
      }
      // currently, disallow input tasks to be mixed with normal tasks.
      // This occurs in `all` or `show`, which support multiple tasks.
      // Previously, multiple tasks could be run in one execution, but they were all for the same key, just in different scopes.
      // When `all` was added, it allowed different keys and thus opened the possibility for mixing settings,
      //  tasks, and input tasks in the same call.  The code below allows settings and tasks to be mixed, but not input tasks.
      // One problem with input tasks in `all` is that many input tasks consume all input and would need syntactic delimiters.
      // Once that is addressed, the tasks constructed by the input tasks would need to be combined with the explicit tasks.
      if (inputTasks.nonEmpty) {
        if (other.nonEmpty) {
          val inputStrings = inputTasks.map(_.key).mkString("Input task(s):\n\t", "\n\t", "\n")
          val otherStrings = other.map(_.key).mkString("Task(s)/setting(s):\n\t", "\n\t", "\n")
          failure(s"Cannot mix input tasks with plain tasks/settings.  $inputStrings $otherStrings")
        } else
          applyDynamicTasks(s, structure, maps(inputTasks)(castToAny), show)
      } else {
        val base =
          if (tasks.isEmpty) success(() => s)
          else
            applyTasks(s, structure, maps(tasks)(x => success(castToAny(x))), show)
        base.map { res => () =>
          val newState = res()
          if (show.settingValues && settings.nonEmpty) printSettings(settings, show.print)
          newState
        }
      }
    }
  }
  // this is a hack to avoid duplicating method implementations
  private[this] def castToAny[T[_]](t: T[_]): T[Any] = t.asInstanceOf[T[Any]]

  private[this] def maps[T, S](vs: Values[T])(f: T => S): Values[S] =
    vs map { case KeyValue(k, v) => KeyValue(k, f(v)) }

  def projectAggregates[Proj](
      proj: Option[Reference],
      extra: BuildUtil[Proj],
      reverse: Boolean
  ): Seq[ProjectRef] = {
    val resRef = proj.map(p => extra.projectRefFor(extra.resolveRef(p)))
    resRef.toList.flatMap(ref =>
      if (reverse) extra.aggregates.reverse(ref) else extra.aggregates.forward(ref))
  }

  def aggregate[T, Proj](
      key: ScopedKey[T],
      rawMask: ScopeMask,
      extra: BuildUtil[Proj],
      reverse: Boolean = false
  ): Seq[ScopedKey[T]] = {
    val mask = rawMask.copy(project = true)
    Dag.topologicalSort(key) { k =>
      if (reverse)
        reverseAggregatedKeys(k, extra, mask)
      else if (aggregationEnabled(k, extra.data))
        aggregatedKeys(k, extra, mask)
      else
        Nil
    }
  }
  def reverseAggregatedKeys[T](
      key: ScopedKey[T],
      extra: BuildUtil[_],
      mask: ScopeMask
  ): Seq[ScopedKey[T]] =
    projectAggregates(key.scope.project.toOption, extra, reverse = true) flatMap { ref =>
      val toResolve = key.scope.copy(project = Select(ref))
      val resolved = Resolve(extra, Zero, key.key, mask)(toResolve)
      val skey = ScopedKey(resolved, key.key)
      if (aggregationEnabled(skey, extra.data)) skey :: Nil else Nil
    }

  def aggregatedKeys[T](
      key: ScopedKey[T],
      extra: BuildUtil[_],
      mask: ScopeMask
  ): Seq[ScopedKey[T]] =
    projectAggregates(key.scope.project.toOption, extra, reverse = false) map { ref =>
      val toResolve = key.scope.copy(project = Select(ref))
      val resolved = Resolve(extra, Zero, key.key, mask)(toResolve)
      ScopedKey(resolved, key.key)
    }

  def aggregationEnabled(key: ScopedKey[_], data: Settings[Scope]): Boolean =
    Keys.aggregate in Scope.fillTaskAxis(key.scope, key.key) get data getOrElse true
}
