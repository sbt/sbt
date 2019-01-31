/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.{ ByteArrayInputStream, InputStream }
import java.util.concurrent.atomic.AtomicInteger

import sbt.BasicCommandStrings.{
  ContinuousExecutePrefix,
  FailureWall,
  continuousBriefHelp,
  continuousDetail
}
import sbt.BasicCommands.otherCommandParser
import sbt.Def._
import sbt.Scope.Global
import sbt.Watched.Monitor
import sbt.internal.FileManagement.FileTreeRepositoryOps
import sbt.internal.io.WatchState
import sbt.internal.util.Types.const
import sbt.internal.util.complete.Parser._
import sbt.internal.util.complete.{ Parser, Parsers }
import sbt.internal.util.{ AttributeKey, AttributeMap }
import sbt.io._
import sbt.util.{ Level, _ }

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration.FiniteDurationIsOrdered
import scala.concurrent.duration._
import scala.util.Try

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
 *
 */
object Continuous extends DeprecatedContinuous {

  /**
   * Provides the dynamic inputs to the continuous build callbacks that cannot be stored as
   * settings. This wouldn't need to exist if there was a notion of a lazy setting in sbt.
   * @param logger the Logger
   * @param inputs the transitive task inputs
   * @param triggers the transitive task triggers
   */
  final class Arguments private[Continuous] (
      val logger: Logger,
      val inputs: Seq[Glob],
      val triggers: Seq[Glob]
  )

  /**
   * Provides a copy of System.in that can be scanned independently from System.in itself. This task
   * will only be valid during a continuous build started via `~` or the `watch` task. The
   * motivation is that a plugin may want to completely override the parsing of System.in which
   * is not straightforward since the default implementation is hard-wired to read from and
   * parse System.in. If an invalid parser is provided by [[Keys.watchInputParser]] and
   * [[Keys.watchInputStream]] is set to this task, then a custom parser can be provided via
   * [[Keys.watchInputHandler]] and the default System.in processing will not occur.
   *
   * @return the duplicated System.in
   */
  def dupedSystemIn: Def.Initialize[Task[InputStream]] = Def.task {
    Keys.state.value.get(DupedSystemIn).map(_.duped).getOrElse(System.in)
  }

  /**
   * Create a function from InputStream => [[Watched.Action]] from a [[Parser]]. This is intended
   * to be used to set the watchInputHandler setting for a task.
   * @param parser the parser
   * @return the function
   */
  def defaultInputHandler(parser: Parser[Watched.Action]): InputStream => Watched.Action = {
    val builder = new StringBuilder
    val any = matched(Parsers.any.*)
    val fullParser = any ~> parser ~ any
    inputStream =>
      parse(inputStream, builder, fullParser)
  }

  /**
   * Implements continuous execution. It works by first parsing the command and generating a task to
   * run with each build. It can run multiple commands that are separated by ";" in the command
   * input. If any of these commands are invalid, the watch will immediately exit.
   * @return a Command that can be used by sbt to implement continuous builds.
   */
  private[sbt] def continuous: Command =
    Command(ContinuousExecutePrefix, continuousBriefHelp, continuousDetail)(continuousParser) {
      case (state, (initialCount, command)) =>
        runToTermination(state, command, initialCount, isCommand = true)
    }

  /**
   * The task implementation is quite similar to the command implementation. The tricky part is that
   * we have to modify the Task.info to apply the state transformation after the task completes.
   * @return the [[InputTask]]
   */
  private[sbt] def continuousTask: Def.Initialize[InputTask[State]] =
    Def.inputTask {
      val (initialCount, command) = continuousParser.parsed
      runToTermination(Keys.state.value, command, initialCount, isCommand = false)
    }(_.mapTask { t =>
      val postTransform = t.info.postTransform {
        case (state: State, am: AttributeMap) => am.put(Keys.transformState, const(state))
      }
      Task(postTransform, t.work)
    })

  private[this] val DupedSystemIn =
    AttributeKey[DupedInputStream](
      "duped-system-in",
      "Receives a copy of all of the bytes from System.in.",
      10000
    )

  private[this] val continuousParser: State => Parser[(Int, String)] = {
    def toInt(s: String): Int = Try(s.toInt).getOrElse(0)
    // This allows us to re-enter the watch with the previous count.
    val digitParser: Parser[Int] =
      (Parsers.Space.* ~> matched(Parsers.Digit.+) <~ Parsers.Space.*).map(toInt)
    state =>
      val ocp = otherCommandParser(state)
      (digitParser.? ~ ocp).map { case (i, s) => (i.getOrElse(0), s) }
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
      scopedKey: ScopedKey[_],
      compiledMap: CompiledMap,
  )(implicit extracted: Extracted, logger: Logger): Config = {

    // Extract all of the globs that we will monitor during the continuous build.
    val (inputs, triggers) = {
      val configs = scopedKey.get(Keys.internalDependencyConfigurations).getOrElse(Nil)
      val args = new InputGraph.Arguments(scopedKey, extracted, compiledMap, logger, configs, state)
      InputGraph.transitiveGlobs(args)
    } match {
      case (i: Seq[Glob], t: Seq[Glob]) => (i.distinct.sorted, t.distinct.sorted)
    }

    val repository = getRepository(state)
    (inputs ++ triggers).foreach(repository.register)
    val watchSettings = new WatchSettings(scopedKey)
    new Config(
      scopedKey,
      repository,
      inputs,
      triggers,
      watchSettings
    )
  }
  private def getRepository(state: State): FileTreeRepository[FileAttributes] = {
    lazy val exception =
      new IllegalStateException("Tried to access FileTreeRepository for uninitialized state")
    state
      .get(Keys.globalFileTreeRepository)
      .map(FileManagement.toMonitoringRepository(_).copy())
      .getOrElse(throw exception)
  }

  private[sbt] def setup[R](state: State, command: String)(
      f: (State, Seq[String], Seq[() => Boolean], Seq[String]) => R
  ): R = {
    // First set up the state so that we can capture whether or not a task completed successfully
    // or if it threw an Exception (we lose the actual exception, but that should still be printed
    // to the console anyway).
    val failureCommandName = "SbtContinuousWatchOnFail"
    val onFail = Command.command(failureCommandName)(identity)
    /*
     * Takes a task string and converts it to an EitherTask. We cannot preserve either
     * the value returned by the task or any exception thrown by the task, but we can determine
     * whether or not the task ran successfully using the onFail command defined above.
     */
    def makeTask(cmd: String)(task: () => State): () => Boolean = { () =>
      MainLoop
        .processCommand(Exec(cmd, None), state, task)
        .remainingCommands
        .forall(_.commandLine != failureCommandName)
    }

    // This adds the "SbtContinuousWatchOnFail" onFailure handler which allows us to determine
    // whether or not the last task successfully ran. It is used in the makeTask method below.
    val s = (FailureWall :: state).copy(
      onFailure = Some(Exec(failureCommandName, None)),
      definedCommands = state.definedCommands :+ onFail
    )

    // We support multiple commands in watch, so it's necessary to run the command string through
    // the multi parser.
    val trimmed = command.trim
    val commands = Parser.parse(trimmed, BasicCommands.multiParserImpl(Some(s))) match {
      case Left(_)  => trimmed :: Nil
      case Right(c) => c
    }

    // Convert the command strings to runnable tasks, which are represented by
    // () => Try[Boolean].
    val taskParser = Command.combine(s.definedCommands)(s)
    // This specified either the task corresponding to a command or the command itself if the
    // the command cannot be converted to a task.
    val (invalid, valid) = commands.foldLeft((Nil: Seq[String], Nil: Seq[() => Boolean])) {
      case ((i, v), cmd) =>
        Parser.parse(cmd, taskParser) match {
          case Right(task) => (i, v :+ makeTask(cmd)(task))
          case Left(c)     => (i :+ c, v)
        }
    }
    f(s, commands, valid, invalid)
  }

  private[sbt] def runToTermination(
      state: State,
      command: String,
      count: Int,
      isCommand: Boolean
  ): State = Watched.withCharBufferedStdIn { in =>
    val duped = new DupedInputStream(in)
    setup(state.put(DupedSystemIn, duped), command) { (s, commands, valid, invalid) =>
      implicit val extracted: Extracted = Project.extract(s)
      EvaluateTask.withStreams(extracted.structure, s)(_.use(Keys.streams in Global) { streams =>
        implicit val logger: Logger = streams.log
        if (invalid.isEmpty) {
          val currentCount = new AtomicInteger(count)
          val callbacks =
            aggregate(getAllConfigs(s, commands), logger, in, state, currentCount, isCommand)
          val task = () => {
            currentCount.getAndIncrement()
            // abort as soon as one of the tasks fails
            valid.takeWhile(_.apply())
            ()
          }
          callbacks.onEnter()
          // Here we enter the Watched.watch state machine. We will not return until one of the
          // state machine callbacks returns Watched.CancelWatch, Watched.Custom, Watched.HandleError
          // or Watched.Reload. The task defined above will be run at least once. It will be run
          // additional times whenever the state transition callbacks return Watched.Trigger.
          try {
            val terminationAction = Watched.watch(task, callbacks.onStart, callbacks.nextEvent)
            callbacks.onTermination(terminationAction, command, currentCount.get(), state)
          } finally callbacks.onExit()
        } else {
          // At least one of the commands in the multi command string could not be parsed, so we
          // log an error and exit.
          val commands = invalid.mkString("'", "', '", "'")
          logger.error(s"Terminating watch due to invalid command(s): $commands")
          state.fail
        }
      })
    }
  }

  private def parseCommands(state: State, commands: Seq[String]): Seq[ScopedKey[_]] = {
    // Collect all of the scoped keys that are used to delegate the multi commands. These are
    // necessary to extract all of the transitive globs that we need to monitor during watch.
    // We have to add the <~ Parsers.any.* to ensure that we're able to extract the input key
    // from input tasks.
    val scopedKeyParser: Parser[Seq[ScopedKey[_]]] = Act.aggregatedKeyParser(state) <~ Parsers.any.*
    commands.flatMap { cmd: String =>
      Parser.parse(cmd, scopedKeyParser) match {
        case Right(scopedKeys: Seq[ScopedKey[_]]) => scopedKeys
        case Left(e) =>
          throw new IllegalStateException(s"Error attempting to extract scope from $cmd: $e.")
        case _ => Nil: Seq[ScopedKey[_]]
      }
    }
  }
  private def getAllConfigs(
      state: State,
      commands: Seq[String]
  )(implicit extracted: Extracted, logger: Logger): Seq[Config] = {
    val commandKeys = parseCommands(state, commands)
    val compiledMap = InputGraph.compile(extracted.structure)
    commandKeys.map((scopedKey: ScopedKey[_]) => getConfig(state, scopedKey, compiledMap))
  }

  private class Callbacks(
      val nextEvent: () => Watched.Action,
      val onEnter: () => Unit,
      val onExit: () => Unit,
      val onStart: () => Watched.Action,
      val onTermination: (Watched.Action, String, Int, State) => State
  )

  /**
   * Aggregates a collection of [[Config]] instances into a single instance of [[Callbacks]].
   * This allows us to monitor and respond to changes for all of
   * the inputs and triggers for each of the tasks that we are monitoring in the continuous build.
   * To monitor all of the inputs and triggers, it creates a [[FileEventMonitor]] for each task
   * and then aggregates each of the individual [[FileEventMonitor]] instances into an aggregated
   * instance. It aggregates all of the event callbacks into a single callback that delegates
   * to each of the individual callbacks. For the callbacks that return a [[Watched.Action]],
   * the aggregated callback will select the minimum [[Watched.Action]] returned where the ordering
   * is such that the highest priority [[Watched.Action]] have the lowest values. Finally, to
   * handle user input, we read from the provided input stream and buffer the result. Each
   * task's input parser is then applied to the buffered result and, again, we return the mimimum
   * [[Watched.Action]] returned by the parsers (when the parsers fail, they just return
   * [[Watched.Ignore]], which is the lowest priority [[Watched.Action]].
   *
   * @param configs the [[Config]] instances
   * @param rawLogger the default sbt logger instance
   * @param state the current state
   * @param extracted the [[Extracted]] instance for the current build
   * @return the [[Callbacks]] to pass into [[Watched.watch]]
   */
  private def aggregate(
      configs: Seq[Config],
      rawLogger: Logger,
      inputStream: InputStream,
      state: State,
      count: AtomicInteger,
      isCommand: Boolean
  )(
      implicit extracted: Extracted
  ): Callbacks = {
    val logger = setLevel(rawLogger, configs.map(_.watchSettings.logLevel).min, state)
    val onEnter = () => configs.foreach(_.watchSettings.onEnter())
    val onStart: () => Watched.Action = getOnStart(configs, logger, count)
    val nextInputEvent: () => Watched.Action = parseInputEvents(configs, state, inputStream, logger)
    val (nextFileEvent, cleanupFileMonitor): (() => Watched.Action, () => Unit) =
      getFileEvents(configs, logger, state, count)
    val nextEvent: () => Watched.Action =
      combineInputAndFileEvents(nextInputEvent, nextFileEvent, logger)
    val onExit = () => {
      cleanupFileMonitor()
      configs.foreach(_.watchSettings.onExit())
    }
    val onTermination = getOnTermination(configs, isCommand)
    new Callbacks(nextEvent, onEnter, onExit, onStart, onTermination)
  }

  private def getOnTermination(
      configs: Seq[Config],
      isCommand: Boolean
  ): (Watched.Action, String, Int, State) => State = {
    configs.flatMap(_.watchSettings.onTermination).distinct match {
      case Seq(head, tail @ _*) =>
        tail.foldLeft(head) {
          case (onTermination, configOnTermination) =>
            (action, cmd, count, state) =>
              configOnTermination(action, cmd, count, onTermination(action, cmd, count, state))
        }
      case _ =>
        if (isCommand) Watched.defaultCommandOnTermination else Watched.defaultTaskOnTermination
    }
  }

  private def getOnStart(
      configs: Seq[Config],
      logger: Logger,
      count: AtomicInteger
  ): () => Watched.Action = {
    val f = configs.map { params =>
      val ws = params.watchSettings
      ws.onStart.map(_.apply(params.arguments(logger))).getOrElse { () =>
        ws.onIteration.map(_(count.get)).getOrElse {
          if (configs.size == 1) { // Only allow custom start messages for single tasks
            ws.startMessage match {
              case Some(Left(sm))  => logger.info(sm(params.watchState(count.get())))
              case Some(Right(sm)) => sm(count.get()).foreach(logger.info(_))
              case None            => Watched.defaultStartWatch(count.get()).foreach(logger.info(_))
            }
          }
          Watched.Ignore
        }
      }
    }
    () =>
      {
        val res = f.view.map(_()).min
        // Print the default watch message if there are multiple tasks
        if (configs.size > 1) Watched.defaultStartWatch(count.get()).foreach(logger.info(_))
        res
      }
  }
  private def getFileEvents(
      configs: Seq[Config],
      logger: Logger,
      state: State,
      count: AtomicInteger,
  )(implicit extracted: Extracted): (() => Watched.Action, () => Unit) = {
    val trackMetaBuild = configs.forall(_.watchSettings.trackMetaBuild)
    val buildGlobs =
      if (trackMetaBuild) extracted.getOpt(Keys.fileInputs in Keys.settingsData).getOrElse(Nil)
      else Nil
    val buildFilter = buildGlobs.toEntryFilter

    /*
     * This is a callback that will be invoked whenever onEvent returns a Trigger action. The
     * motivation is to allow the user to specify this callback via setting so that, for example,
     * they can clear the screen when the build triggers.
     */
    val onTrigger: Event => Watched.Action = {
      val f: Seq[Event => Unit] = configs.map { params =>
        val ws = params.watchSettings
        ws.onTrigger
          .map(_.apply(params.arguments(logger)))
          .getOrElse {
            val globFilter = (params.inputs ++ params.triggers).toEntryFilter
            event: Event =>
              if (globFilter(event.entry)) {
                ws.triggerMessage match {
                  case Some(Left(tm))  => logger.info(tm(params.watchState(count.get())))
                  case Some(Right(tm)) => tm(count.get(), event).foreach(logger.info(_))
                  case None            => // By default don't print anything
                }
              }
          }
      }
      event: Event =>
        f.view.foreach(_.apply(event))
        Watched.Trigger
    }

    val onEvent: Event => (Event, Watched.Action) = {
      val f = configs.map { params =>
        val ws = params.watchSettings
        val oe = ws.onEvent
          .map(_.apply(params.arguments(logger)))
          .getOrElse {
            val onInputEvent = ws.onInputEvent.getOrElse(Watched.trigger)
            val onTriggerEvent = ws.onTriggerEvent.getOrElse(Watched.trigger)
            val onMetaBuildEvent = ws.onMetaBuildEvent.getOrElse(Watched.ifChanged(Watched.Reload))
            val inputFilter = params.inputs.toEntryFilter
            val triggerFilter = params.triggers.toEntryFilter
            event: Event =>
              val c = count.get()
              Seq[Watched.Action](
                if (inputFilter(event.entry)) onInputEvent(c, event) else Watched.Ignore,
                if (triggerFilter(event.entry)) onTriggerEvent(c, event) else Watched.Ignore,
                if (buildFilter(event.entry)) onMetaBuildEvent(c, event) else Watched.Ignore
              ).min
          }
        event: Event =>
          event -> (oe(event) match {
            case Watched.Trigger => onTrigger(event)
            case a               => a
          })
      }
      event: Event =>
        f.view.map(_.apply(event)).minBy(_._2)
    }
    val monitor: Monitor = new FileEventMonitor[FileAttributes] {
      private def setup(
          monitor: FileEventMonitor[FileAttributes],
          globs: Seq[Glob]
      ): FileEventMonitor[FileAttributes] = {
        val globFilters = globs.toEntryFilter
        val filter: Event => Boolean = (event: Event) => globFilters(event.entry)
        new FileEventMonitor[FileAttributes] {
          override def poll(duration: Duration): Seq[FileEventMonitor.Event[FileAttributes]] =
            monitor.poll(duration).filter(filter)
          override def close(): Unit = monitor.close()
        }
      }
      private[this] val monitors: Seq[FileEventMonitor[FileAttributes]] =
        configs.map { config =>
          // Create a logger with a scoped key prefix so that we can tell from which
          // monitor events occurred.
          val l = logger.withPrefix(config.key.show)
          val monitor: FileEventMonitor[FileAttributes] =
            FileManagement.monitor(config.repository, config.watchSettings.antiEntropy, l)
          val allGlobs = (config.inputs ++ config.triggers).distinct.sorted
          setup(monitor, allGlobs)
        } ++ (if (trackMetaBuild) {
                val l = logger.withPrefix("meta-build")
                val antiEntropy = configs.map(_.watchSettings.antiEntropy).min
                setup(FileManagement.monitor(getRepository(state), antiEntropy, l), buildGlobs) :: Nil
              } else Nil)
      override def poll(duration: Duration): Seq[FileEventMonitor.Event[FileAttributes]] = {
        // The call to .par allows us to poll all of the monitors in parallel.
        // This should be cheap because poll just blocks on a queue until an event is added.
        monitors.par.flatMap(_.poll(duration)).toSet.toVector
      }
      override def close(): Unit = monitors.foreach(_.close())
    }
    val watchLogger: WatchLogger = msg => logger.debug(msg.toString)
    val retentionPeriod = configs.map(_.watchSettings.antiEntropyRetentionPeriod).max
    val antiEntropy = configs.map(_.watchSettings.antiEntropy).max
    val quarantinePeriod = configs.map(_.watchSettings.deletionQuarantinePeriod).max
    val antiEntropyMonitor = FileEventMonitor.antiEntropy(
      monitor,
      antiEntropy,
      watchLogger,
      quarantinePeriod,
      retentionPeriod
    )
    (() => {
      val actions = antiEntropyMonitor.poll(2.milliseconds).map(onEvent)
      if (actions.exists(_._2 != Watched.Ignore)) {
        val min = actions.minBy(_._2)
        logger.debug(s"Received file event actions: ${actions.mkString(", ")}. Returning: $min")
        min._2
      } else Watched.Ignore
    }, () => monitor.close())
  }

  /**
   * Each task has its own input parser that can be used to modify the watch based on the input
   * read from System.in as well as a custom task-specific input stream that can be used as
   * an alternative source of control. In this method, we create two functions for each task,
   * one from `String => Seq[Watched.Action]` and another from `() => Seq[Watched.Action]`.
   * Each of these functions is invoked to determine the next state transformation for the watch.
   * The first function is a task specific copy of System.in. For each task we keep a mutable
   * buffer of the characters previously seen from System.in. Every time we receive new characters
   * we update the buffer and then try to parse a Watched.Action for each task. Any trailing
   * characters are captured and can be used for the next trigger. Because each task has a local
   * copy of the buffer, we do not have to worry about one task breaking parsing of another. We
   * also provide an alternative per task InputStream that is read in a similar way except that
   * we don't need to copy the custom InputStream which allows the function to be
   * `() => Seq[Watched.Action]` which avoids actually exposing the InputStream anywhere.
   */
  private def parseInputEvents(
      configs: Seq[Config],
      state: State,
      inputStream: InputStream,
      logger: Logger
  )(
      implicit extracted: Extracted
  ): () => Watched.Action = {
    /*
     * This parses the buffer until all possible actions are extracted. By draining the input
     * to a state where it does not parse an action, we can wait until we receive new input
     * to attempt to parse again.
     */
    type ActionParser = String => Watched.Action
    // Transform the Config.watchSettings.inputParser instances to functions of type
    // String => Watched.Action. The String that is provided will contain any characters that
    // have been read from stdin. If there are any characters available, then it calls the
    // parse method with the InputStream set to a ByteArrayInputStream that wraps the input
    // string. The parse method then appends those bytes to a mutable buffer and attempts to
    // parse the buffer. To make this work with streaming input, we prefix the parser with any.*.
    // If the Config.watchSettings.inputStream is set, the same process is applied except that
    // instead of passing in the wrapped InputStream for the input string, we directly pass
    // in the inputStream provided by Config.watchSettings.inputStream.
    val inputHandlers: Seq[ActionParser] = configs.map { c =>
      val any = Parsers.any.*
      val inputParser = c.watchSettings.inputParser
      val parser = any ~> inputParser ~ matched(any)
      // Each parser gets its own copy of System.in that it can modify while parsing.
      val systemInBuilder = new StringBuilder
      def inputStream(string: String): InputStream = new ByteArrayInputStream(string.getBytes)
      // This string is provided in the closure below by reading from System.in
      val default: String => Watched.Action =
        string => parse(inputStream(string), systemInBuilder, parser)
      val alternative = c.watchSettings.inputStream
        .map { inputStreamKey =>
          val is = extracted.runTask(inputStreamKey, state)._2
          val handler = c.watchSettings.inputHandler.getOrElse(defaultInputHandler(inputParser))
          () =>
            handler(is)
        }
        .getOrElse(() => Watched.Ignore)
      (string: String) =>
        (default(string) :: alternative() :: Nil).min
    }
    () =>
      {
        val stringBuilder = new StringBuilder
        while (inputStream.available > 0) stringBuilder += inputStream.read().toChar
        val newBytes = stringBuilder.toString
        val parse: ActionParser => Watched.Action = parser => parser(newBytes)
        val allEvents = inputHandlers.map(parse).filterNot(_ == Watched.Ignore)
        if (allEvents.exists(_ != Watched.Ignore)) {
          val res = allEvents.min
          logger.debug(s"Received input events: ${allEvents mkString ","}. Taking $res")
          res
        } else Watched.Ignore
      }
  }

  private def combineInputAndFileEvents(
      nextInputEvent: () => Watched.Action,
      nextFileEvent: () => Watched.Action,
      logger: Logger
  ): () => Watched.Action = () => {
    val Seq(inputEvent: Watched.Action, fileEvent: Watched.Action) =
      Seq(nextInputEvent, nextFileEvent).par.map(_.apply()).toIndexedSeq
    val min: Watched.Action = Seq[Watched.Action](inputEvent, fileEvent).min
    lazy val inputMessage =
      s"Received input event: $inputEvent." +
        (if (inputEvent != min) s" Dropping in favor of file event: $min" else "")
    lazy val fileMessage =
      s"Received file event: $fileEvent." +
        (if (fileEvent != min) s" Dropping in favor of input event: $min" else "")
    if (inputEvent != Watched.Ignore) logger.debug(inputMessage)
    if (fileEvent != Watched.Ignore) logger.debug(fileMessage)
    min
  }

  @tailrec
  private final def parse(
      is: InputStream,
      builder: StringBuilder,
      parser: Parser[(Watched.Action, String)]
  ): Watched.Action = {
    if (is.available > 0) builder += is.read().toChar
    Parser.parse(builder.toString, parser) match {
      case Right((action, rest)) =>
        builder.clear()
        builder ++= rest
        action
      case _ if is.available > 0 => parse(is, builder, parser)
      case _                     => Watched.Ignore
    }
  }

  /**
   * Generates a custom logger for the watch process that is able to log at a different level
   * from the provided logger.
   * @param logger the delegate logger.
   * @param logLevel the log level for watch events
   * @return the wrapped logger.
   */
  private def setLevel(logger: Logger, logLevel: Level.Value, state: State): Logger = {
    import Level._
    val delegateLevel = state.get(Keys.logLevel.key).getOrElse(Info)
    /*
     * The delegate logger may be set to, say, info level, but we want it to print out debug
     * messages if the logLevel variable above is Debug. To do this, we promote Debug messages
     * to the Info level (or Warn or Error if that's what the input logger is set to).
     */
    new Logger {
      override def trace(t: => Throwable): Unit = logger.trace(t)
      override def success(message: => String): Unit = logger.success(message)
      override def log(level: Level.Value, message: => String): Unit = {
        val levelString = if (level < delegateLevel) s"[$level] " else ""
        val newMessage = s"[watch] $levelString$message"
        val watchLevel = if (level < delegateLevel && level >= logLevel) delegateLevel else level
        logger.log(watchLevel, newMessage)
      }
    }
  }

  private type WatchOnEvent = (Int, Event) => Watched.Action

  /**
   * Contains all of the user defined settings that will be used to build a [[Callbacks]]
   * instance that is used to produce the arguments to [[Watched.watch]]. The
   * callback settings (e.g. onEvent or onInputEvent) come in two forms: those that return a
   * function from [[Arguments]] => F for some function type `F` and those that directly return a function, e.g.
   * `(Int, Boolean) => Watched.Action`. The former are a low level interface that will usually
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
  private final class WatchSettings private[Continuous] (val key: ScopedKey[_])(
      implicit extracted: Extracted
  ) {
    val antiEntropy: FiniteDuration =
      key.get(Keys.watchAntiEntropy).getOrElse(Watched.defaultAntiEntropy)
    val antiEntropyRetentionPeriod: FiniteDuration =
      key
        .get(Keys.watchAntiEntropyRetentionPeriod)
        .getOrElse(Watched.defaultAntiEntropyRetentionPeriod)
    val deletionQuarantinePeriod: FiniteDuration =
      key.get(Keys.watchDeletionQuarantinePeriod).getOrElse(Watched.defaultDeletionQuarantinePeriod)
    val inputHandler: Option[InputStream => Watched.Action] = key.get(Keys.watchInputHandler)
    val inputParser: Parser[Watched.Action] =
      key.get(Keys.watchInputParser).getOrElse(Watched.defaultInputParser)
    val logLevel: Level.Value = key.get(Keys.watchLogLevel).getOrElse(Level.Info)
    val onEnter: () => Unit = key.get(Keys.watchOnEnter).getOrElse(() => {})
    val onEvent: Option[Arguments => Event => Watched.Action] = key.get(Keys.watchOnEvent)
    val onExit: () => Unit = key.get(Keys.watchOnExit).getOrElse(() => {})
    val onInputEvent: Option[WatchOnEvent] = key.get(Keys.watchOnInputEvent)
    val onIteration: Option[Int => Watched.Action] = key.get(Keys.watchOnIteration)
    val onMetaBuildEvent: Option[WatchOnEvent] = key.get(Keys.watchOnMetaBuildEvent)
    val onStart: Option[Arguments => () => Watched.Action] = key.get(Keys.watchOnStart)
    val onTermination: Option[(Watched.Action, String, Int, State) => State] =
      key.get(Keys.watchOnTermination)
    val onTrigger: Option[Arguments => Event => Unit] = key.get(Keys.watchOnTrigger)
    val onTriggerEvent: Option[WatchOnEvent] = key.get(Keys.watchOnTriggerEvent)
    val startMessage: StartMessage = getStartMessage(key)
    val trackMetaBuild: Boolean = key.get(Keys.watchTrackMetaBuild).getOrElse(true)
    val triggerMessage: TriggerMessage = getTriggerMessage(key)

    // Unlike the rest of the settings, InputStream is a TaskKey which means that if it is set,
    // we have to use Extracted.runTask to get the value. The reason for this is because it is
    // logical that users may want to use a different InputStream on each task invocation. The
    // alternative would be SettingKey[() => InputStream], but that doesn't feel right because
    // one might want the InputStream to depend on other tasks.
    val inputStream: Option[TaskKey[InputStream]] = key.get(Keys.watchInputStream)
  }

  /**
   * Container class for all of the components we need to setup a watch for a particular task or
   * input task.
   * @param key the [[ScopedKey]] instance for the task we will watch
   * @param repository the task [[FileTreeRepository]] instance
   * @param inputs the transitive task inputs (see [[InputGraph]])
   * @param triggers the transitive triggers (see [[InputGraph]])
   * @param watchSettings the [[WatchSettings]] instance for the task
   */
  private final class Config private[internal] (
      val key: ScopedKey[_],
      val repository: FileTreeRepository[FileAttributes],
      val inputs: Seq[Glob],
      val triggers: Seq[Glob],
      val watchSettings: WatchSettings
  ) {
    private[sbt] def watchState(count: Int): DeprecatedWatchState =
      WatchState.empty(inputs ++ triggers).withCount(count)
    def arguments(logger: Logger): Arguments = new Arguments(logger, inputs, triggers)
  }
  private def getStartMessage(key: ScopedKey[_])(implicit e: Extracted): StartMessage = Some {
    lazy val default = key.get(Keys.watchStartMessage).getOrElse(Watched.defaultStartWatch)
    key.get(deprecatedWatchingMessage).map(Left(_)).getOrElse(Right(default))
  }
  private def getTriggerMessage(key: ScopedKey[_])(implicit e: Extracted): TriggerMessage = Some {
    lazy val default =
      key.get(Keys.watchTriggeredMessage).getOrElse(Watched.defaultOnTriggerMessage)
    key.get(deprecatedWatchingMessage).map(Left(_)).getOrElse(Right(default))
  }

  private implicit class ScopeOps(val scope: Scope) {

    /**
     * This shows the [[Scope]] in the format that a user would likely type it in a build
     * or in the sbt console. For example, the key corresponding to the command
     * foo/Compile/compile will pretty print as "foo / Compile / compile", not
     * "ProjectRef($URI, foo) / compile / compile", where the ProjectRef part is just noise that
     * is rarely relevant for debugging.
     * @return the pretty printed output.
     */
    def show: String = {
      val mask = ScopeMask(
        config = scope.config.toOption.isDefined,
        task = scope.task.toOption.isDefined,
        extra = scope.extra.toOption.isDefined
      )
      Scope
        .displayMasked(scope, " ", (_: Reference) match {
          case p: ProjectRef => s"${p.project.trim} /"
          case _             => "Global /"
        }, mask)
        .dropRight(3) // delete trailing "/"
        .trim
    }
  }

  private implicit class ScopedKeyOps(val scopedKey: ScopedKey[_]) extends AnyVal {

    /**
     * Gets the value for a setting key scoped to the wrapped [[ScopedKey]]. If the task axis is not
     * set in the [[ScopedKey]], then we first set the task axis and try to extract the setting
     * from that scope otherwise we fallback on the [[ScopedKey]] instance's scope. We use the
     * reverse order if the task is set.
     *
     * @param settingKey the [[SettingKey]] to extract
     * @param extracted the provided [[Extracted]] instance
     * @tparam T the type of the [[SettingKey]]
     * @return the optional value of the [[SettingKey]] if it is defined at the input
     *         [[ScopedKey]] instance's scope or task scope.
     */
    def get[T](settingKey: SettingKey[T])(implicit extracted: Extracted): Option[T] = {
      lazy val taskScope = Project.fillTaskAxis(scopedKey).scope
      scopedKey.scope match {
        case scope if scope.task.toOption.isDefined =>
          extracted.getOpt(settingKey in scope) orElse extracted.getOpt(settingKey in taskScope)
        case scope =>
          extracted.getOpt(settingKey in taskScope) orElse extracted.getOpt(settingKey in scope)
      }
    }

    /**
     * Gets the [[ScopedKey]] for a task scoped to the wrapped [[ScopedKey]]. If the task axis is
     * not set in the [[ScopedKey]], then we first set the task axis and try to extract the tak
     * from that scope otherwise we fallback on the [[ScopedKey]] instance's scope. We use the
     * reverse order if the task is set.
     *
     * @param taskKey the [[TaskKey]] to extract
     * @param extracted the provided [[Extracted]] instance
     * @tparam T the type of the [[SettingKey]]
     * @return the optional value of the [[SettingKey]] if it is defined at the input
     *         [[ScopedKey]] instance's scope or task scope.
     */
    def get[T](taskKey: TaskKey[T])(implicit extracted: Extracted): Option[TaskKey[T]] = {
      lazy val taskScope = Project.fillTaskAxis(scopedKey).scope
      scopedKey.scope match {
        case scope if scope.task.toOption.isDefined =>
          if (extracted.getOpt(taskKey in scope).isDefined) Some(taskKey in scope)
          else if (extracted.getOpt(taskKey in taskScope).isDefined) Some(taskKey in taskScope)
          else None
        case scope =>
          if (extracted.getOpt(taskKey in taskScope).isDefined) Some(taskKey in taskScope)
          else if (extracted.getOpt(taskKey in scope).isDefined) Some(taskKey in scope)
          else None
      }
    }

    /**
     * This shows the [[ScopedKey[_]] in the format that a user would likely type it in a build
     * or in the sbt console. For example, the key corresponding to the command
     * foo/Compile/compile will pretty print as "foo / Compile / compile", not
     * "ProjectRef($URI, foo) / compile / compile", where the ProjectRef part is just noise that
     * is rarely relevant for debugging.
     * @return the pretty printed output.
     */
    def show: String = s"${scopedKey.scope.show} / ${scopedKey.key}"
  }

  private implicit class LoggerOps(val logger: Logger) extends AnyVal {

    /**
     * Creates a logger that adds a prefix to the messages that it logs. The motivation is so that
     * we can tell from which FileEventMonitor an event originated.
     * @param prefix the string to prefix the message with
     * @return the wrapped Logger.
     */
    def withPrefix(prefix: String): Logger = new Logger {
      override def trace(t: => Throwable): Unit = logger.trace(t)
      override def success(message: => String): Unit = logger.success(message)
      override def log(level: Level.Value, message: => String): Unit =
        logger.log(level, s"$prefix - $message")
    }
  }

}
