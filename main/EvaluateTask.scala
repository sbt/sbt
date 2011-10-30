/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import Project.{ScopedKey, Setting}
	import Keys.{streams, Streams, TaskStreams}
	import Keys.{dummyState, dummyStreamsManager, streamsManager, taskDefinitionKey, transformState}
	import Scope.{GlobalScope, ThisScope}
	import Types.const
	import scala.Console.{RED, RESET}

final case class EvaluateConfig(cancelable: Boolean, checkCycles: Boolean = false, maxWorkers: Int = EvaluateTask.SystemProcessors)
object EvaluateTask
{
	import Load.BuildStructure
	import Project.display
	import std.{TaskExtra,Transform}
	import TaskExtra._
	import Keys.state
	
	val SystemProcessors = Runtime.getRuntime.availableProcessors
	def defaultConfig = EvaluateConfig(false)
	def extractedConfig(extracted: Extracted, structure: BuildStructure): EvaluateConfig =
	{
		val workers = maxWorkers(extracted, structure)
		val canCancel = cancelable(extracted, structure)
		EvaluateConfig(cancelable = canCancel, maxWorkers = workers)
	}

	def maxWorkers(extracted: Extracted, structure: Load.BuildStructure): Int =
		if(getBoolean(Keys.parallelExecution, true, extracted, structure))
			EvaluateTask.SystemProcessors
		else
			1
	def cancelable(extracted: Extracted, structure: Load.BuildStructure): Boolean =
		getBoolean(Keys.cancelable, false, extracted, structure)
	def getBoolean(key: SettingKey[Boolean], default: Boolean, extracted: Extracted, structure: Load.BuildStructure): Boolean =
		(key in extracted.currentRef get structure.data) getOrElse default

	def injectSettings: Seq[Setting[_]] = Seq(
		(state in GlobalScope) ::= dummyState,
		(streamsManager in GlobalScope) ::= dummyStreamsManager
	)

	def evalPluginDef(log: Logger)(pluginDef: BuildStructure, state: State): Seq[Attributed[File]] =
	{
		val root = ProjectRef(pluginDef.root, Load.getRootProject(pluginDef.units)(pluginDef.root))
		val pluginKey = Keys.fullClasspath in Configurations.Runtime
		val evaluated = apply(pluginDef, ScopedKey(pluginKey.scope, pluginKey.key), state, root, defaultConfig)
		val (newS, result) = evaluated getOrElse error("Plugin classpath does not exist for plugin definition at " + pluginDef.root)
		Project.runUnloadHooks(newS) // discard states
		processResult(result, log)
	}

	@deprecated("This method does not apply state changes requested during task execution.  Use 'apply' instead, which does.", "0.11.1")
	def evaluateTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, ref: ProjectRef, checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors): Option[Result[T]] =
		apply(structure, taskKey, state, ref, EvaluateConfig(false, checkCycles, maxWorkers)).map(_._2)
	def apply[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, ref: ProjectRef, config: EvaluateConfig = defaultConfig): Option[(State, Result[T])] =
		withStreams(structure) { str =>
			for( (task, toNode) <- getTask(structure, taskKey, state, str, ref) ) yield
				runTask(task, state, str, structure.index.triggers, config)(toNode)
		}
	def logIncResult(result: Result[_], streams: Streams) = result match { case Inc(i) => logIncomplete(i, streams); case _ => () }
	def logIncomplete(result: Incomplete, streams: Streams)
	{
		val all = Incomplete linearize result
		val keyed = for(Incomplete(Some(key: Project.ScopedKey[_]), _, msg, _, ex) <- all) yield (key, msg, ex)
		val un = all.filter { i => i.node.isEmpty || i.message.isEmpty }
		for( (key, _, Some(ex)) <- keyed)
			getStreams(key, streams).log.trace(ex)

		for( (key, msg, ex) <- keyed if(msg.isDefined || ex.isDefined) )
		{
			val msgString = (msg.toList ++ ex.toList.map(ErrorHandling.reducedToString)).mkString("\n\t")
			val log = getStreams(key, streams).log
			val keyString = if(log.ansiCodesSupported) RED + key.key.label + RESET else key.key.label
			log.error(Scope.display(key.scope, keyString) + ": " + msgString)
		}
	}
	def getStreams(key: ScopedKey[_], streams: Streams): TaskStreams =
		streams(ScopedKey(Project.fillTaskAxis(key).scope, Keys.streams.key))
	def withStreams[T](structure: BuildStructure)(f: Streams => T): T =
	{
		val str = std.Streams.closeable(structure.streams)
		try { f(str) } finally { str.close() }
	}
	
	def getTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, streams: Streams, ref: ProjectRef): Option[(Task[T], Execute.NodeView[Task])] =
	{
		val thisScope = Load.projectScope(ref)
		val resolvedScope = Scope.replaceThis(thisScope)( taskKey.scope )
		for( t <- structure.data.get(resolvedScope, taskKey.key)) yield
			(t, nodeView(state, streams))
	}
	def nodeView[HL <: HList](state: State, streams: Streams, extraDummies: KList[Task, HL] = KNil, extraValues: HL = HNil): Execute.NodeView[Task] =
		Transform(dummyStreamsManager :^: KCons(dummyState, extraDummies), streams :+: HCons(state, extraValues))

	def runTask[T](root: Task[T], state: State, streams: Streams, triggers: Triggers[Task], config: EvaluateConfig = defaultConfig)(implicit taskToNode: Execute.NodeView[Task]): (State, Result[T]) =
	{
		val log = state.log
		log.debug("Running task... Cancelable: " + config.cancelable + ", max worker threads: " + config.maxWorkers + ", check cycles: " + config.checkCycles)
		val (service, shutdown) = CompletionService[Task[_], Completed](config.maxWorkers)

		def run() = {
			val x = new Execute[Task](config.checkCycles, triggers)(taskToNode)
			val (newState, result) =
				try applyResults(x.runKeep(root)(service), state, root)
				catch { case inc: Incomplete => (state, Inc(inc)) }
				finally shutdown()
			val replaced = transformInc(result)
			logIncResult(replaced, streams)
			(newState, replaced)
		}
		val cancel = () => {
			println("")
			log.warn("Canceling execution...")
			shutdown()
		}
		if(config.cancelable)
			Signals.withHandler(cancel) { run }
		else
			run()
	}

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
		case in @ Incomplete(_, _, _, _, Some(c: AnyCyclic)) => in.copy(directCause = Some(new RuntimeException(convertCyclic(c))) )
		case i => i
	}
	def convertCyclic(c: AnyCyclic): String =
		(c.caller, c.target) match {
			case (caller: Task[_], target: Task[_]) =>
				c.toString + (if(caller eq target) "(task: " + name(caller) + ")" else "(caller: " + name(caller) + ", target: " + name(target) + ")" )
			case _ => c.toString
		}
	def name(node: Task[_]): String =
		node.info.name orElse transformNode(node).map(Project.displayFull) getOrElse ("<anon-" + System.identityHashCode(node).toHexString + ">")
	def liftAnonymous: Incomplete => Incomplete = {
		case i @ Incomplete(node, tpe, None, causes, None) =>
			causes.find( inc => !inc.node.isDefined && (inc.message.isDefined || inc.directCause.isDefined)) match {
				case Some(lift) => i.copy(directCause = lift.directCause, message = lift.message)
				case None => i
			}
		case i => i
	}
	def transformNode(node: Task[_]): Option[ScopedKey[_]] =
		node.info.attributes get taskDefinitionKey

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
