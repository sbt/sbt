/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import Project.{ScopedKey, Setting}
	import Keys.{globalLogging, streams, Streams, TaskStreams}
	import Keys.{dummyState, dummyStreamsManager, streamsManager, taskDefinitionKey}
	import Scope.{GlobalScope, ThisScope}
	import scala.Console.{RED, RESET}

object EvaluateTask
{
	import Load.BuildStructure
	import Project.display
	import std.{TaskExtra,Transform}
	import TaskExtra._
	import Keys.state
	
	val SystemProcessors = Runtime.getRuntime.availableProcessors

	def injectSettings: Seq[Setting[_]] = Seq(
		(state in GlobalScope) ::= dummyState,
		(streamsManager in GlobalScope) ::= dummyStreamsManager
	)

	def evalPluginDef(log: Logger)(pluginDef: BuildStructure, state: State): Seq[Attributed[File]] =
	{
		val root = ProjectRef(pluginDef.root, Load.getRootProject(pluginDef.units)(pluginDef.root))
		val pluginKey = Keys.fullClasspath in Configurations.Runtime
		val evaluated = evaluateTask(pluginDef, ScopedKey(pluginKey.scope, pluginKey.key), state, root)
		val result = evaluated getOrElse error("Plugin classpath does not exist for plugin definition at " + pluginDef.root)
		processResult(result, log)
	}
	def evaluateTask[T](structure: BuildStructure, taskKey: ScopedKey[Task[T]], state: State, ref: ProjectRef, checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors): Option[Result[T]] =
		withStreams(structure) { str =>
			for( (task, toNode) <- getTask(structure, taskKey, state, str, ref) ) yield
				runTask(task, str, structure.index.triggers, checkCycles, maxWorkers)(toNode)
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

	def runTask[Task[_] <: AnyRef, T](root: Task[T], streams: Streams, triggers: Triggers[Task], checkCycles: Boolean = false, maxWorkers: Int = SystemProcessors)(implicit taskToNode: Execute.NodeView[Task]): Result[T] =
	{
		val (service, shutdown) = CompletionService[Task[_], Completed](maxWorkers)

		val x = new Execute[Task](checkCycles, triggers)(taskToNode)
		val result = try { x.run(root)(service) } finally { shutdown() }
		val replaced = transformInc(result)
		logIncResult(replaced, streams)
		replaced
	}
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
