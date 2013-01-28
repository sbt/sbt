/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Def.ScopedKey
	import Keys.{aggregate, showSuccess, showTiming, timingFormat}
	import sbt.complete.Parser
	import java.net.URI
	import Parser._
	import collection.mutable
	import std.Transform.{DummyTaskMap, TaskAndValue}

sealed trait Aggregation
final object Aggregation
{
	final case class KeyValue[+T](key: ScopedKey[_], value: T)

	def printSettings[T](xs: Seq[KeyValue[T]], log: Logger)(implicit display: Show[ScopedKey[_]]) =
		xs match
		{
			case KeyValue(_,x) :: Nil => log.info(x.toString)
			case _ => xs foreach { case KeyValue(key, value) => log.info(display(key) + "\n\t" + value.toString) }
		}
	type Values[T] = Seq[KeyValue[T]]
	type AnyKeys = Values[_]
	def seqParser[T](ps: Values[Parser[T]]): Parser[Seq[KeyValue[T]]]  =  seq(ps.map { case KeyValue(k,p) => p.map(v => KeyValue(k,v) ) })

	def applyTasks[T](s: State, structure: BuildStructure, ps: Values[Parser[Task[T]]], show: Boolean)(implicit display: Show[ScopedKey[_]]): Parser[() => State] =
		Command.applyEffect(seqParser(ps)) { ts =>
			runTasks(s, structure, ts, DummyTaskMap(Nil), show)
		}
	def runTasksWithResult[T](s: State, structure: BuildStructure, ts: Values[Task[T]], extra: DummyTaskMap, show: Boolean)(implicit display: Show[ScopedKey[_]]): (State, Result[Seq[KeyValue[T]]]) =
	{
			import EvaluateTask._
			import std.TaskExtra._

		val extracted = Project extract s
		val toRun = ts map { case KeyValue(k,t) => t.map(v => KeyValue(k,v)) } join;
		val roots = ts map { case KeyValue(k,_) => k }
		val config = extractedConfig(extracted, structure)

		val start = System.currentTimeMillis
		val (newS, result) = withStreams(structure, s){ str =>
			val transform = nodeView(s, str, roots, extra)
			runTask(toRun, s,str, structure.index.triggers, config)(transform)
		}
		val stop = System.currentTimeMillis
		val log = newS.log

		val success = result match { case Value(_) => true; case Inc(_) => false }
		try { onResult(result, log) { results => if(show) printSettings(results, log) } }
		finally { printSuccess(start, stop, extracted, success, log) }

		(newS, result)
	}

  def runTasks[HL <: HList, T](s: State, structure: BuildStructure, ts: Values[Task[T]], extra: DummyTaskMap, show: Boolean)(implicit display: Show[ScopedKey[_]]): State = {
    runTasksWithResult(s, structure, ts, extra, show)._1
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

	def applyDynamicTasks[I](s: State, structure: BuildStructure, inputs: Values[InputTask[I]], show: Boolean)(implicit display: Show[ScopedKey[_]]): Parser[() => State] =
	{
		val parsers = for(KeyValue(k,it) <- inputs) yield it.parser(s).map(v => KeyValue(k,v))
		Command.applyEffect(seq(parsers)) { roots =>
			import EvaluateTask._
			runTasks(s, structure, roots, DummyTaskMap(Nil), show)
		}
	}

	def evaluatingParser[T](s: State, structure: BuildStructure, show: Boolean)(keys: Seq[KeyValue[T]])(implicit display: Show[ScopedKey[_]]): Parser[() => State] =
		keys.toList match
		{
			case Nil => failure("No such setting/task")
			case xs @ KeyValue(_, _: InputTask[t]) :: _ => applyDynamicTasks(s, structure, xs.asInstanceOf[Values[InputTask[t]]], show)
			case xs @ KeyValue(_, _: Task[t]) :: _ => applyTasks(s, structure, maps(xs.asInstanceOf[Values[Task[t]]])(x => success(x)), show)
			case xs => success(() => { printSettings(xs, s.log); s} )
		}
	private[this] def maps[T, S](vs: Values[T])(f: T => S): Values[S] =
		vs map { case KeyValue(k,v) => KeyValue(k, f(v)) }


	def projectAggregates[Proj](proj: Option[Reference], extra: BuildUtil[Proj], reverse: Boolean): Seq[ProjectRef] =
	{
		val resRef = proj.map(p => extra.projectRefFor(extra.resolveRef(p)))
		resRef.toList.flatMap(ref =>
			if(reverse) extra.aggregates.reverse(ref) else extra.aggregates.forward(ref)
		)
	}

	def aggregate[T, Proj](key: ScopedKey[T], rawMask: ScopeMask, extra: BuildUtil[Proj], reverse: Boolean = false): Seq[ScopedKey[T]] =
	{
		val mask = rawMask.copy(project = true)
		Dag.topologicalSort(key) { k =>
			if(reverse)
				reverseAggregatedKeys(k, extra, mask)
			else if(aggregationEnabled(k, extra.data))
				aggregatedKeys(k, extra, mask)
			else
				Nil
		}
	}
	def reverseAggregatedKeys[T](key: ScopedKey[T], extra: BuildUtil[_], mask: ScopeMask): Seq[ScopedKey[T]] =
		projectAggregates(key.scope.project.toOption, extra, reverse = true) flatMap { ref =>
			val toResolve = key.scope.copy(project = Select(ref))
			val resolved = Resolve(extra, Global, key.key, mask)(toResolve)
			val skey = ScopedKey(resolved, key.key)
			if( aggregationEnabled(skey, extra.data) ) skey :: Nil else Nil
		}

	def aggregatedKeys[T](key: ScopedKey[T], extra: BuildUtil[_], mask: ScopeMask): Seq[ScopedKey[T]] =
		projectAggregates(key.scope.project.toOption, extra, reverse = false) map { ref =>
			val toResolve = key.scope.copy(project = Select(ref))
			val resolved = Resolve(extra, Global, key.key, mask)(toResolve)
			ScopedKey(resolved, key.key)
		}
		
	def aggregationEnabled(key: ScopedKey[_], data: Settings[Scope]): Boolean =
		Keys.aggregate in Scope.fillTaskAxis(key.scope, key.key) get data getOrElse true

	@deprecated("Use BuildUtil.aggregationRelation", "0.13.0")
	def relation(units: Map[URI, LoadedBuildUnit]): Relation[ProjectRef, ProjectRef] =
		BuildUtil.aggregationRelation(units)
}