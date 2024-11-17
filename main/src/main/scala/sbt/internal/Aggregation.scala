/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.text.DateFormat

import sbt.Def.{ ScopedKey, Settings }
import sbt.Keys.{ showSuccess, showTiming, timingFormat }
import sbt.ProjectExtra.*
import sbt.SlashSyntax0.given
import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.Parser.{ failure, seq, success }
import sbt.internal.util._
import sbt.std.Transform.DummyTaskMap
import sbt.util.{ Logger, Show }

sealed trait Aggregation
object Aggregation {
  final case class ShowConfig(
      settingValues: Boolean,
      taskValues: Boolean,
      print: String => Unit,
      success: Boolean
  )

  final case class Complete[A](
      start: Long,
      stop: Long,
      results: sbt.Result[Seq[KeyValue[A]]],
      cacheSummary: String,
      state: State
  )

  final case class KeyValue[+T](key: ScopedKey[?], value: T)

  def defaultShow(state: State, showTasks: Boolean): ShowConfig =
    ShowConfig(
      settingValues = true,
      taskValues = showTasks,
      s => state.log.info(s),
      success = true
    )

  def printSettings(xs: Seq[KeyValue[?]], print: String => Unit)(using
      display: Show[ScopedKey[?]]
  ): Unit =
    xs match {
      case KeyValue(_, x: Seq[_]) :: Nil => print(x.mkString("* ", "\n* ", ""))
      case KeyValue(_, x) :: Nil         => print(x.toString)
      case _ =>
        xs foreach (kv => print(display.show(kv.key) + "\n\t" + kv.value.toString))
    }

  type Values[T] = Seq[KeyValue[T]]
  type AnyKeys = Values[Any]

  def seqParser[T](ps: Values[Parser[T]]): Parser[Seq[KeyValue[T]]] =
    seq(ps.map { case KeyValue(k, p) => p.map(v => KeyValue(k, v)) })

  def applyTasks[T](
      s: State,
      ps: Values[Parser[Task[T]]],
      show: ShowConfig
  )(using display: Show[ScopedKey[?]]): Parser[() => State] =
    Command.applyEffect(seqParser(ps))(ts => runTasks(s, ts, DummyTaskMap(Nil), show))

  private def showRun[A](complete: Complete[A], show: ShowConfig)(using
      display: Show[ScopedKey[?]]
  ): Unit =
    import complete.*
    val log = state.log
    val extracted = Project.extract(state)
    val success = results match
      case Result.Value(_) => true
      case Result.Inc(_)   => false
    val isPaused = currentChannel(state) match
      case Some(channel) => channel.isPaused
      case None          => false
    results.toEither.foreach { r =>
      if show.taskValues then printSettings(r, show.print) else ()
    }
    if !isPaused && show.success && !state.get(suppressShow).getOrElse(false) then
      printSuccess(start, stop, extracted, success, cacheSummary, log)
    else ()

  private def currentChannel(state: State): Option[CommandChannel] =
    state.currentCommand match
      case Some(exec) =>
        exec.source match
          case Some(source) =>
            StandardMain.exchange.channels.find(_.name == source.channelName)
          case _ => None
      case _ => None

  def timedRun[A](
      s: State,
      ts: Values[Task[A]],
      extra: DummyTaskMap,
  ): Complete[A] =
    import EvaluateTask._
    import std.TaskExtra._
    val extracted = Project.extract(s)
    import extracted.structure
    val toRun = ts.map { case KeyValue(k, t) => t.map(v => KeyValue(k, v)) }.join
    val roots = ts.map { case KeyValue(k, _) => k }
    val config = extractedTaskConfig(extracted, structure, s)
    val start = System.currentTimeMillis
    Def.cacheEventLog.clear()
    val (newS, result) = withStreams(structure, s): str =>
      val transform = nodeView(s, str, roots, extra)
      runTask(toRun, s, str, structure.index.triggers, config)(using transform)
    val stop = System.currentTimeMillis
    val cacheSummary = Def.cacheEventLog.summary
    Complete(start, stop, result, cacheSummary, newS)

  def runTasks[A1](
      s: State,
      ts: Values[Task[A1]],
      extra: DummyTaskMap,
      show: ShowConfig
  )(using display: Show[ScopedKey[?]]): State =
    val complete = timedRun[A1](s, ts, extra)
    showRun(complete, show)
    complete.results match
      case Result.Inc(i)   => complete.state.handleError(i)
      case Result.Value(_) => complete.state

  def printSuccess(
      start: Long,
      stop: Long,
      extracted: Extracted,
      success: Boolean,
      cacheSummary: String,
      log: Logger,
  ): Unit =
    import extracted.*
    def get(key: SettingKey[Boolean]): Boolean =
      (currentRef / key).get(structure.data) getOrElse true
    if get(showSuccess) then
      if get(showTiming) then
        val msg = timingString(start, stop, structure.data, currentRef) + (
          if cacheSummary == "" then ""
          else ", " + cacheSummary
        )
        if success then log.success(msg)
        else if Terminal.get.isSuccessEnabled then log.error(msg)
      else if success then log.success("")
    else ()

  private def timingString(
      startTime: Long,
      endTime: Long,
      data: Settings,
      currentRef: ProjectRef,
  ): String = {
    val format = (currentRef / timingFormat).get(data) getOrElse defaultFormat
    timing(format, startTime, endTime)
  }

  def timing(format: java.text.DateFormat, startTime: Long, endTime: Long): String =
    val total = (endTime - startTime + 500) / 1000
    val totalString = s"$total s" +
      (if total <= 60 then ""
       else {
         val maybeHours = total / 3600 match
           case 0 => ""
           case h => f"$h%02d:"
         val mins = f"${total % 3600 / 60}%02d"
         val secs = f"${total % 60}%02d"
         s" ($maybeHours$mins:$secs)"
       })
    s"elapsed time: $totalString"

  def defaultFormat: DateFormat = {
    import java.text.DateFormat
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
  }

  def applyDynamicTasks[I](
      s: State,
      inputs: Values[InputTask[I]],
      show: ShowConfig
  )(using display: Show[ScopedKey[?]]): Parser[() => State] = {
    val parsers =
      for (KeyValue(k, it) <- inputs)
        yield it.parser(s).map(v => KeyValue(k, v))
    Command.applyEffect(seq(parsers)) { roots =>
      runTasks(s, roots, DummyTaskMap(Nil), show)
    }
  }

  def evaluatingParser(s: State, show: ShowConfig)(keys: Seq[KeyValue[?]])(using
      display: Show[ScopedKey[?]]
  ): Parser[() => State] = {

    // to make the call sites clearer
    def separate[L](in: Seq[KeyValue[?]])(
        f: KeyValue[?] => Either[KeyValue[L], KeyValue[?]]
    ): (Seq[KeyValue[L]], Seq[KeyValue[?]]) =
      Util.separate(in)(f)

    val kvs = keys.toList
    if (kvs.isEmpty) failure("No such setting/task")
    else {
      val (inputTasks, other) = separate[InputTask[?]](kvs) {
        case KeyValue(k, v: InputTask[_]) => Left(KeyValue(k, v))
        case kv                           => Right(kv)
      }
      val (tasks, settings) = separate[Task[?]](other) {
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
          applyDynamicTasks(
            s,
            inputTasks.map { case KeyValue(k, v: InputTask[a]) => KeyValue(k, castToAny(v)) },
            show
          )
      } else {
        val base =
          if (tasks.isEmpty) success(() => s)
          else
            applyTasks(s, maps(tasks)(x => success(castToAny(x))), show)
        base.map { res => () =>
          val newState = res()
          if (show.settingValues && settings.nonEmpty) printSettings(settings, show.print)
          newState
        }
      }
    }
  }

  // this is a hack to avoid duplicating method implementations
  private def castToAny[F[_]]: [a] => F[a] => F[Any] = [a] => (fa: F[a]) => fa.asInstanceOf[F[Any]]

  private def maps[T, S](vs: Values[T])(f: T => S): Values[S] =
    vs map { case KeyValue(k, v) => KeyValue(k, f(v)) }

  def projectAggregates[Proj](
      proj: Option[Reference],
      extra: BuildUtil[Proj],
      reverse: Boolean
  ): Seq[ProjectRef] =
    val resRef = proj.map(p => extra.projectRefFor(extra.resolveRef(p)))
    resRef.toList.flatMap { ref =>
      if reverse then extra.aggregates.reverse(ref)
      else extra.aggregates.forward(ref)
    }

  /**
   * Compute the reverse aggregate keys of all the `keys` at once.
   * This is more performant than computing the revere aggregate keys of each key individually
   * because of the duplicates. One aggregate key is the aggregation of many keys.
   */
  def reverseAggregate[Proj](
      keys: Set[ScopedKey[?]],
      extra: BuildUtil[Proj],
  ): Iterable[ScopedKey[?]] =
    val mask = ScopeMask()
    def recur(keys: Set[ScopedKey[?]], acc: Set[ScopedKey[?]]): Set[ScopedKey[?]] =
      if keys.isEmpty then acc
      else
        val aggKeys = for
          key <- keys
          ref <- projectAggregates(key.scope.project.toOption, extra, reverse = true)
          toResolve = key.scope.copy(project = Select(ref))
          resolved = Resolve(extra, Zero, key.key, mask)(toResolve)
          scoped = ScopedKey(resolved, key.key)
          if !acc.contains(scoped)
        yield scoped
        val filteredAggKeys = aggKeys.filter(aggregationEnabled(_, extra.data))
        // recursive because an aggregate project can be aggregated in another aggregate project
        recur(filteredAggKeys, acc ++ filteredAggKeys)
    recur(keys, keys)

  def aggregate[A1, Proj](
      key: ScopedKey[A1],
      rawMask: ScopeMask,
      extra: BuildUtil[Proj]
  ): Seq[ScopedKey[A1]] =
    val mask = rawMask.copy(project = true)
    Dag.topologicalSort(key): (k) =>
      if aggregationEnabled(k, extra.data) then aggregatedKeys(k, extra, mask) else Nil

  def aggregatedKeys[T](
      key: ScopedKey[T],
      extra: BuildUtil[?],
      mask: ScopeMask
  ): Seq[ScopedKey[T]] =
    projectAggregates(key.scope.project.toOption, extra, reverse = false) map { ref =>
      val toResolve = key.scope.copy(project = Select(ref))
      val resolved = Resolve(extra, Zero, key.key, mask)(toResolve)
      ScopedKey(resolved, key.key)
    }

  def aggregationEnabled(key: ScopedKey[?], data: Settings): Boolean =
    (Scope.fillTaskAxis(key.scope, key.key) / Keys.aggregate).get(data).getOrElse(true)
  private[sbt] val suppressShow =
    AttributeKey[Boolean]("suppress-aggregation-show", Int.MaxValue)
}
