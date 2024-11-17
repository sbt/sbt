/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import sbt.Def.{ ScopedKey, Setting, dummyState }
import sbt.Keys.{ TaskProgress => _, name => _, _ }
import sbt.BuildExtra.*
import sbt.ProjectExtra.*
import sbt.Scope.Global
import sbt.SlashSyntax0.given
import sbt.internal.Aggregation.KeyValue
import sbt.internal.TaskName._
import sbt.internal._
import sbt.internal.langserver.ErrorCodes
import sbt.internal.util.{ Terminal => ITerminal, _ }
import sbt.librarymanagement.{ Resolver, UpdateReport }
import sbt.std.Transform.DummyTaskMap
import sbt.util.{ Logger, Show }
import sbt.internal.bsp.BuildTargetIdentifier

import scala.annotation.nowarn
import scala.Console.RED
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import xsbti.FileConverter

/**
 * An API that allows you to cancel executing tasks upon some signal.
 *
 *  For example, this is implemented by the TaskEngine;
 *  invoking `cancelAndShutdown()` allows you to cancel the current task execution.
 */
trait RunningTaskEngine {

  /** Attempts to kill and shutdown the running task engine. */
  def cancelAndShutdown(): Unit
}

/**
 * A strategy for being able to cancel tasks.
 *
 * Implementations of this trait determine what will trigger `cancel()` for
 * the task engine, providing in the `start` method.
 *
 * All methods on this API are expected to be called from the same thread.
 */
trait TaskCancellationStrategy {

  /** The state used by this task. */
  type State

  /**
   * Called when task evaluation starts.
   *
   * @param canceller An object that can cancel the current task evaluation session.
   * @return Whatever state you need to cleanup in your finish method.
   */
  def onTaskEngineStart(canceller: RunningTaskEngine): State

  /** Called when task evaluation completes, either in success or failure. */
  def onTaskEngineFinish(state: State): Unit
}

object TaskCancellationStrategy {

  /** An empty handler that does not cancel tasks. */
  object Null extends TaskCancellationStrategy {
    type State = Unit
    def onTaskEngineStart(canceller: RunningTaskEngine): Unit = ()
    def onTaskEngineFinish(state: Unit): Unit = ()
    override def toString: String = "Null"
  }

  /** Cancel handler which registers for SIGINT and cancels tasks when it is received. */
  object Signal extends TaskCancellationStrategy {
    type State = Signals.Registration

    def onTaskEngineStart(canceller: RunningTaskEngine): Signals.Registration =
      Signals.register(() => canceller.cancelAndShutdown())

    def onTaskEngineFinish(registration: Signals.Registration): Unit = registration.remove()

    override def toString: String = "Signal"
  }
}

/**
 * The new API for running tasks.
 *
 * This represents all the hooks possible when running the task engine.
 * We expose this trait so that we can, in a binary compatible way, modify what is used
 * inside this configuration and how to construct it.
 */
sealed trait EvaluateTaskConfig {
  def restrictions: Seq[Tags.Rule]
  def checkCycles: Boolean
  def progressReporter: ExecuteProgress
  def cancelStrategy: TaskCancellationStrategy

  /** If true, we force a finalizer/gc run (or two) after task execution completes when needed. */
  def forceGarbageCollection: Boolean

  /** Interval to force GC. */
  def minForcegcInterval: Duration
}

object EvaluateTaskConfig {

  /** Raw constructor for EvaluateTaskConfig. */
  def apply(
      restrictions: Seq[Tags.Rule],
      checkCycles: Boolean,
      progressReporter: ExecuteProgress,
      cancelStrategy: TaskCancellationStrategy,
      forceGarbageCollection: Boolean,
      minForcegcInterval: Duration
  ): EvaluateTaskConfig =
    DefaultEvaluateTaskConfig(
      restrictions,
      checkCycles,
      progressReporter,
      cancelStrategy,
      forceGarbageCollection,
      minForcegcInterval
    )

  private case class DefaultEvaluateTaskConfig(
      restrictions: Seq[Tags.Rule],
      checkCycles: Boolean,
      progressReporter: ExecuteProgress,
      cancelStrategy: TaskCancellationStrategy,
      forceGarbageCollection: Boolean,
      minForcegcInterval: Duration
  ) extends EvaluateTaskConfig
}

final case class PluginData(
    dependencyClasspath: Def.Classpath,
    definitionClasspath: Def.Classpath,
    resolvers: Option[Vector[Resolver]],
    report: Option[UpdateReport],
    scalacOptions: Seq[String],
    javacOptions: Seq[String],
    unmanagedSourceDirectories: Seq[File],
    unmanagedSources: Seq[File],
    managedSourceDirectories: Seq[File],
    managedSources: Seq[File],
    buildTarget: Option[BuildTargetIdentifier],
    converter: FileConverter,
) {
  val classpath: Def.Classpath = definitionClasspath ++ dependencyClasspath
}

object PluginData {
  private[sbt] def apply(dependencyClasspath: Def.Classpath, converter: FileConverter): PluginData =
    PluginData(dependencyClasspath, Nil, None, None, Nil, Nil, Nil, Nil, Nil, Nil, None, converter)
}

object EvaluateTask {
  import Keys.state
  import std.Transform

  @nowarn
  lazy private val sharedProgress = new TaskTimings(reportOnShutdown = true)
  def taskTimingProgress: Option[ExecuteProgress] =
    if (SysProp.taskTimingsOnShutdown) Some(sharedProgress)
    else None

  private val capturedThunk = new AtomicReference[() => Unit]()
  def onShutdown(): Unit = {
    val thunk = capturedThunk.getAndSet(null)
    if (thunk != null) thunk()
  }
  // our own implementation of shutdown hook, because the "sbt -timings help" command was not working with the JVM shutdown hook,
  // which is a little hard to control.
  def addShutdownHandler[A](thunk: () => A): Unit = {
    capturedThunk
      .set(() =>
        try {
          thunk()
          ()
        } catch {
          case NonFatal(e) =>
            System.err.println(s"Caught exception running shutdown hook: $e")
            e.printStackTrace(System.err)
        }
      )
  }

  lazy private val sharedTraceEvent = new TaskTraceEvent()
  def taskTraceEvent: Option[ExecuteProgress] =
    if (SysProp.traces) {
      Some(sharedTraceEvent)
    } else None

  // sbt-pgp calls this
  @deprecated("No longer used", "1.3.0")
  private[sbt] def defaultProgress(): ExecuteProgress = ExecuteProgress.empty

  val SystemProcessors = Runtime.getRuntime.availableProcessors

  def extractedTaskConfig(
      extracted: Extracted,
      structure: BuildStructure,
      state: State
  ): EvaluateTaskConfig = {
    val rs = restrictions(extracted, structure)
    val canceller = cancelStrategy(extracted, structure, state)
    val progress = executeProgress(extracted, structure, state)
    val fgc = forcegc(extracted, structure)
    val mfi = minForcegcInterval(extracted, structure)
    EvaluateTaskConfig(rs, false, progress, canceller, fgc, mfi)
  }

  def defaultRestrictions(maxWorkers: Int) = Tags.limitAll(maxWorkers) :: Nil
  def defaultRestrictions(extracted: Extracted, structure: BuildStructure): Seq[Tags.Rule] =
    Tags.limitAll(maxWorkers(extracted, structure)) :: Nil

  def restrictions(state: State): Seq[Tags.Rule] = {
    val extracted = Project.extract(state)
    restrictions(extracted, extracted.structure)
  }

  def restrictions(extracted: Extracted, structure: BuildStructure): Seq[Tags.Rule] =
    getSetting(
      Keys.concurrentRestrictions,
      defaultRestrictions(extracted, structure),
      extracted,
      structure
    )

  def maxWorkers(extracted: Extracted, structure: BuildStructure): Int =
    if (getSetting(Keys.parallelExecution, true, extracted, structure))
      SystemProcessors
    else
      1

  def cancelable(extracted: Extracted, structure: BuildStructure): Boolean =
    getSetting(Keys.cancelable, false, extracted, structure)

  def cancelStrategy(
      extracted: Extracted,
      structure: BuildStructure,
      state: State
  ): TaskCancellationStrategy =
    getSetting(
      Keys.taskCancelStrategy,
      { (_: State) =>
        TaskCancellationStrategy.Null
      },
      extracted,
      structure
    )(state)

  private[sbt] def executeProgress(
      extracted: Extracted,
      structure: BuildStructure,
      state: State
  ): ExecuteProgress2 = {
    state
      .get(currentCommandProgress)
      .map { progress =>
        new ExecuteProgress2 {
          override def beforeCommand(cmd: String, state: State): Unit =
            progress.beforeCommand(cmd, state)
          override def afterCommand(cmd: String, result: Either[Throwable, State]): Unit =
            progress.afterCommand(cmd, result)
          override def initial(): Unit = progress.initial()
          override def afterRegistered(
              task: TaskId[?],
              allDeps: Iterable[TaskId[?]],
              pendingDeps: Iterable[TaskId[?]]
          ): Unit =
            progress.afterRegistered(task, allDeps, pendingDeps)
          override def afterReady(task: TaskId[?]): Unit = progress.afterReady(task)
          override def beforeWork(task: TaskId[?]): Unit = progress.beforeWork(task)
          override def afterWork[A](task: TaskId[A], result: Either[TaskId[A], Result[A]]): Unit =
            progress.afterWork(task, result)
          override def afterCompleted[A](task: TaskId[A], result: Result[A]): Unit =
            progress.afterCompleted(task, result)
          override def afterAllCompleted(results: RMap[TaskId, Result]): Unit =
            progress.afterAllCompleted(results)
          override def stop(): Unit = {
            // TODO: this is not a typo, but a questionable decision in 6559c3a0 that is probably obsolete
          }
        }
      }
      .getOrElse {
        val maker: Seq[Keys.TaskProgress] = getSetting(
          Keys.progressReports,
          Seq(),
          extracted,
          structure
        )
        val reporters = maker.map(_.progress) ++ state.get(Keys.taskProgress) ++
          (if (SysProp.taskTimings)
             new TaskTimings(reportOnShutdown = false, state.globalLogging.full) :: Nil
           else Nil)
        val cmdProgress = getSetting(Keys.commandProgress, Seq(), extracted, structure)
        ExecuteProgress2.aggregate(reporters match {
          case xs if xs.isEmpty   => cmdProgress
          case xs if xs.size == 1 => cmdProgress :+ new ExecuteProgressAdapter(xs.head)
          case xs => cmdProgress :+ new ExecuteProgressAdapter(ExecuteProgress.aggregate(xs))
        })
      }
  }
  // TODO - Should this pull from Global or from the project itself?
  private[sbt] def forcegc(extracted: Extracted, structure: BuildStructure): Boolean =
    getSetting((Global / Keys.forcegc), GCUtil.defaultForceGarbageCollection, extracted, structure)
  // TODO - Should this pull from Global or from the project itself?
  private[sbt] def minForcegcInterval(extracted: Extracted, structure: BuildStructure): Duration =
    getSetting(
      (Global / Keys.minForcegcInterval),
      GCUtil.defaultMinForcegcInterval,
      extracted,
      structure
    )

  def getSetting[T](
      key: SettingKey[T],
      default: T,
      extracted: Extracted,
      structure: BuildStructure
  ): T =
    (extracted.currentRef / key).get(structure.data).getOrElse(default)

  def injectSettings: Seq[Setting[?]] = Seq(
    Global / state ::= dummyState,
    Global / streamsManager ::= Def.dummyStreamsManager,
    Global / executionRoots ::= dummyRoots,
  )

  @deprecated("Use variant which doesn't take a logger", "1.1.1")
  def evalPluginDef(log: Logger)(pluginDef: BuildStructure, state: State): PluginData =
    evalPluginDef(pluginDef, state)

  def evalPluginDef(pluginDef: BuildStructure, state: State): PluginData = {
    val root = ProjectRef(pluginDef.root, Load.getRootProject(pluginDef.units)(pluginDef.root))
    val config = extractedTaskConfig(Project.extract(state), pluginDef, state)
    val evaluated =
      apply(pluginDef, ScopedKey(pluginData.scope, pluginData.key), state, root, config)
    val (newS, result) = evaluated getOrElse sys.error(
      "Plugin data does not exist for plugin definition at " + pluginDef.root
    )
    Project.runUnloadHooks(newS) // discard states
    processResult2(result)
  }

  /**
   * Evaluates `taskKey` and returns the new State and the result of the task wrapped in Some.
   * If the task is not defined, None is returned.  The provided task key is resolved against the current project `ref`.
   * Task execution is configured according to settings defined in the loaded project.
   */
  def apply[T](
      structure: BuildStructure,
      taskKey: ScopedKey[Task[T]],
      state: State,
      ref: ProjectRef
  ): Option[(State, Result[T])] =
    apply[T](
      structure,
      taskKey,
      state,
      ref,
      extractedTaskConfig(Project.extract(state), structure, state)
    )

  /**
   * Evaluates `taskKey` and returns the new State and the result of the task wrapped in Some.
   * If the task is not defined, None is returned.  The provided task key is resolved against the current project `ref`.
   * `config` configures concurrency and canceling of task execution.
   */
  def apply[T](
      structure: BuildStructure,
      taskKey: ScopedKey[Task[T]],
      state: State,
      ref: ProjectRef,
      config: EvaluateTaskConfig
  ): Option[(State, Result[T])] = {
    withStreams(structure, state) { str =>
      for ((task, toNode) <- getTask(structure, taskKey, state, str, ref))
        yield runTask(task, state, str, structure.index.triggers, config)(using toNode)
    }
  }

  def logIncResult(result: Result[?], state: State, streams: Streams) = result match {
    case Result.Inc(i) => logIncomplete(i, state, streams); case _ => ()
  }

  def logIncomplete(result: Incomplete, state: State, streams: Streams): Unit = {
    val all = Incomplete.linearize(result)
    val keyed =
      all collect { case Incomplete(Some(key: ScopedKey[_]), _, msg, _, ex) =>
        (key, msg, ex)
      }

    import ExceptionCategory._
    for case (key, msg, Some(ex)) <- keyed
    do
      def log = getStreams(key, streams).log
      ExceptionCategory(ex) match {
        case AlreadyHandled => ()
        case m: MessageOnly => if (msg.isEmpty) log.error(m.message)
        case f: Full        => log.trace(f.exception)
      }

    for ((key, msg, ex) <- keyed if msg.isDefined || ex.isDefined) {
      val msgString = (msg.toList ++ ex.toList.map(ErrorHandling.reducedToString)).mkString("\n\t")
      val log = getStreams(key, streams).log
      val display = contextDisplay(state, ITerminal.isColorEnabled)
      val errorMessage = "(" + display.show(key) + ") " + msgString
      state.respondError(ErrorCodes.InternalError, errorMessage)
      log.error(errorMessage)
    }
  }

  private def contextDisplay(state: State, highlight: Boolean) =
    Project.showContextKey(state, if (highlight) Some(RED) else None)

  def suppressedMessage(key: ScopedKey[?])(using display: Show[ScopedKey[?]]): String =
    "Stack trace suppressed.  Run 'last %s' for the full log.".format(display.show(key))

  def getStreams(key: ScopedKey[?], streams: Streams): TaskStreams =
    streams(ScopedKey(Project.fillTaskAxis(key).scope, Keys.streams.key))

  def withStreams[T](structure: BuildStructure, state: State)(f: Streams => T): T = {
    val str = std.Streams.closeable(structure.streams(state))
    try {
      f(str)
    } finally {
      str.close()
    }
  }

  def getTask[T](
      structure: BuildStructure,
      taskKey: ScopedKey[Task[T]],
      state: State,
      streams: Streams,
      ref: ProjectRef
  ): Option[(Task[T], NodeView)] = {
    val thisScope = Load.projectScope(ref)
    val subScoped = Project.replaceThis(thisScope)(taskKey.scopedKey)
    for (t <- structure.data.get(subScoped))
      yield (t, nodeView(state, streams, taskKey :: Nil))
  }
  def nodeView(
      state: State,
      streams: Streams,
      roots: Seq[ScopedKey[?]],
      dummies: DummyTaskMap = DummyTaskMap(Nil)
  ): NodeView =
    Transform(
      (dummyRoots, roots) :: (Def.dummyStreamsManager, streams) :: (dummyState, state) :: dummies
    )

  @deprecated("use StandardMain.exchange.withState to obtain an instance of State", "1.4.2")
  val lastEvaluatedState: AtomicReference[SafeState] = new AtomicReference()
  @deprecated("use currentlyRunningTaskEngine", "1.4.2")
  val currentlyRunningEngine: AtomicReference[(SafeState, RunningTaskEngine)] =
    new AtomicReference()
  private[sbt] val currentlyRunningTaskEngine: AtomicReference[RunningTaskEngine] =
    new AtomicReference()

  /**
   * The main method for the task engine.
   * See also Aggregation.runTasks.
   */
  def runTask[T](
      root: Task[T],
      state: State,
      streams: Streams,
      triggers: Triggers,
      config: EvaluateTaskConfig
  )(using taskToNode: NodeView): (State, Result[T]) = {
    import ConcurrentRestrictions.{ cancellableCompletionService, tagged }

    val log = state.log
    log.debug(
      s"Running task... Cancel: ${config.cancelStrategy}, check cycles: ${config.checkCycles}, forcegc: ${config.forceGarbageCollection}"
    )
    val tags = tagged(Tags.predicate(config.restrictions))
    val (service, shutdownThreads) =
      cancellableCompletionService(
        tags,
        (s: String) => log.warn(s),
        (t: TaskId[?]) => t.tags.contains(Tags.Sentinel)
      )

    def shutdownImpl(force: Boolean): Unit = {
      // First ensure that all threads are stopped for task execution.
      shutdownThreads(force)
      config.progressReporter.stop()

      // Now we run the gc cleanup to force finalizers to clear out file handles (yay GC!)
      if (config.forceGarbageCollection) {
        GCUtil.forceGcWithInterval(config.minForcegcInterval, log)
      }
    }
    def shutdown(): Unit = shutdownImpl(false)
    // propagate the defining key for reporting the origin
    def overwriteNode(i: Incomplete): Boolean = i.node match {
      case Some(t: Task[?]) => transformNode(t).isEmpty
      case _                => true
    }
    def suspendChannel[A1](
        state: State,
        result: Result[A1]
    ): Unit =
      (state.getSetting(Global / Keys.bgJobService), result) match
        case (Some(service), Result.Value(List(KeyValue(_, EmulateForeground(handle))))) =>
          state.remainingCommands match
            case Nil => service.waitForTry(handle).get
            case _   => service.pauseChannelDuringJob(state, handle)
        case _ => ()
    def run() = {
      val x = new Execute(
        Execute.config(config.checkCycles, overwriteNode),
        triggers,
        config.progressReporter
      )
      val (newState, result) =
        try {
          given strategy: CompletionService = service
          val results = x.runKeep(root)
          storeValuesForPrevious(results, state, streams)
          applyResults(results, state, root)
        } catch {
          case inc: Incomplete => (state, Result.Inc(inc))
        } finally shutdown()
      val replaced = transformInc(result)
      logIncResult(replaced, state, streams)
      suspendChannel(newState, replaced)
      (newState, replaced)
    }
    object runningEngine extends RunningTaskEngine {
      def cancelAndShutdown(): Unit = {
        println("")
        log.warn("Canceling execution...")
        RunningProcesses.killAll()
        ConcurrentRestrictions.cancelAll()
        shutdownImpl(true)
      }
    }
    currentlyRunningTaskEngine.set(runningEngine)
    // Register with our cancel handler we're about to start.
    val strat = config.cancelStrategy
    val cancelState = strat.onTaskEngineStart(runningEngine)
    config.progressReporter.initial()
    try run()
    finally {
      strat.onTaskEngineFinish(cancelState)
      currentlyRunningTaskEngine.set(null)
    }
  }

  private def storeValuesForPrevious(
      results: RMap[TaskId, Result],
      state: State,
      streams: Streams
  ): Unit =
    for (referenced <- (Global / Previous.references).get(Project.structure(state).data))
      Previous.complete(referenced, results, streams)

  def applyResults[T](
      results: RMap[TaskId, Result],
      state: State,
      root: Task[T]
  ): (State, Result[T]) = {
    (stateTransform(results)(state), results(root))
  }
  def stateTransform(results: RMap[TaskId, Result]): State => State =
    Function.chain(
      results.toTypedSeq flatMap {
        case results.TPair(_, Result.Value(KeyValue(_, st: StateTransform))) => Some(st.transform)
        case results.TPair(task: Task[?], Result.Value(v)) => task.post(v).get(transformState)
        case _                                             => Nil
      }
    )

  def transformInc[T](result: Result[T]): Result[T] =
    // taskToKey needs to be before liftAnonymous.  liftA only lifts non-keyed (anonymous) Incompletes.
    result.toEither.left.map { i =>
      Incomplete.transformBU(i)(convertCyclicInc andThen taskToKey andThen liftAnonymous)
    }
  def taskToKey: Incomplete => Incomplete = {
    case in @ Incomplete(Some(node: Task[?]), _, _, _, _) => in.copy(node = transformNode(node))
    case i                                                => i
  }

  def convertCyclicInc: Incomplete => Incomplete = {
    case in @ Incomplete(
          _,
          _,
          _,
          _,
          Some(c: Execute#CyclicException)
        ) =>
      in.copy(directCause = Some(new RuntimeException(convertCyclic(c))))
    case i => i
  }

  def convertCyclic(c: Execute#CyclicException): String =
    (c.caller, c.target) match {
      case (caller: Task[?], target: Task[?]) =>
        c.toString + (if (caller eq target) "(task: " + name(caller) + ")"
                      else "(caller: " + name(caller) + ", target: " + name(target) + ")")
      case _ => c.toString
    }

  def liftAnonymous: Incomplete => Incomplete = {
    case i @ Incomplete(_, _, None, causes, None) =>
      causes.find(inc =>
        inc.node.isEmpty && (inc.message.isDefined || inc.directCause.isDefined)
      ) match {
        case Some(lift) => i.copy(directCause = lift.directCause, message = lift.message)
        case None       => i
      }
    case i => i
  }

  @deprecated("Use processResult2 which doesn't take the unused log param", "1.1.1")
  def processResult[T](result: Result[T], log: Logger, show: Boolean = false): T =
    processResult2(result, show)

  def processResult2[T](result: Result[T], show: Boolean = false): T =
    onResult(result) { v =>
      if (show) println("Result: " + v); v
    }

  @deprecated("Use variant that doesn't take log", "1.1.1")
  def onResult[T, S](result: Result[T], log: Logger)(f: T => S): S = onResult(result)(f)

  def onResult[T, S](result: Result[T])(f: T => S): S =
    result match {
      case Result.Value(v) => f(v)
      case Result.Inc(inc) => throw inc
    }

  // if the return type Seq[Setting[_]] is not explicitly given, scalac hangs
  val injectStreams: ScopedKey[?] => Seq[Setting[?]] = scoped =>
    if (scoped.key == streams.key) {
      Seq(scoped.scope / streams := {
        (streamsManager.map { mgr =>
          val stream = mgr(scoped)
          stream.open()
          stream
        }).value
      })
    } else {
      Nil
    }
}
