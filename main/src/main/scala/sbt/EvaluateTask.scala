/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import Def.{displayFull, dummyState, ScopedKey, Setting}
	import Keys.{streams, Streams, TaskStreams}
	import Keys.{dummyRoots, dummyStreamsManager, executionRoots, pluginData, streamsManager, taskDefinitionKey, transformState}
	import Project.richInitializeTask
	import Scope.{GlobalScope, ThisScope}
	import Types.const
	import scala.Console.RED
	import std.Transform.{DummyTaskMap,TaskAndValue}
	import TaskName._

final case class EvaluateConfig(
	cancelable: Boolean, 
	restrictions: Seq[Tags.Rule], 
	checkCycles: Boolean = false, 
	progress: ExecuteProgress[Task] = EvaluateTask.defaultProgress)
final case class PluginData(dependencyClasspath: Seq[Attributed[File]], definitionClasspath: Seq[Attributed[File]], resolvers: Option[Seq[Resolver]], report: Option[UpdateReport], scalacOptions: Seq[String])
{
	val classpath: Seq[Attributed[File]] = definitionClasspath ++ dependencyClasspath
}
object PluginData
{
	@deprecated("Use the alternative that specifies the compiler options and specific classpaths.", "0.13.1")
	def apply(dependencyClasspath: Seq[Attributed[File]], definitionClasspath: Seq[Attributed[File]], resolvers: Option[Seq[Resolver]], report: Option[UpdateReport]): PluginData =
		PluginData(dependencyClasspath, definitionClasspath, resolvers, report, Nil)
	@deprecated("Use the alternative that specifies the specific classpaths.", "0.13.0")
	def apply(classpath: Seq[Attributed[File]], resolvers: Option[Seq[Resolver]], report: Option[UpdateReport]): PluginData =
		PluginData(classpath, Nil, resolvers, report, Nil)
}

object EvaluateTask
{
	import std.{TaskExtra,Transform}
	import TaskExtra._
	import Keys.state

	private[sbt] def defaultProgress: ExecuteProgress[Task] =
		if(java.lang.Boolean.getBoolean("sbt.task.timings")) new TaskTimings else ExecuteProgress.empty[Task]


	// Default is true, but can be disabled.
	private[sbt] val isAutoGcEnabled =
		System.getProperty("sbt.task.autogc") == null ||
		java.lang.Boolean.getBoolean("sbt.task.autogc")
	private[sbt] val isPoorlyActingGcOnJvm: Boolean = {
		// TODO - Not so hacky a mechanism here.
		val javaVersion = System.getProperty("java.version")
		javaVersion.startsWith("1.5") ||
		javaVersion.startsWith("1.6") ||
		javaVersion.startsWith("1.7")
	}

	val SystemProcessors = Runtime.getRuntime.availableProcessors

	@deprecated("Use extractedConfig.", "0.13.0")
	def defaultConfig(state: State): EvaluateConfig =
	{
		val extracted = Project.extract(state)
		extractedConfig(extracted, extracted.structure, state)
	}

	@deprecated("Use extractedConfig.", "0.13.0")
	def defaultConfig(extracted: Extracted, structure: BuildStructure) =
		EvaluateConfig(false, restrictions(extracted, structure), progress = defaultProgress)

  @deprecated("Use other extractedConfig", "0.13.2")
	def extractedConfig(extracted: Extracted, structure: BuildStructure): EvaluateConfig =
	{
		val workers = restrictions(extracted, structure)
		val canCancel = cancelable(extracted, structure)
		EvaluateConfig(cancelable = canCancel, restrictions = workers, progress = defaultProgress)
	}
	def extractedConfig(extracted: Extracted, structure: BuildStructure, state: State): EvaluateConfig =
	{
		val workers = restrictions(extracted, structure)
		val canCancel = cancelable(extracted, structure)
		val progress = executeProgress(extracted, structure, state)
		EvaluateConfig(cancelable = canCancel, restrictions = workers, progress = progress)
	}

	def defaultRestrictions(maxWorkers: Int) = Tags.limitAll(maxWorkers) :: Nil
	def defaultRestrictions(extracted: Extracted, structure: BuildStructure): Seq[Tags.Rule] =
		Tags.limitAll(maxWorkers(extracted, structure)) :: Nil

	def restrictions(state: State): Seq[Tags.Rule] =
	{
		val extracted = Project.extract(state)
		restrictions(extracted, extracted.structure)
	}
	def restrictions(extracted: Extracted, structure: BuildStructure): Seq[Tags.Rule] =
		getSetting(Keys.concurrentRestrictions, defaultRestrictions(extracted, structure), extracted, structure)
	def maxWorkers(extracted: Extracted, structure: BuildStructure): Int =
		if(getSetting(Keys.parallelExecution, true, extracted, structure))
			SystemProcessors
		else
			1
	def cancelable(extracted: Extracted, structure: BuildStructure): Boolean =
		getSetting(Keys.cancelable, false, extracted, structure)

	private[sbt] def executeProgress(extracted: Extracted, structure: BuildStructure, state: State): ExecuteProgress[Task] = {
		import Types.const
		 val maker: State => Keys.TaskProgress = getSetting(Keys.executeProgress, const(new Keys.TaskProgress(defaultProgress)), extracted, structure)
		 maker(state).progress
   }

	def getSetting[T](key: SettingKey[T], default: T, extracted: Extracted, structure: BuildStructure): T =
		key in extracted.currentRef get structure.data getOrElse default

	def injectSettings: Seq[Setting[_]] = Seq(
		(state in GlobalScope) ::= dummyState,
		(streamsManager in GlobalScope) ::= dummyStreamsManager,
		(executionRoots in GlobalScope) ::= dummyRoots
	)

	def evalPluginDef(log: Logger)(pluginDef: BuildStructure, state: State): PluginData =
	{
		val root = ProjectRef(pluginDef.root, Load.getRootProject(pluginDef.units)(pluginDef.root))
		val pluginKey = pluginData
		val config = extractedConfig(Project.extract(state), pluginDef, state)
		val evaluated = apply(pluginDef, ScopedKey(pluginKey.scope, pluginKey.key), state, root, config)
		val (newS, result) = evaluated getOrElse sys.error("Plugin data does not exist for plugin definition at " + pluginDef.root)
		Project.runUnloadHooks(newS) // discard states
		processResult(result, log)
	}

	@deprecated("This method does not apply state changes requested during task execution and does not honor concurrent execution restrictions.  Use 'apply' instead.", "0.11.1")
	def evaluateTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, ref: ProjectRef, checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors): Option[Result[T]] =
		apply(structure, taskKey, state, ref, EvaluateConfig(false, defaultRestrictions(maxWorkers), checkCycles)).map(_._2)

	/** Evaluates `taskKey` and returns the new State and the result of the task wrapped in Some.
	* If the task is not defined, None is returned.  The provided task key is resolved against the current project `ref`.
	* Task execution is configured according to settings defined in the loaded project.*/
	def apply[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, ref: ProjectRef): Option[(State, Result[T])] =
		apply[T](structure, taskKey, state, ref, extractedConfig(Project.extract(state), structure))

	/** Evaluates `taskKey` and returns the new State and the result of the task wrapped in Some.
	* If the task is not defined, None is returned.  The provided task key is resolved against the current project `ref`.
	* `config` configures concurrency and canceling of task execution.  */
	def apply[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, ref: ProjectRef, config: EvaluateConfig): Option[(State, Result[T])] =
		withStreams(structure, state) { str =>
			for( (task, toNode) <- getTask(structure, taskKey, state, str, ref) ) yield
				runTask(task, state, str, structure.index.triggers, config)(toNode)
		}
	def logIncResult(result: Result[_], state: State, streams: Streams) = result match { case Inc(i) => logIncomplete(i, state, streams); case _ => () }
	def logIncomplete(result: Incomplete, state: State, streams: Streams)
	{
		val all = Incomplete linearize result
		val keyed = for(Incomplete(Some(key: ScopedKey[_]), _, msg, _, ex) <- all) yield (key, msg, ex)
		val un = all.filter { i => i.node.isEmpty || i.message.isEmpty }

			import ExceptionCategory._
		for( (key, msg, Some(ex)) <- keyed) {
			def log = getStreams(key, streams).log
			ExceptionCategory(ex) match {
				case AlreadyHandled => ()
				case m: MessageOnly => if(msg.isEmpty) log.error(m.message)
				case f: Full => log.trace(f.exception)
			}
		}

		for( (key, msg, ex) <- keyed if(msg.isDefined || ex.isDefined) )
		{
			val msgString = (msg.toList ++ ex.toList.map(ErrorHandling.reducedToString)).mkString("\n\t")
			val log = getStreams(key, streams).log
			val display = contextDisplay(state, log.ansiCodesSupported)
			log.error("(" + display(key) + ") " + msgString)
		}
	}
	private[this] def contextDisplay(state: State, highlight: Boolean) = Project.showContextKey(state, if(highlight) Some(RED) else None)
	def suppressedMessage(key: ScopedKey[_])(implicit display: Show[ScopedKey[_]]): String =
		"Stack trace suppressed.  Run 'last %s' for the full log.".format(display(key))

	def getStreams(key: ScopedKey[_], streams: Streams): TaskStreams =
		streams(ScopedKey(Project.fillTaskAxis(key).scope, Keys.streams.key))
	def withStreams[T](structure: BuildStructure, state: State)(f: Streams => T): T =
	{
		val str = std.Streams.closeable(structure.streams(state))
		try { f(str) } finally { str.close() }
	}

	def getTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, streams: Streams, ref: ProjectRef): Option[(Task[T], NodeView[Task])] =
	{
		val thisScope = Load.projectScope(ref)
		val resolvedScope = Scope.replaceThis(thisScope)( taskKey.scope )
		for( t <- structure.data.get(resolvedScope, taskKey.key)) yield
			(t, nodeView(state, streams, taskKey :: Nil))
	}
	def nodeView[HL <: HList](state: State, streams: Streams, roots: Seq[ScopedKey[_]], dummies: DummyTaskMap = DummyTaskMap(Nil)): NodeView[Task] =
		Transform((dummyRoots, roots) :: (dummyStreamsManager, streams) :: (dummyState, state) :: dummies )

	def runTask[T](root: Task[T], state: State, streams: Streams, triggers: Triggers[Task], config: EvaluateConfig)(implicit taskToNode: NodeView[Task]): (State, Result[T]) =
	{
			import ConcurrentRestrictions.{completionService, TagMap, Tag, tagged, tagsKey}

		val log = state.log
		log.debug("Running task... Cancelable: " + config.cancelable + ", check cycles: " + config.checkCycles)
		val tags = tagged[Task[_]](_.info get tagsKey getOrElse Map.empty, Tags.predicate(config.restrictions))
		val (service, shutdown) = completionService[Task[_], Completed](tags, (s: String) => log.warn(s))

		// FIX - We create a cleanup method here which ensures the thread pool is closed down
		//       and we attempt to force the JVM < 8 to release classloaders, due to issues
		//       with WeakHashMaps/WeakHashSets and lazy GCs not running finalizers.
		def shutdownAndCleanup(): Unit = {
			shutdown()
			if(isAutoGcEnabled && isPoorlyActingGcOnJvm) {
				// Force the detection of finalizers for scala.reflect weakhashsets
				System.gc()
				// Force finalizers to run.
				System.runFinalization()
				// Force actually cleaning the weak hash maps.
				System.gc()
			}
		}


		// propagate the defining key for reporting the origin
		def overwriteNode(i: Incomplete): Boolean = i.node match {
			case Some(t: Task[_]) => transformNode(t).isEmpty
			case _ => true
		}
		def run() = {
			val x = new Execute[Task]( Execute.config(config.checkCycles, overwriteNode), triggers, config.progress)(taskToNode)
			val (newState, result) =
				try {
					val results = x.runKeep(root)(service)
					storeValuesForPrevious(results, state, streams)
					applyResults(results, state, root)
				} catch { case inc: Incomplete => (state, Inc(inc)) }
				finally shutdownAndCleanup()
			val replaced = transformInc(result)
			logIncResult(replaced, state, streams)
			(newState, replaced)
		}
		val cancel = () => {
			println("")
			log.warn("Canceling execution...")
			shutdownAndCleanup()
		}
		if(config.cancelable)
			Signals.withHandler(cancel) { run }
		else
			run()
	}

	private[this] def storeValuesForPrevious(results: RMap[Task, Result], state: State, streams: Streams): Unit =
		for(referenced <- Previous.references in Global get Project.structure(state).data)
			Previous.complete(referenced, results, streams)

	def applyResults[T](results: RMap[Task, Result], state: State, root: Task[T]): (State, Result[T]) =
		(stateTransform(results)(state), results(root))
	def stateTransform(results: RMap[Task, Result]): State => State =
		Function.chain(
			results.toTypedSeq flatMap {
				case results.TPair(Task(info, _), Value(v)) => info.post(v) get transformState
				case _ => Nil
			}
		)

	def transformInc[T](result: Result[T]): Result[T] =
		// taskToKey needs to be before liftAnonymous.  liftA only lifts non-keyed (anonymous) Incompletes.
		result.toEither.left.map { i => Incomplete.transformBU(i)(convertCyclicInc andThen taskToKey andThen liftAnonymous ) }
	def taskToKey: Incomplete => Incomplete = {
		case in @ Incomplete(Some(node: Task[_]), _, _, _, _) => in.copy(node = transformNode(node))
		case i => i
	}
	type AnyCyclic = Execute[Task]#CyclicException[_]
	def convertCyclicInc: Incomplete => Incomplete = {
		case in @ Incomplete(_, _, _, _, Some(c: AnyCyclic)) =>
			in.copy(directCause = Some(new RuntimeException(convertCyclic(c))) )
		case i => i
	}
	def convertCyclic(c: AnyCyclic): String =
		(c.caller, c.target) match {
			case (caller: Task[_], target: Task[_]) =>
				c.toString + (if(caller eq target) "(task: " + name(caller) + ")" else "(caller: " + name(caller) + ", target: " + name(target) + ")" )
			case _ => c.toString
		}

	def liftAnonymous: Incomplete => Incomplete = {
		case i @ Incomplete(node, tpe, None, causes, None) =>
			causes.find( inc => !inc.node.isDefined && (inc.message.isDefined || inc.directCause.isDefined)) match {
				case Some(lift) => i.copy(directCause = lift.directCause, message = lift.message)
				case None => i
			}
		case i => i
	}

	def processResult[T](result: Result[T], log: Logger, show: Boolean = false): T =
		onResult(result, log) { v => if(show) println("Result: " + v); v }
	def onResult[T, S](result: Result[T], log: Logger)(f: T => S): S =
		result match
		{
			case Value(v) => f(v)
			case Inc(inc) => throw inc
		}

	// if the return type Seq[Setting[_]] is not explicitly given, scalac hangs
	val injectStreams: ScopedKey[_] => Seq[Setting[_]] = scoped =>
		if(scoped.key == streams.key)
			Seq(streams in scoped.scope <<= streamsManager map { mgr =>
				val stream = mgr(scoped)
				stream.open()
				stream
			})
		else
			Nil
}
