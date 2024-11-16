/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.{ ByteArrayInputStream, IOException, InputStream, File => _ }
import java.nio.file.Path
import java.util.concurrent.{
  ConcurrentHashMap,
  CountDownLatch,
  LinkedBlockingQueue,
  TimeUnit,
  TimeoutException
}
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import sbt.BasicCommandStrings._
import sbt.Def._
import sbt.Keys._
import sbt.ProjectExtra.extract
import sbt.SlashSyntax0.given
import sbt.internal.Continuous.{ ContinuousState, FileStampRepository }
import sbt.internal.LabeledFunctions._
import sbt.internal.io.WatchState
import sbt.internal.nio._
import sbt.internal.ui.UITask
import sbt.internal.util.JoinThread._
import sbt.internal.util.complete.DefaultParsers.Space
import sbt.internal.util.complete.Parser._
import sbt.internal.util.complete.{ Parser, Parsers }
import sbt.internal.util._
import sbt.nio.Keys.{ fileInputs, _ }
import sbt.nio.Watch.{ Creation, Deletion, ShowOptions, Update }
import sbt.nio.file.{ FileAttributes, Glob }
import sbt.nio.{ FileStamp, FileStamper, Watch }
import sbt.util.{ Level, _ }

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration.FiniteDurationIsOrdered
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal
import scala.annotation.nowarn

/**
 * Provides the implementation of the `~` command and `watch` task. The implementation is quite
 * complex because we have to parse the command string to figure out which tasks we want to run.
 * Using the tasks, we then have to extract all of the settings for the continuous build. Finally
 * we have to aggregate the settings for each task into an aggregated watch config that will
 * sanely watch multiple tasks and respond to file updates and user input in a way that makes
 * sense for each of the tasks that are being monitored.
 *
 * The behavior, on the other hand, should be fairly straightforward. For example, if a user
 * wants to continuously run the compile task for projects a and b, then we create FileEventMonitor
 * instances for each product and watch all of the directories that contain compile sources
 * (as well as the source directories of transitive inter-project classpath dependencies). If
 * a change is detected in project a, then we should trigger a build for both projects a and b.
 *
 * The semantics are flexible and may be adapted. For example, a user may want to watch two
 * unrelated tasks and only rebuild the task with sources that have been changed. This could be
 * handled at the `~` level, but it probably makes more sense to build a better task caching
 * system so that we don't rerun tasks if their inputs have not changed. As of 1.3.0, the
 * semantics match previous sbt versions as closely as possible while allowing the user more
 * freedom to adjust the behavior to best suit their use cases.
 *
 * For now Continuous extends DeprecatedContinuous to minimize the number of deprecation warnings
 * produced by this file. In sbt 2.0, the DeprecatedContinuous mixin should be eliminated and
 * the deprecated apis should no longer be supported.
 */
private[sbt] object Continuous extends DeprecatedContinuous {
  private type Event = FileEvent[FileAttributes]

  /**
   * Provides the dynamic inputs to the continuous build callbacks that cannot be stored as
   * settings. This wouldn't need to exist if there was a notion of a lazy setting in sbt.
   *
   * @param logger the Logger
   * @param inputs the transitive task inputs
   */
  private[sbt] final class Arguments private[Continuous] (
      val logger: Logger,
      val inputs: Seq[DynamicInput]
  )

  /**
   * Create a function from InputStream => [[Watch.Action]] from a [[Parser]]. This is intended
   * to be used to set the watchInputHandler setting for a task.
   *
   * @param parser the parser
   * @return the function
   */
  private def defaultInputHandler(parser: Parser[Watch.Action]): InputStream => Watch.Action = {
    val builder = new StringBuilder
    val any = matched(Parsers.any.*)
    val fullParser = any ~> parser ~ any
    ((inputStream: InputStream) => parse(inputStream, builder, fullParser))
      .label("Continuous.defaultInputHandler")
  }

  /**
   * Implements continuous execution. It works by first parsing the command and generating a task to
   * run with each build. It can run multiple commands that are separated by ";" in the command
   * input. If any of these commands are invalid, the watch will immediately exit.
   *
   * @return a Command that can be used by sbt to implement continuous builds.
   */
  private[sbt] def continuous: Command =
    Command(ContinuousExecutePrefix, continuousBriefHelp, continuousDetail)(continuousParser) {
      case (s, (initialCount, commands)) =>
        val (s1, channel) = s.currentCommand.flatMap(_.source.map(_.channelName)) match {
          case Some(c) => s -> c
          case None    => StandardMain.exchange.run(s) -> ConsoleChannel.defaultName
        }
        val ws = ContinuousCommands.setupWatchState(channel, initialCount, commands, s1)
        s"${ContinuousCommands.runWatch} $channel" :: s"${ContinuousCommands.waitWatch} $channel" :: ws
    }

  @deprecated("The input task version of watch is no longer available", "1.4.0")
  private[sbt] def continuousTask: Def.Initialize[InputTask[StateTransform]] =
    Def.inputTask(StateTransform(identity))

  private[sbt] val dynamicInputs = taskKey[Option[mutable.Set[DynamicInput]]](
    "The input globs found during task evaluation that are used in watch."
  )

  private[sbt] def dynamicInputsImpl: Def.Initialize[Task[Option[mutable.Set[DynamicInput]]]] =
    Def.task(Keys.state.value.get(DynamicInputs))

  private[sbt] val DynamicInputs =
    AttributeKey[mutable.Set[DynamicInput]](
      "dynamic-inputs",
      "Stores the inputs (dynamic and regular) for a task",
      10000
    )

  private val continuousParser: State => Parser[(Int, Seq[String])] = {
    def toInt(s: String): Int = Try(s.toInt).getOrElse(0)

    // This allows us to re-enter the watch with the previous count.
    val digitParser: Parser[Int] =
      (Parsers.Space.* ~> matched(Parsers.Digit.+) <~ Parsers.Space.*).map(toInt)
    state =>
      val ocp = BasicCommands.multiParserImpl(Some(state)) |
        BasicCommands.otherCommandParser(state).map(_ :: Nil)
      (digitParser.? ~ ocp).flatMap {
        case (i, commands) if commands.exists(_.nonEmpty) =>
          Parser.success((i.getOrElse(0), commands.filter(_.nonEmpty)))
        case (_, _) => Parser.failure("Couldn't parse any commands")
      }
  }

  /**
   * Gets the [[Config]] necessary to watch a task. It will extract the internal dependency
   * configurations for the task (these are the classpath dependencies specified by
   * [[Project.dependsOn]]). Using these configurations and the settings map, it walks the
   * dependency graph for the key and extracts all of the transitive globs specified by the
   * inputs and triggers keys. It also extracts the legacy globs specified by the watchSources key.
   *
   * @param state the current [[State]] instance.
   * @param scopedKey the [[ScopedKey]] instance corresponding to the task we're monitoring
   * @param compiledMap the map of all of the build settings
   * @param extracted the [[Extracted]] instance for the build
   * @param logger a logger that can be used while generating the [[Config]]
   * @return the [[Config]] instance
   */
  private def getConfig(
      state: State,
      scopedKey: ScopedKey[?],
      compiledMap: CompiledMap,
      dynamicInputs: mutable.Set[DynamicInput],
  )(implicit extracted: Extracted, logger: Logger): Config = {

    // Extract all of the globs that we will monitor during the continuous build.
    val inputs = {
      val configs = scopedKey.get(internalDependencyConfigurations).getOrElse(Nil)
      import WatchTransitiveDependencies.{ Arguments => DArguments }
      val args = new DArguments(scopedKey, extracted, compiledMap, logger, configs, state)
      WatchTransitiveDependencies.transitiveDynamicInputs(args)
    }

    val repository = getRepository(state)
    dynamicInputs ++= inputs
    logger.debug(s"[watch] [${scopedKey.show}] Found inputs: ${inputs.map(_.glob).mkString(",")}")
    inputs.foreach(i => repository.register(i.glob).foreach(_.close()))
    val watchSettings = new WatchSettings(scopedKey)
    new Config(
      scopedKey.show,
      dynamicInputs,
      watchSettings
    )
  }

  private def getRepository(state: State): FileTreeRepository[FileAttributes] = {
    lazy val exception =
      new IllegalStateException("Tried to access FileTreeRepository for uninitialized state")
    state
      .get(globalFileTreeRepository)
      .getOrElse(throw exception)
  }

  private[sbt] def validateCommands(state: State, commands: Seq[String]): Unit = {
    commands.filter(cmd => Parser.parse(cmd, state.combinedParser).isLeft) match {
      case invalid if invalid.isEmpty =>
      case invalid =>
        val msg = s"Invalid commands: ${invalid.mkString("'", "', '", ",")}"
        throw new IllegalArgumentException(msg)
    }
  }

  // This is defined so we can assign a task key to a command to parse the WatchSettings.
  private val globalWatchSettingKey =
    taskKey[Unit]("Internal task key. Not actually used.").withRank(KeyRanks.Invisible)
  private def parseCommand(command: String, state: State): Seq[ScopedKey[?]] = {
    // Collect all of the scoped keys that are used to delegate the multi commands. These are
    // necessary to extract all of the transitive globs that we need to monitor during watch.
    // We have to add the <~ Parsers.any.* to ensure that we're able to extract the input key
    // from input tasks.
    val scopedKeyParser: Parser[Seq[ScopedKey[?]]] = Act.aggregatedKeyParser(state) <~ Parsers.any.*
    @tailrec def impl(current: String): Seq[ScopedKey[?]] = {
      Parser.parse(current, scopedKeyParser) match {
        case Right(scopedKeys: Seq[ScopedKey[_]]) => scopedKeys
        case Left(e) =>
          val aliases = BasicCommands.allAliases(state)
          aliases.collectFirst { case (`command`, aliased) => aliased } match {
            case Some(aliased) => impl(aliased)
            case None =>
              Parser.parse(command, state.combinedParser) match {
                case Right(_) => globalWatchSettingKey.scopedKey :: Nil
                case _ =>
                  val msg = s"Error attempting to extract scope from $command: $e."
                  throw new IllegalStateException(msg)
              }
          }
      }
    }
    impl(command)
  }

  private def getAllConfigs(
      state: State,
      commands: Seq[String],
      dynamicInputs: mutable.Set[DynamicInput],
  )(implicit extracted: Extracted, logger: Logger): Seq[Config] = {
    val commandKeys = commands.map(parseCommand(_, state))
    val compiledMap = WatchTransitiveDependencies.compile(extracted.structure)
    commandKeys.flatMap(_.map(getConfig(state, _, compiledMap, dynamicInputs)))
  }

  private[sbt] class Callbacks(
      val nextEvent: Int => Watch.Action,
      val beforeCommand: () => Unit,
      val onExit: () => Unit,
      val onStart: Int => Watch.Action,
      val onTermination: (Watch.Action, String, Int, State) => State
  )

  private[sbt] def getCallbacks(
      s: State,
      channel: CommandChannel,
      commands: Seq[String],
      fileStampCache: FileStamp.Cache,
      dynamicInputs: mutable.Set[DynamicInput],
      context: LoggerContext
  ): Callbacks = {
    given extracted: Extracted = Project.extract(s)
    given logger: Logger =
      context.logger(channel.name + "-watch", None, None)
    validateCommands(s, commands)
    val configs = getAllConfigs(s, commands, dynamicInputs)
    val appender = ConsoleAppender(channel.name + "-watch", channel.terminal)
    val level = configs.minBy(_.watchSettings.logLevel).watchSettings.logLevel
    context.addAppender(channel.name + "-watch", appender -> level)
    aggregate(
      configs,
      logger,
      channel,
      s,
      isCommand = true,
      commands,
      fileStampCache
    )
  }

  /**
   * Aggregates a collection of [[Config]] instances into a single instance of [[Callbacks]].
   * This allows us to monitor and respond to changes for all of
   * the inputs and triggers for each of the tasks that we are monitoring in the continuous build.
   * To monitor all of the inputs and triggers, it creates a monitor for each task
   * and then aggregates each of the individual monitor instances into an aggregated
   * instance. It aggregates all of the event callbacks into a single callback that delegates
   * to each of the individual callbacks. For the callbacks that return a [[Watch.Action]],
   * the aggregated callback will select the minimum [[Watch.Action]] returned where the ordering
   * is such that the highest priority [[Watch.Action]] have the lowest values. Finally, to
   * handle user input, we read from the provided input stream and buffer the result. Each
   * task's input parser is then applied to the buffered result and, again, we return the minimum
   * [[Watch.Action]] returned by the parsers (when the parsers fail, they just return
   * [[Watch.Ignore]], which is the lowest priority [[Watch.Action]].
   *
   * @param configs the [[Config]] instances
   * @param logger the default sbt logger instance
   * @param state the current state
   * @param extracted the [[Extracted]] instance for the current build
   * @return the [[Callbacks]] to pass into [[Watch.apply]]
   */
  private def aggregate(
      configs: Seq[Config],
      logger: Logger,
      channel: CommandChannel,
      state: State,
      isCommand: Boolean,
      commands: Seq[String],
      fileStampCache: FileStamp.Cache
  )(implicit
      extracted: Extracted
  ): Callbacks = {
    val project = extracted.currentRef
    val beforeCommand = () => configs.foreach(_.watchSettings.beforeCommand())
    val onStart: Int => Watch.Action =
      getOnStart(project, commands, configs, logger, extracted)
    val (message, parser, altParser) = getWatchInputOptions(configs, extracted)
    val nextInputEvent: WatchExecutor => Option[Watch.Action] = {
      parseInputEvents(parser, altParser, state, channel.terminal, logger)
    }
    val (nextFileEvent, cleanupFileMonitor): (
        Int => Option[(Watch.Event, Watch.Action)],
        () => Unit
    ) = getFileEvents(configs, logger, state, commands, fileStampCache, channel.name)
    val executor = new WatchExecutor(channel.name)
    val nextEvent: Int => Watch.Action =
      combineInputAndFileEvents(
        nextInputEvent,
        nextFileEvent,
        message,
        logger,
        logger,
        executor,
        channel
      )
    val onExit = () => {
      cleanupFileMonitor()
      Util.ignoreResult(executor.close())
    }
    val onTermination = getOnTermination(configs, isCommand)
    new Callbacks(nextEvent, beforeCommand, onExit, onStart, onTermination)
  }

  private def getOnTermination(
      configs: Seq[Config],
      isCommand: Boolean
  ): (Watch.Action, String, Int, State) => State = {
    configs.flatMap(_.watchSettings.onTermination).distinct match {
      case Seq(head, tail @ _*) =>
        tail.foldLeft(head) { case (onTermination, configOnTermination) =>
          (action, cmd, count, state) =>
            configOnTermination(action, cmd, count, onTermination(action, cmd, count, state))
        }
      case _ =>
        if (isCommand) Watch.defaultCommandOnTermination else Watch.defaultTaskOnTermination
    }
  }

  private def getWatchInputOptions(
      configs: Seq[Config],
      extracted: Extracted
  ): (String, Parser[Watch.Action], Option[(TaskKey[InputStream], InputStream => Watch.Action)]) = {
    configs match {
      case Seq(h) =>
        val settings = h.watchSettings
        val parser = settings.inputParser
        val alt = settings.inputStream.map { k =>
          k -> settings.inputHandler.getOrElse(defaultInputHandler(parser))
        }
        (settings.inputOptionsMessage, parser, alt)
      case _ =>
        val options =
          extracted.getOpt((ThisBuild / watchInputOptions)).getOrElse(Watch.defaultInputOptions)
        val message = extracted
          .getOpt((ThisBuild / watchInputOptionsMessage))
          .getOrElse(Watch.defaultInputOptionsMessage(options))
        val parser = extracted
          .getOpt((ThisBuild / watchInputParser))
          .getOrElse(Watch.defaultInputParser(options))
        val alt = extracted
          .getOpt((ThisBuild / watchInputStream))
          .map { _ =>
            (ThisBuild / watchInputStream) -> extracted
              .getOpt((ThisBuild / watchInputHandler))
              .getOrElse(defaultInputHandler(parser))
          }
        (message, parser, alt)
    }
  }

  @nowarn
  private def getOnStart(
      project: ProjectRef,
      commands: Seq[String],
      configs: Seq[Config],
      logger: Logger,
      extracted: Extracted,
  ): Int => Watch.Action = {
    val f: Int => Seq[Watch.Action] = count => {
      configs.map { params =>
        val ws = params.watchSettings
        ws.onIteration.map(_(count, project, commands)).getOrElse {
          if (configs.size == 1) { // Only allow custom start messages for single tasks
            ws.startMessage match {
              case Some(Left(sm))  => logger.info(sm(params.watchState(count)))
              case Some(Right(sm)) => sm(count, project, commands).foreach(logger.info(_))
              case None =>
                Watch.defaultStartWatch(count, project, commands).foreach(logger.info(_))
            }
          }
          Watch.Ignore
        }
      }
    }
    count => {
      val res = f(count).min
      // Print the default watch message if there are multiple tasks
      if (configs.size > 1) {
        val onStartWatch =
          extracted.getOpt((project / watchStartMessage)).getOrElse(Watch.defaultStartWatch)
        onStartWatch(count, project, commands).foreach(logger.info(_))
      }
      res
    }
  }

  @nowarn
  private def getFileEvents(
      configs: Seq[Config],
      logger: Logger,
      state: State,
      commands: Seq[String],
      fileStampCache: FileStamp.Cache,
      channel: String,
  )(implicit extracted: Extracted): (Int => Option[(Watch.Event, Watch.Action)], () => Unit) = {
    val trackMetaBuild = configs.forall(_.watchSettings.trackMetaBuild)
    val buildGlobs =
      if (trackMetaBuild) extracted.getOpt((checkBuildSources / fileInputs)).getOrElse(Nil)
      else Nil

    val retentionPeriod = configs.map(_.watchSettings.antiEntropyRetentionPeriod).max
    val quarantinePeriod = configs.map(_.watchSettings.deletionQuarantinePeriod).max
    val monitor: FileEventMonitor[Event] = new FileEventMonitor[Event] {

      private implicit class WatchLogger(val l: Logger) extends sbt.internal.nio.WatchLogger {
        override def debug(msg: Any): Unit = l.debug(msg.toString)
      }

      private val observers: Observers[Event] = new Observers
      private val repo = getRepository(state)
      private val handles = new java.util.ArrayList[AutoCloseable]
      handles.add(repo.addObserver(observers))
      private val eventMonitorObservers = new Observers[Event]
      private val configHandle: AutoCloseable =
        observers.addObserver { e =>
          // We only want to create one event per actual source file event. It doesn't matter
          // which of the config inputs triggers the event because they all will be used in
          // the onEvent callback above.
          configs.find(_.inputs().exists(_.glob.matches(e.path))) match {
            case Some(config) =>
              val configLogger = logger.withPrefix(config.command)
              configLogger.debug(s"Accepted event for ${e.path}")
              eventMonitorObservers.onNext(e)
            case None =>
          }
          if (trackMetaBuild && buildGlobs.exists(_.matches(e.path))) {
            val metaLogger = logger.withPrefix("build")
            metaLogger.debug(s"Accepted event for ${e.path}")
            eventMonitorObservers.onNext(e)
          }
        }
      if (trackMetaBuild) {
        state.get(CheckBuildSources.CheckBuildSourcesKey).flatMap(_.fileTreeRepository) match {
          case Some(r) => buildGlobs.foreach(r.register(_).foreach(observers.addObservable))
          case _       => buildGlobs.foreach(repo.register(_).foreach(_.close()))
        }
      }

      private val antiEntropyWindow = configs.map(_.watchSettings.antiEntropy).max
      private val monitor = FileEventMonitor.antiEntropy(
        eventMonitorObservers,
        antiEntropyWindow,
        logger,
        quarantinePeriod,
        retentionPeriod
      )

      private val antiEntropyPollPeriod =
        configs.map(_.watchSettings.antiEntropyPollPeriod).max
      override def poll(duration: Duration, filter: Event => Boolean): Seq[Event] = {
        monitor.poll(duration, filter) match {
          case s if s.nonEmpty =>
            val limit = antiEntropyWindow.fromNow
            /*
             * File events may come in bursts so we poll for a short time to see if there
             * are other changes detected in the burst. As soon as no changes are detected
             * during the polling window, we return all of the detected events. The polling
             * period is by default 5 milliseconds which is short enough to detect bursts
             * induced by commands like git rebase but fast enough to not lead to a noticable
             * increase in latency.
             */
            @tailrec def aggregate(res: Seq[Event]): Seq[Event] =
              if (limit.isOverdue) res
              else {
                monitor.poll(antiEntropyPollPeriod) match {
                  case s if s.nonEmpty => aggregate(res ++ s)
                  case _               => res
                }
              }
            aggregate(s)
          case s => s
        }
      }

      override def close(): Unit = {
        configHandle.close()
        handles.forEach(_.close())
        observers.close()
      }
    }
    val watchLogger: WatchLogger = msg => logger.debug(msg.toString)
    val antiEntropy = configs.map(_.watchSettings.antiEntropy).max
    val antiEntropyMonitor = FileEventMonitor.antiEntropy(
      monitor,
      antiEntropy,
      watchLogger,
      quarantinePeriod,
      retentionPeriod
    )

    val onEvent: (Int, Event) => Seq[(Watch.Event, Watch.Action)] = (count, event) => {
      val path = event.path

      def getWatchEvent(forceTrigger: Boolean): Option[Watch.Event] = {
        if (!event.exists) {
          Some(Deletion(event))
          fileStampCache.remove(event.path) match {
            case null => None
            case _    => Some(Deletion(event))
          }
        } else {
          fileStampCache.update(path, FileStamper.Hash) match {
            case (None, Some(_)) => Some(Creation(event))
            case (Some(_), None) => Some(Deletion(event))
            case (Some(p), Some(c)) =>
              if (forceTrigger) {
                val msg =
                  s"Creating forced update event for path $path (previous stamp: $p, current stamp: $c)"
                logger.debug(msg)
                Some(Update(event))
              } else if (p == c) {
                logger.debug(s"Dropping event for unmodified path $path")
                None
              } else {
                val msg =
                  s"Creating update event for modified $path (previous stamp: $p, current stamp: $c)"
                logger.debug(msg)
                Some(Update(event))
              }
            case _ => None
          }
        }
      }

      if (buildGlobs.exists(_.matches(path))) {
        getWatchEvent(forceTrigger = false).flatMap { e =>
          state.get(CheckBuildSources.CheckBuildSourcesKey) match {
            case Some(cbs) =>
              if (cbs.needsReload(state, Exec("", Some(CommandSource(channel)))))
                Some(e -> Watch.Reload)
              else None
            case None =>
              Some(e -> Watch.Reload)
          }
        }.toSeq
      } else {
        val acceptedConfigParameters = configs.flatMap { config =>
          config.inputs().flatMap {
            case i if i.glob.matches(path) =>
              Some((i.forceTrigger, i.fileStamper, config.watchSettings.onFileInputEvent))
            case _ => None
          }
        }
        if (acceptedConfigParameters.nonEmpty) {
          val useHash = acceptedConfigParameters.exists(_._2 == FileStamper.Hash)
          val forceTrigger = acceptedConfigParameters.exists(_._1)
          val watchEvent =
            if (useHash) getWatchEvent(forceTrigger)
            else {
              logger.debug(s"Trigger path detected $path")
              Some(
                if (!event.exists) Deletion(event)
                else if (fileStampCache.get(path).isDefined) Creation(event)
                else Update(event)
              )
            }
          acceptedConfigParameters.flatMap { case (_, _, callback) =>
            watchEvent.map(e => e -> callback(count, e))
          }
        } else Nil
      }
    }
    /*
     * This is a callback that will be invoked whenever onEvent returns a Trigger action. The
     * motivation is to allow the user to specify this callback via setting so that, for example,
     * they can clear the screen when the build triggers.
     */
    val onTrigger: (Int, Watch.Event) => Unit = { (count: Int, event: Watch.Event) =>
      if (configs.size == 1) {
        val config = configs.head
        config.watchSettings.triggerMessage match {
          case Left(tm)  => logger.info(tm(config.watchState(count)))
          case Right(tm) => tm(count, event.path, commands).foreach(logger.info(_))
        }
      } else {
        Watch.defaultOnTriggerMessage(count, event.path, commands).foreach(logger.info(_))
      }
    }

    (
      (count: Int) => {
        val interrupted = new AtomicBoolean(false)
        def getEvent: Option[(Watch.Event, Watch.Action)] = {
          val events =
            try antiEntropyMonitor.poll(Duration.Inf)
            catch { case _: InterruptedException => interrupted.set(true); Nil }
          val actions = events.flatMap(onEvent(count, _))
          if (actions.exists(_._2 != Watch.Ignore)) {
            val builder = new StringBuilder
            val min = actions.minBy { case (e, a) =>
              if (builder.nonEmpty) builder.append(", ")
              val path = e.path
              builder.append(path)
              builder.append(" -> ")
              builder.append(a.toString)
              a
            }
            logger.debug(s"Received file event actions: $builder. Returning: $min")
            if (min._2 == Watch.Trigger) onTrigger(count, min._1)
            if (min._2 == Watch.ShowOptions) None else Some(min)
          } else None
        }

        @tailrec def impl(): Option[(Watch.Event, Watch.Action)] = getEvent match {
          case None =>
            if (interrupted.get || Thread.interrupted) None
            else impl()
          case r => r
        }

        impl()
      },
      () => monitor.close()
    )
  }

  private class WatchExecutor(name: String) extends AutoCloseable {
    val id = new AtomicInteger(0)
    val threads = new java.util.Vector[Thread]
    val closed = new AtomicBoolean(false)
    def submit[R](tag: String, f: => R): WatchExecutor.Future[R] = {
      if (closed.get) new WatchExecutor.FutureFailed(new IllegalStateException("closed executor"))
      else {
        val queue = new LinkedBlockingQueue[Either[Unit, R]]
        val latch = new CountDownLatch(1)
        val runnable: Runnable = () => {
          try {
            latch.countDown()
            Util.ignoreResult(queue.offer(if (closed.get) Left(()) else Right(f)))
          } catch { case _: Exception => Util.ignoreResult(queue.offer(Left(()))) }
        }
        val thread = new Thread(runnable, s"sbt-watch-$name-$tag")
        thread.setDaemon(true)
        thread.start()
        threads.add(thread)
        new WatchExecutor.FutureImpl(thread, queue, this, latch)
      }
    }
    def removeThread(thread: Thread): Unit = Util.ignoreResult(threads.remove(thread))
    def close(): Unit = if (closed.compareAndSet(false, true)) {
      var exception: Option[InterruptedException] = None
      threads.forEach { thread =>
        try thread.joinFor(1.second)
        catch { case e: InterruptedException => exception = Some(e) }
      }
      threads.clear()
      exception.foreach(throw _)
    }
  }
  private object WatchExecutor {
    sealed trait Future[R] extends Any {
      def waitUntilStart(duration: FiniteDuration): Boolean
      def cancel(): Unit
      def result: Try[R]
    }
    final class FutureFailed[R](t: Throwable) extends Future[R] {
      override def waitUntilStart(duration: FiniteDuration): Boolean = false
      def cancel(): Unit = {}
      def result: Try[R] = Failure(t)
    }
    final class FutureImpl[R](
        thread: Thread,
        queue: LinkedBlockingQueue[Either[Unit, R]],
        executor: WatchExecutor,
        latch: CountDownLatch,
    ) extends Future[R] {
      override def waitUntilStart(duration: FiniteDuration): Boolean =
        latch.await(duration.toMillis, TimeUnit.MILLISECONDS)
      def cancel(): Unit = {
        executor.removeThread(thread)
        thread.joinFor(1.second)
      }
      def result: Try[R] =
        try
          queue.take match {
            case Right(r) => Success(r)
            case Left(_)  => Failure(new NullPointerException)
          }
        catch { case t: InterruptedException => Failure(t) }
    }
  }

  /**
   * Each task has its own input parser that can be used to modify the watch based on the input
   * read from System.in as well as a custom task-specific input stream that can be used as
   * an alternative source of control. In this method, we create two functions for each task,
   * one from `String => Seq[Watch.Action]` and another from `() => Seq[Watch.Action]`.
   * Each of these functions is invoked to determine the next state transformation for the watch.
   * The first function is a task specific copy of System.in. For each task we keep a mutable
   * buffer of the characters previously seen from System.in. Every time we receive new characters
   * we update the buffer and then try to parse a Watch.Action for each task. Any trailing
   * characters are captured and can be used for the next trigger. Because each task has a local
   * copy of the buffer, we do not have to worry about one task breaking parsing of another. We
   * also provide an alternative per task InputStream that is read in a similar way except that
   * we don't need to copy the custom InputStream which allows the function to be
   * `() => Seq[Watch.Action]` which avoids actually exposing the InputStream anywhere.
   */
  private def parseInputEvents(
      parser: Parser[Watch.Action],
      alternative: Option[(TaskKey[InputStream], InputStream => Watch.Action)],
      state: State,
      terminal: Terminal,
      logger: Logger,
  )(implicit extracted: Extracted): WatchExecutor => Option[Watch.Action] = {
    /*
     * This parses the buffer until all possible actions are extracted. By draining the input
     * to a state where it does not parse an action, we can wait until we receive new input
     * to attempt to parse again.
     */
    // Transform the Config.watchSettings.inputParser instances to functions of type
    // String => Watch.Action. The String that is provided will contain any characters that
    // have been read from stdin. If there are any characters available, then it calls the
    // parse method with the InputStream set to a ByteArrayInputStream that wraps the input
    // string. The parse method then appends those bytes to a mutable buffer and attempts to
    // parse the buffer. To make this work with streaming input, we prefix the parser with any.*.
    // If the Config.watchSettings.inputStream is set, the same process is applied except that
    // instead of passing in the wrapped InputStream for the input string, we directly pass
    // in the inputStream provided by Config.watchSettings.inputStream.
    val inputHandler: String => Watch.Action = {
      val any = Parsers.any.*
      val fullParser = any ~> parser ~ matched(any)
      // Each parser gets its own copy of System.in that it can modify while parsing.
      val systemInBuilder = new StringBuilder

      def inputStream(string: String): InputStream = new ByteArrayInputStream(string.getBytes)

      // This string is provided in the closure below by reading from System.in
      val default: String => Watch.Action =
        string => parse(inputStream(string), systemInBuilder, fullParser)
      val alt = alternative
        .map { case (key, handler) =>
          val is = extracted.runTask(key, state)._2
          () => handler(is)
        }
        .getOrElse(() => Watch.Ignore)
      (string: String) =>
        ((if (string.nonEmpty) default(string) else Watch.Ignore) :: alt() :: Nil).min
    }
    executor => {
      val interrupted = new AtomicBoolean(false)
      @tailrec def read(): Int = {
        if (terminal.name.startsWith("network")) terminal.inputStream.read
        else if (Terminal.canPollSystemIn || terminal.inputStream.available > 0)
          terminal.inputStream.read
        else {
          Thread.sleep(50)
          read()
        }
      }
      @tailrec def impl(): Option[Watch.Action] = {
        val action =
          try {
            interrupted.set(false)
            read() match {
              case -1   => throw new InterruptedException
              case 3    => Watch.CancelWatch // ctrl+c on windows
              case byte => inputHandler(byte.toChar.toString)
            }
          } catch {
            case _: InterruptedException =>
              interrupted.set(true)
              Watch.Ignore
          }
        action match {
          case Watch.Ignore =>
            val stop = interrupted.get || Thread.interrupted
            if (!stop) impl()
            else None
          case r => Some(r)
        }
      }

      terminal.withRawInput(impl())
    }
  }

  private def combineInputAndFileEvents(
      nextInputAction: WatchExecutor => Option[Watch.Action],
      nextFileEvent: Int => Option[(Watch.Event, Watch.Action)],
      options: String,
      logger: Logger,
      rawLogger: Logger,
      executor: WatchExecutor,
      channel: CommandChannel
  ): Int => Watch.Action = count => {
    val events = new LinkedBlockingQueue[Either[Watch.Action, (Watch.Event, Watch.Action)]]

    val inputJob =
      executor
        .submit(s"handle-input-$count", nextInputAction(executor).foreach(a => events.put(Left(a))))
    val fileJob =
      executor
        .submit(s"get-file-events-$count", nextFileEvent(count).foreach(e => events.put(Right(e))))
    val signalRegistration = channel match {
      case _: ConsoleChannel => Some(Signals.register(() => events.put(Left(Watch.CancelWatch))))
      case _                 => None
    }
    try {
      if (!inputJob.waitUntilStart(1.second) || !fileJob.waitUntilStart(1.second)) {
        inputJob.cancel()
        fileJob.cancel()
        new Watch.HandleError(new TimeoutException)
      } else {
        val (inputAction: Watch.Action, fileEvent: Option[(Watch.Event, Watch.Action)]) =
          events.take() match {
            case Left(a)  => (a, None)
            case Right(e) => (Watch.Ignore, Some(e))
          }
        val min: Watch.Action = (fileEvent.map(_._2).toSeq :+ inputAction).min
        lazy val inputMessage =
          s"Received input event: $inputAction." +
            (if (inputAction != min) s" Dropping in favor of file event: $min" else "")
        if (inputAction != Watch.Ignore) logger.info(inputMessage)
        fileEvent
          .collect {
            case (event, action) if action != Watch.Ignore =>
              s"Received file event $action for $event." +
                (if (action != min) s" Dropping in favor of input event: $min" else "")
          }
          .foreach(logger.debug(_))
        min match {
          case ShowOptions =>
            rawLogger.info(options)
            Watch.Ignore
          case m => m
        }
      }
    } finally {
      signalRegistration.foreach(_.remove())
      inputJob.cancel()
      fileJob.cancel()
      ()
    }
  }

  @tailrec
  private final def parse(
      is: InputStream,
      builder: StringBuilder,
      parser: Parser[(Watch.Action, String)]
  ): Watch.Action = {
    if (is.available > 0) builder += is.read().toChar
    Parser.parse(builder.toString, parser) match {
      case Right((action, rest)) =>
        builder.clear()
        builder ++= rest
        action
      case _ if is.available > 0 => parse(is, builder, parser)
      case _                     => Watch.Ignore
    }
  }

  private type WatchOnEvent = (Int, Watch.Event) => Watch.Action

  /**
   * Contains all of the user defined settings that will be used to build a [[Callbacks]]
   * instance that is used to produce the arguments to [[Watch.apply]]. The
   * callback settings (e.g. onEvent or onInputEvent) come in two forms: those that return a
   * function from [[Arguments]] => F for some function type `F` and those that directly return a function, e.g.
   * `(Int, Boolean) => Watch.Action`. The former are a low level interface that will usually
   * be unspecified and automatically filled in by [[Continuous.aggregate]]. The latter are
   * intended to be user configurable and will be scoped to the input [[ScopedKey]]. To ensure
   * that the scoping makes sense, we first try and extract the setting from the [[ScopedKey]]
   * instance's task scope, which is the scope with the task axis set to the task key. If that
   * fails, we fall back on the task axis. To make this concrete, to get the logLevel for
   * `foo / Compile / compile` (which is a TaskKey with scope `foo / Compile`), we first try and
   * get the setting in the `foo / Compile / compile` scope. If logLevel is not set at the task
   * level, then we fall back to the `foo / Compile` scope.
   *
   * This has to be done by manually extracting the settings via [[Extracted]] because there is
   * no good way to automatically add a [[WatchSettings]] setting to every task in the build.
   * Thankfully these map retrievals are reasonably fast so there is not a significant runtime
   * performance penalty for creating the [[WatchSettings]] this way. The drawback is that we
   * have to manually resolve the settings in multiple scopes which may lead to inconsistencies
   * with scope resolution elsewhere in sbt.
   *
   * @param key the [[ScopedKey]] instance that sets the [[Scope]] for the settings we're extracting
   * @param extracted the [[Extracted]] instance for the build
   */
  private final class WatchSettings private[Continuous] (val key: ScopedKey[?])(implicit
      extracted: Extracted
  ) {
    val antiEntropy: FiniteDuration =
      key.get(watchAntiEntropy).getOrElse(Watch.defaultAntiEntropy)
    val antiEntropyRetentionPeriod: FiniteDuration =
      key
        .get(watchAntiEntropyRetentionPeriod)
        .getOrElse(Watch.defaultAntiEntropyRetentionPeriod)
    val deletionQuarantinePeriod: FiniteDuration =
      key.get(watchDeletionQuarantinePeriod).getOrElse(Watch.defaultDeletionQuarantinePeriod)
    val inputHandler: Option[InputStream => Watch.Action] = key.get(watchInputHandler)
    val inputOptions: Seq[Watch.InputOption] =
      key.get(watchInputOptions).getOrElse(Watch.defaultInputOptions)
    val inputOptionsMessage: String =
      key.get(watchInputOptionsMessage).getOrElse(Watch.defaultInputOptionsMessage(inputOptions))
    val inputParser: Parser[Watch.Action] =
      key.get(watchInputParser).getOrElse(Watch.defaultInputParser(inputOptions))
    val logLevel: Level.Value = key.get(watchLogLevel).getOrElse(Level.Info)
    val beforeCommand: () => Unit = key.get(watchBeforeCommand).getOrElse(() => {})
    val onFileInputEvent: WatchOnEvent =
      key.get(watchOnFileInputEvent).getOrElse(Watch.trigger)
    val onIteration: Option[(Int, ProjectRef, Seq[String]) => Watch.Action] =
      key.get(watchOnIteration)
    val onTermination: Option[(Watch.Action, String, Int, State) => State] =
      key.get(watchOnTermination)
    val startMessage: StartMessage = getStartMessage(key)
    val trackMetaBuild: Boolean =
      key.get(onChangedBuildSource).fold(false)(_ == ReloadOnSourceChanges)
    val triggerMessage: TriggerMessage = getTriggerMessage(key)

    // Unlike the rest of the settings, InputStream is a TaskKey which means that if it is set,
    // we have to use Extracted.runTask to get the value. The reason for this is because it is
    // logical that users may want to use a different InputStream on each task invocation. The
    // alternative would be SettingKey[() => InputStream], but that doesn't feel right because
    // one might want the InputStream to depend on other tasks.
    val inputStream: Option[TaskKey[InputStream]] = key.get(watchInputStream)
    val antiEntropyPollPeriod: FiniteDuration =
      key.get(watchAntiEntropyPollPeriod).getOrElse(Watch.defaultAntiEntropyPollPeriod)
  }

  /**
   * Container class for all of the components we need to setup a watch for a particular task or
   * input task.
   *
   * @param command       the name of the command/task to run with each iteration
   * @param dynamicInputs the transitive task inputs (see [[SettingsGraph]])
   * @param watchSettings the [[WatchSettings]] instance for the task
   */
  private final class Config(
      val command: String,
      val dynamicInputs: mutable.Set[DynamicInput],
      val watchSettings: WatchSettings,
  ):
    def inputs() = dynamicInputs.toSeq.sorted
    private[sbt] def watchState(count: Int): DeprecatedWatchState =
      WatchState.empty(inputs().map(_.glob)).withCount(count)

    def arguments(logger: Logger): Arguments = new Arguments(logger, inputs())
  end Config

  @nowarn
  private def getStartMessage(key: ScopedKey[?])(implicit e: Extracted): StartMessage = Some {
    lazy val default = key.get(watchStartMessage).getOrElse(Watch.defaultStartWatch)
    key.get(deprecatedWatchingMessage).map(Left(_)).getOrElse(Right(default))
  }

  @nowarn
  private def getTriggerMessage(
      key: ScopedKey[?]
  )(implicit e: Extracted): TriggerMessage = {
    lazy val default =
      key.get(watchTriggeredMessage).getOrElse(Watch.defaultOnTriggerMessage)
    key.get(deprecatedTriggeredMessage).map(Left(_)).getOrElse(Right(default))
  }

  extension (scope: Scope) {

    /**
     * This shows the [[Scope]] in the format that a user would likely type it in a build
     * or in the sbt console. For example, the key corresponding to the command
     * foo/Compile/compile will pretty print as "foo / Compile / compile", not
     * "ProjectRef($URI, foo) / compile / compile", where the ProjectRef part is just noise that
     * is rarely relevant for debugging.
     *
     * @return the pretty printed output.
     */
    private def show: String = {
      val mask = ScopeMask(
        config = scope.config.toOption.isDefined,
        task = scope.task.toOption.isDefined,
        extra = scope.extra.toOption.isDefined
      )
      Scope
        .displayMasked(
          scope,
          " ",
          (_: Reference) match {
            case p: ProjectRef => s"${p.project.trim} /"
            case _             => "Global /"
          },
          mask
        )
        .dropRight(3) // delete trailing "/"
        .trim
    }
  }

  extension (scopedKey: ScopedKey[?]) {

    /**
     * Gets the value for a setting key scoped to the wrapped [[ScopedKey]]. If the task axis is not
     * set in the [[ScopedKey]], then we first set the task axis and try to extract the setting
     * from that scope otherwise we fallback on the [[ScopedKey]] instance's scope. We use the
     * reverse order if the task is set.
     *
     * @param settingKey the [[SettingKey]] to extract
     * @param extracted  the provided [[Extracted]] instance
     * @tparam T the type of the [[SettingKey]]
     * @return the optional value of the [[SettingKey]] if it is defined at the input
     *         [[ScopedKey]] instance's scope or task scope.
     */
    private def get[T](settingKey: SettingKey[T])(implicit extracted: Extracted): Option[T] = {
      lazy val taskScope = Project.fillTaskAxis(scopedKey).scope
      scopedKey.scope match {
        case scope if scope.task.toOption.isDefined =>
          extracted.getOpt((scope / settingKey)) orElse extracted.getOpt((taskScope / settingKey))
        case scope =>
          extracted.getOpt((taskScope / settingKey)) orElse extracted.getOpt((scope / settingKey))
      }
    }

    /**
     * Gets the [[ScopedKey]] for a task scoped to the wrapped [[ScopedKey]]. If the task axis is
     * not set in the [[ScopedKey]], then we first set the task axis and try to extract the tak
     * from that scope otherwise we fallback on the [[ScopedKey]] instance's scope. We use the
     * reverse order if the task is set.
     *
     * @param taskKey   the [[TaskKey]] to extract
     * @param extracted the provided [[Extracted]] instance
     * @tparam T the type of the [[SettingKey]]
     * @return the optional value of the [[SettingKey]] if it is defined at the input
     *         [[ScopedKey]] instance's scope or task scope.
     */
    private def get[T](taskKey: TaskKey[T])(implicit extracted: Extracted): Option[TaskKey[T]] = {
      lazy val taskScope = Project.fillTaskAxis(scopedKey).scope
      scopedKey.scope match {
        case scope if scope.task.toOption.isDefined =>
          if (extracted.getOpt((scope / taskKey)).isDefined) Some((scope / taskKey))
          else if (extracted.getOpt((taskScope / taskKey)).isDefined) Some((taskScope / taskKey))
          else None
        case scope =>
          if (extracted.getOpt((taskScope / taskKey)).isDefined) Some((taskScope / taskKey))
          else if (extracted.getOpt((scope / taskKey)).isDefined) Some((scope / taskKey))
          else None
      }
    }

    /**
     * This shows the [[ScopedKey[_]] in the format that a user would likely type it in a build
     * or in the sbt console. For example, the key corresponding to the command
     * foo/Compile/compile will pretty print as "foo / Compile / compile", not
     * "ProjectRef($URI, foo) / compile / compile", where the ProjectRef part is just noise that
     * is rarely relevant for debugging.
     *
     * @return the pretty printed output.
     */
    private def show: String = s"${scopedKey.scope.show} / ${scopedKey.key}"
  }

  extension (logger: Logger) {

    /**
     * Creates a logger that adds a prefix to the messages that it logs. The motivation is so that
     * we can tell from which FileEventMonitor an event originated.
     *
     * @param prefix the string to prefix the message with
     * @return the wrapped Logger.
     */
    private def withPrefix(prefix: String): Logger = new Logger {
      override def trace(t: => Throwable): Unit = logger.trace(t)

      override def success(message: => String): Unit = logger.success(message)

      override def log(level: Level.Value, message: => String): Unit =
        logger.log(level, s"$prefix - $message")
    }
  }

  private[sbt] class FileStampRepository(
      fileStampCache: FileStamp.Cache,
      underlying: FileTreeRepository[FileAttributes]
  ) extends FileTreeRepository[FileAttributes] {
    def putIfAbsent(path: Path, stamper: FileStamper): (Option[FileStamp], Option[FileStamp]) =
      fileStampCache.putIfAbsent(path, stamper)
    override def list(path: Path): Seq[(Path, FileAttributes)] = underlying.list(path)
    override def addObserver(observer: Observer[FileEvent[FileAttributes]]): AutoCloseable =
      underlying.addObserver(observer)
    override def register(glob: Glob): Either[IOException, Observable[FileEvent[FileAttributes]]] =
      underlying.register(glob)
    override def close(): Unit = underlying.close()
  }

  private[sbt] final class ContinuousState(
      val count: Int,
      val commands: Seq[String],
      beforeCommandImpl: (State, mutable.Set[DynamicInput]) => State,
      val afterCommand: State => State,
      val afterWatch: State => State,
      val callbacks: Callbacks,
      val dynamicInputs: mutable.Set[DynamicInput],
      val pending: Boolean,
      var failAction: Option[Watch.Action],
  ) {
    def this(
        count: Int,
        commands: Seq[String],
        beforeCommandImpl: (State, mutable.Set[DynamicInput]) => State,
        afterCommand: State => State,
        afterWatch: State => State,
        callbacks: Callbacks,
        dynamicInputs: mutable.Set[DynamicInput],
        pending: Boolean,
    ) = this(
      count,
      commands,
      beforeCommandImpl,
      afterCommand,
      afterWatch,
      callbacks,
      dynamicInputs,
      pending,
      None
    )
    def beforeCommand(state: State): State = beforeCommandImpl(state, dynamicInputs)
    def incremented: ContinuousState = withCount(count + 1)
    def withPending(p: Boolean) =
      new ContinuousState(
        count,
        commands,
        beforeCommandImpl,
        afterCommand,
        afterWatch,
        callbacks,
        dynamicInputs,
        p
      )
    private def withCount(c: Int): ContinuousState =
      new ContinuousState(
        c,
        commands,
        beforeCommandImpl,
        afterCommand,
        afterWatch,
        callbacks,
        dynamicInputs,
        pending
      )
  }
}

private[sbt] object ContinuousCommands {
  private[sbt] def value: Seq[Command] = Vector(
    failWatchCommand,
    preWatchCommand,
    postWatchCommand,
    runWatchCommand,
    stopWatchCommand,
  )
  private[sbt] val watchStateCallbacks =
    AttributeKey[java.util.Map[String, (State => State, State => State)]](
      "sbt-watch-state-callbacks",
      "",
      Int.MaxValue
    )
  private val watchStates =
    AttributeKey[Map[String, ContinuousState]]("sbt-watch-states", Int.MaxValue)
  private[sbt] val runWatch = networkExecPrefix + "runWatch"
  private[sbt] val preWatch = networkExecPrefix + "preWatch"
  private[sbt] val postWatch = networkExecPrefix + "postWatch"
  private[sbt] val stopWatch = networkExecPrefix + "stopWatch"
  private[sbt] val failWatch = networkExecPrefix + "failWatch"
  private[sbt] val waitWatch = networkExecPrefix + "waitWatch"
  private def noComplete[T](p: Parser[T]): Parser[T] = p.examples()
  private val space = noComplete(Space)
  private def cmdParser(s: String): Parser[String] = noComplete(matched(s)) <~ space
  private def channelParser: Parser[String] =
    noComplete(matched(charClass(c => c.isLetterOrDigit || c == '-').+))

  private val stashedRepo = AttributeKey[FileTreeRepository[FileAttributes]](
    "stashed-file-tree-repository",
    "",
    Int.MaxValue
  )
  private[sbt] val setupWatchState: (String, Int, Seq[String], State) => State =
    (channelName, count, commands, state) => {
      state.get(watchStates).flatMap(_.get(channelName)) match {
        case None =>
          val extracted = Project.extract(state)
          val repo = state.get(globalFileTreeRepository) match {
            case Some(r) => localRepo(r)
            case _ =>
              throw new IllegalStateException(s"No file tree repository was found for $state")
          }
          val cache = new FileStamp.Cache
          repo.addObserver(t => cache.invalidate(t.path))
          val persistFileStamps = extracted.get(watchPersistFileStamps)
          val cachingRepo: FileTreeRepository[FileAttributes] =
            if (persistFileStamps) repo else new FileStampRepository(cache, repo)
          val channel = StandardMain.exchange
            .channelForName(channelName)
            .getOrElse(throw new IllegalStateException(s"No channel with name $channelName"))
          val dynamicInputs = mutable.Set.empty[DynamicInput]
          val context = LoggerContext()
          def cb: Continuous.Callbacks =
            Continuous.getCallbacks(state, channel, commands, cache, dynamicInputs, context)

          val s = new ContinuousState(
            count = count,
            commands = commands,
            beforeCommandImpl = (state, dynamicInputs) => {
              val original = state
                .get(globalFileTreeRepository)
                .getOrElse(
                  throw new IllegalStateException(
                    s"No global file tree repository for state $state"
                  )
                )
              val stateWithRepo =
                state.put(globalFileTreeRepository, cachingRepo).put(stashedRepo, original)
              val stateWithCache =
                if (persistFileStamps) stateWithRepo.put(persistentFileStampCache, cache)
                else stateWithRepo
              stateWithCache.put(Continuous.DynamicInputs, dynamicInputs)
            },
            afterCommand = state => {
              val newWatchState = state.get(watchStates) match {
                case None => state
                case Some(ws) =>
                  ws.get(channelName) match {
                    case None     => state
                    case Some(cs) => state.put(watchStates, ws + (channelName -> cs.incremented))
                  }
              }
              val restoredState = newWatchState.get(stashedRepo) match {
                case None    => throw new IllegalStateException(s"No stashed repository for $state")
                case Some(r) => newWatchState.put(globalFileTreeRepository, r)
              }
              restoredState.remove(persistentFileStampCache).remove(Continuous.DynamicInputs)
            },
            afterWatch = state => {
              context.clearAppenders(channelName + "-watch")
              repo.close()
              context.close()
              state.get(watchStates) match {
                case None     => state
                case Some(ws) => state.put(watchStates, ws - channelName)
              }
            },
            callbacks = cb,
            dynamicInputs = dynamicInputs,
            pending = false,
          )
          state.get(watchStates) match {
            case None     => state.put(watchStates, Map(channelName -> s))
            case Some(ws) => state.put(watchStates, ws + (channelName -> s))
          }
        case Some(cs) =>
          val cmd = cs.commands.mkString("; ")
          val msg =
            s"Tried to start new watch while channel, '$channelName', was already watching '$cmd'"
          throw new IllegalStateException(msg)
      }
    }
  private def watchCommand(
      name: String
  )(updateState: (String, State) => State): Command =
    Command.arb { state =>
      (cmdParser(name) ~> channelParser).map(channel => () => updateState(channel, state))
    } { case (_, newState) => newState() }
  private[sbt] val runWatchCommand = watchCommand(runWatch) { (channel, state) =>
    state.get(watchStates).flatMap(_.get(channel)) match {
      case None => state
      case Some(cs) =>
        val pre = StashOnFailure :: s"$preWatch $channel" :: Nil
        val post = FailureWall :: PopOnFailure :: s"$postWatch $channel" :: Nil
        pre ::: cs.commands.toList ::: post ::: state
    }
  }
  private[sbt] def watchUITaskFor(state: State, channel: CommandChannel): Option[UITask] =
    state.get(watchStates).flatMap(_.get(channel.name)).map(new WatchUITask(channel, _, state))
  private[sbt] def isInWatch(state: State, channel: CommandChannel): Boolean =
    state.get(watchStates).exists(_.contains(channel.name))
  private[sbt] def isPending(state: State, channel: CommandChannel): Boolean =
    state.get(watchStates).exists(_.get(channel.name).exists(_.pending))
  private class WatchUITask(
      override private[sbt] val channel: CommandChannel,
      cs: ContinuousState,
      state: State
  ) extends Thread(s"sbt-${channel.name}-watch-ui-thread")
      with UITask {
    override private[sbt] lazy val reader: UITask.Reader = () => {
      def stop = Right(s"${ContinuousCommands.stopWatch} ${channel.name}")
      val exitAction: Watch.Action = {
        Watch.apply(
          cs.count,
          _ => (),
          cs.callbacks.onStart,
          cs.callbacks.nextEvent,
          recursive = false
        )
      }
      val ws = state.get(watchStates) match {
        case None => throw new IllegalStateException("no watch states")
        case Some(ws) =>
          ws.get(channel.name)
            .getOrElse(throw new IllegalStateException(s"no watch state for ${channel.name}"))
      }
      exitAction match {
        // Use a Left so that the client can immediately exit watch via <enter>
        case Watch.CancelWatch => Left(s"$stopWatch ${channel.name}")
        case Watch.Trigger     => Right(s"$runWatch ${channel.name}")
        case Watch.Reload =>
          val rewatch = s"$ContinuousExecutePrefix ${ws.count} ${cs.commands mkString "; "}"
          stop.map(_ :: "reload" :: rewatch :: Nil mkString "; ")
        case Watch.Prompt => stop.map(_ :: s"$PromptChannel ${channel.name}" :: Nil mkString ";")
        case Watch.Run(commands) =>
          stop.map(_ +: commands.map(_.commandLine).filter(_.nonEmpty) mkString "; ")
        case a @ Watch.HandleError(_) =>
          cs.failAction = Some(a)
          stop.map(_ :: s"$failWatch ${channel.name}" :: Nil mkString "; ")
        case _ => stop
      }
    }
  }
  @inline
  private def watchState(state: State, channel: String): ContinuousState =
    state.get(watchStates).flatMap(_.get(channel)) match {
      case None    => throw new IllegalStateException(s"no watch state for $channel")
      case Some(s) => s
    }

  private[sbt] val preWatchCommand = watchCommand(preWatch) { (channel, state) =>
    val ws = watchState(state, channel)
    val newState = ws.beforeCommand(state)
    ws.callbacks.beforeCommand()
    newState
  }
  private[sbt] val postWatchCommand = watchCommand(postWatch) { (channel, state) =>
    val cs = watchState(state, channel)
    StandardMain.exchange.channelForName(channel).foreach { c =>
      c.terminal.setPrompt(Prompt.Pending)
    }
    val postState = state.get(watchStates) match {
      case None     => state
      case Some(ws) => state.put(watchStates, ws + (channel -> cs.withPending(false)))
    }
    cs.afterCommand(postState)
  }
  private val exitWatchShared = (error: Boolean) =>
    (channel: String, state: State) =>
      state.get(watchStates).flatMap(_.get(channel)) match {
        case Some(cs) =>
          val afterWatchState = cs.afterWatch(state)
          cs.callbacks.onExit()
          StandardMain.exchange
            .channelForName(channel)
            .foreach { c =>
              c.terminal.setPrompt(Prompt.Pending)
              c.unprompt(ConsoleUnpromptEvent(Some(CommandSource(channel))))
            }
          val newState = afterWatchState.get(watchStates) match {
            case None    => afterWatchState
            case Some(w) => afterWatchState.put(watchStates, w - channel)
          }
          val commands = cs.commands.mkString("; ")
          val count = cs.count
          val action = cs.failAction.getOrElse(Watch.CancelWatch)
          val st = cs.callbacks.onTermination(action, commands, count, newState)
          if (error) st.fail else st
        case _ => if (error) state.fail else state
      }
  private[sbt] val stopWatchCommand = watchCommand(stopWatch)(exitWatchShared(false))
  private[sbt] val failWatchCommand = watchCommand(failWatch)(exitWatchShared(true))
  /*
   * Creates a FileTreeRepository where it is safe to call close without inadvertently cancelling
   * still active watches.
   */
  private def localRepo[T](r: FileTreeRepository[T]): FileTreeRepository[T] =
    new FileTreeRepository[T] {
      private val closeables = ConcurrentHashMap.newKeySet[AutoCloseable]
      override def addObserver(observer: Observer[FileEvent[T]]): AutoCloseable = {
        val ac = r.addObserver(observer)
        val safeCloseable: AutoCloseable = () =>
          try ac.close()
          catch { case NonFatal(_) => }
        closeables.add(safeCloseable)
        () => {
          closeables.remove(safeCloseable)
          safeCloseable.close()
        }
      }

      override def register(glob: Glob): Either[IOException, Observable[FileEvent[T]]] =
        r.register(glob)
      override def close(): Unit = closeables.forEach { c =>
        try c.close()
        catch { case NonFatal(_) => }
      }
      override def list(path: Path): Seq[(Path, T)] = r.list(path)
    }

}
