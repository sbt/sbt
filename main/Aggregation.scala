/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import CommandSupport.logger
	import Project.ScopedKey
	import Load.BuildStructure
	import Keys.{aggregate, parseResult, showSuccess, showTiming, timingFormat}
	import sbt.complete.Parser
	import java.net.URI
	import Parser._
	import collection.mutable

sealed trait Aggregation
final object Aggregation
{
	def apply(dependencies: Seq[ProjectReference], transitive: Boolean = true): Aggregation = new Explicit(dependencies, transitive)
	implicit def fromBoolean(b: Boolean): Aggregation = if(b) Enabled else Disabled
	val Enabled = new Implicit(true)
	val Disabled = new Implicit(false)
	final case class Implicit(enabled: Boolean) extends Aggregation
	final class Explicit(val dependencies: Seq[ProjectReference], val transitive: Boolean) extends Aggregation

	final case class KeyValue[+T](key: ScopedKey[_], value: T)
	def getTasks[T](key: ScopedKey[T], structure: BuildStructure, transitive: Boolean): Seq[KeyValue[T]] =
		getTasks0(key, structure, transitive, new mutable.HashMap[(ScopedKey[_], Boolean), Seq[KeyValue[T]]])
	private type Memo[T] = mutable.Map[(ScopedKey[_], Boolean), Seq[KeyValue[T]]]
	private[this] def getTasks0[T](key: ScopedKey[T], structure: BuildStructure, transitive: Boolean, memo: Memo[T]): Seq[KeyValue[T]] =
		memo.getOrElseUpdate( (key, transitive), {
			val task = structure.data.get(key.scope, key.key).toList.map(t => KeyValue(key,t))
			if(transitive) aggregateDeps(key, structure, memo) ++ task else task
		})
	def projectAggregate(key: ScopedKey[_], structure: BuildStructure): Seq[ProjectRef] =
	{
		val project = key.scope.project.toOption.flatMap { ref => Project.getProjectForReference(ref, structure) }
		project match { case Some(p) => p.aggregate; case None => Nil }
	}
	private[this] def aggregateDeps[T](key: ScopedKey[T], structure: BuildStructure, memo: Memo[T]): Seq[KeyValue[T]] =
	{
		val aggregated = aggregate in Scope.fillTaskAxis(key.scope, key.key) get structure.data getOrElse Enabled
		val (agg, transitive) =
			aggregated match
			{
				case Implicit(false) => (Nil, false)
				case Implicit(true) => (projectAggregate(key, structure), true)
				case e: Explicit => (e.dependencies, e.transitive)
			}
		val currentBuild = key.scope.project.toOption.flatMap { case ProjectRef(uri, _) => Some(uri); case BuildRef(ref) => Some(ref); case _ => None }
		agg flatMap { a =>
			val resolved = subCurrentBuild(a, currentBuild)
			val newKey = ScopedKey(key.scope.copy(project = Select(resolved)), key.key)
			getTasks(newKey, structure, transitive)
		}
	}
	private def subCurrentBuild(ref: Reference, currentBuild: Option[URI]): Reference =
		currentBuild match
		{
			case None => ref
			case Some(current) => Scope.resolveBuildOnly(current, ref)
		}

	def printSettings[T](xs: Seq[KeyValue[T]], log: Logger) =
		xs match
		{
			case KeyValue(_,x) :: Nil => log.info(x.toString)
			case _ => xs foreach { case KeyValue(key, value) => log.info(Project.display(key) + "\n\t" + value.toString) }
		}
	type Values[T] = Seq[KeyValue[T]]
	def seqParser[T](ps: Values[Parser[T]]): Parser[Seq[KeyValue[T]]]  =  seq(ps.map { case KeyValue(k,p) => p.map(v => KeyValue(k,v) ) })

	def applyTasks[T](s: State, structure: BuildStructure, ps: Values[Parser[Task[T]]], show: Boolean): Parser[() => State] =
		Command.applyEffect(seqParser(ps)) { ts =>
			runTasks(s, structure, ts, Dummies(KNil, HNil), show)
			s
		}
	def runTasks[HL <: HList, T](s: State, structure: Load.BuildStructure, ts: Values[Task[T]], extra: Dummies[HL], show: Boolean)
	{
			import EvaluateTask._
			import std.TaskExtra._
		val toRun = ts map { case KeyValue(k,t) => t.map(v => KeyValue(k,v)) } join;
		val start = System.currentTimeMillis
		val result = withStreams(structure){ str => runTask(toRun, str, structure.index.triggers)(nodeView(s, str, extra.tasks, extra.values)) }
		val stop = System.currentTimeMillis
		val log = logger(s)
		lazy val extracted = Project.extract(s)

		val success = result match { case Value(_) => true; case Inc(_) => false }
		try { onResult(result, log) { results => if(show) printSettings(results, log) } }
		finally { printSuccess(start, stop, Project.extract(s), success, log) }
	}
	def printSuccess(start: Long, stop: Long, extracted: Extracted, success: Boolean, log: Logger)
	{
		import extracted._
		lazy val enabled = showSuccess in extracted.currentRef get extracted.structure.data getOrElse true
		if(enabled)
		{
			val timingEnabled = showTiming in currentRef get structure.data getOrElse true
			if(timingEnabled)
			{
				val msg = timingString(start, stop, "", structure.data, currentRef, log)
				if(success) log.success(msg) else log.error(msg)
			}
			else if(success)
				log.success("")
		}
	}
	private def timingString(startTime: Long, endTime: Long, s: String, data: Settings[Scope], currentRef: ProjectRef, log: Logger): String =
	{
		val format = timingFormat in currentRef get data getOrElse defaultFormat
		timing(format, startTime, endTime, "", log)
	}
	def timing(format: java.text.DateFormat, startTime: Long, endTime: Long, s: String, log: Logger): String =
	{
		val ss = if(s.isEmpty) "" else s + " "
		val nowString = format.format(new java.util.Date(endTime))
		"Total " + ss + "time: " + (endTime - startTime + 500) / 1000 + " s, completed " + nowString
	}
	def defaultFormat =
	{
		import java.text.DateFormat
		DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
	}

	final case class Dummies[HL <: HList](tasks: KList[Task,HL], values: HL)
	private[this] def dummyMap[HL <: HList, I](vs: Values[I], data: Settings[Scope], dummies: Dummies[HL]): Dummies[HL2] forSome { type HL2 <: HList } =
		vs match
		{
			case Seq() => dummies
			case Seq(kv: KeyValue[t], xs @ _*) =>
				val dummyParsed = dummyParsedTask(kv.key, data).asInstanceOf[Task[t]]
				dummyMap(xs, data, Dummies(KCons(dummyParsed, dummies.tasks), HCons(kv.value, dummies.values)))
		}
	def applyDynamicTasks[I](s: State, structure: BuildStructure, inputs: Values[InputDynamic[I]], show: Boolean): Parser[() => State] =
	{
		val parsers = inputs.map { case KeyValue(k,t) => KeyValue(k, t parser s) }
		Command.applyEffect(seqParser(parsers)) { parseds =>
			import EvaluateTask._
			val dummies = dummyMap(parseds, structure.data, Dummies(KNil, HNil))
			val roots = inputs.map { case KeyValue(k,t) => KeyValue(k,t.task) }
			runTasks(s, structure, roots, dummies, show)
			s
		}
	}

	private[this] def dummyParsedTask(key: ScopedKey[_], data: Settings[Scope]): Task[_] =
		data.get(Scope.fillTaskAxis(key.scope, key.key), parseResult.key) getOrElse error("Parsed result dummy task not found in " + Project.display(key))
		
	def valueParser(s: State, structure: BuildStructure, show: Boolean)(key: ScopedKey[_]): Parser[() => State] =
		getTasks(key, structure, true).toList match
		{
			case Nil => failure("No such setting/task: " + (Project display key))
			case xs @ KeyValue(_, _: InputStatic[t]) :: _ => applyTasks(s, structure, maps(xs.asInstanceOf[Values[InputStatic[t]]])(_.parser(s)), show)
			case xs @ KeyValue(_, _: InputDynamic[t]) :: _ => applyDynamicTasks(s, structure, xs.asInstanceOf[Values[InputDynamic[t]]], show)
			case xs @ KeyValue(_, _: Task[t]) :: _ => applyTasks(s, structure, maps(xs.asInstanceOf[Values[Task[t]]])(x => success(x)), show)
			case xs => success(() => { printSettings(xs, logger(s)); s} )
		}
	private[this] def maps[T, S](vs: Values[T])(f: T => S): Values[S] =
		vs map { case KeyValue(k,v) => KeyValue(k, f(v)) }
}