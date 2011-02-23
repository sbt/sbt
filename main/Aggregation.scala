/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import CommandSupport.logger
	import Project.ScopedKey
	import Load.BuildStructure
	import EvaluateTask.parseResult
	import Keys.Aggregate
	import sbt.complete.Parser
	import Parser._

sealed trait Aggregation
final object Aggregation
{
	def apply(dependencies: Seq[ProjectRef], transitive: Boolean = true): Aggregation = new Explicit(dependencies, transitive)
	implicit def fromBoolean(b: Boolean): Aggregation = if(b) Enabled else Disabled
	val Enabled = new Implicit(true)
	val Disabled = new Implicit(false)
	final case class Implicit(enabled: Boolean) extends Aggregation
	final class Explicit(val dependencies: Seq[ProjectRef], val transitive: Boolean) extends Aggregation

	final case class KeyValue[+T](key: ScopedKey[_], value: T)
	def getTasks[T](key: ScopedKey[T], structure: BuildStructure, transitive: Boolean): Seq[KeyValue[T]] =
	{
		val task = structure.data.get(key.scope, key.key).toList.map(t => KeyValue(key,t))
		if(transitive) aggregate(key, structure) ++ task else task
	}
	def projectAggregate(key: ScopedKey[_], structure: BuildStructure): Seq[ProjectRef] =
	{
		val project = key.scope.project.toOption.flatMap { p => Project.getProject(p, structure) }
		project match { case Some(p) => p.aggregate; case None => Nil }
	}
	def aggregate[T](key: ScopedKey[T], structure: BuildStructure): Seq[KeyValue[T]] =
	{
		val aggregated = Aggregate in Scope.fillTaskAxis(key.scope, key.key) get structure.data getOrElse Enabled
		val (agg, transitive) =
			aggregated match
			{	
				case Implicit(false) => (Nil, false)	
				case Implicit(true) => (projectAggregate(key, structure), true)	
				case e: Explicit => (subCurrentBuild(key, e.dependencies), e.transitive)	
			}

		agg flatMap { a =>
			val newKey = ScopedKey(key.scope.copy(project = Select(a)), key.key)
			getTasks(newKey, structure, transitive)
		}
	}
	private def subCurrentBuild(key: ScopedKey[_], refs: Seq[ProjectRef]): Seq[ProjectRef] =
		key.scope.project match
		{
			case Select(ProjectRef(Some(current), _)) => refs map { ref => Scope.mapRefBuild(current, ref) }
			case _ => refs
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
		val result = withStreams(structure){ str => runTask(toRun)(nodeView(s, str, extra.tasks, extra.values)) }
		val log = logger(s)
		onResult(result, log)( results => if(show) printSettings(results, log))
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
			case Nil => failure("Invalid setting or task")
			case xs @ KeyValue(_, _: InputStatic[t]) :: _ => applyTasks(s, structure, maps(xs.asInstanceOf[Values[InputStatic[t]]])(_.parser(s)), show)
			case xs @ KeyValue(_, _: InputDynamic[t]) :: _ => applyDynamicTasks(s, structure, xs.asInstanceOf[Values[InputDynamic[t]]], show)
			case xs @ KeyValue(_, _: Task[t]) :: _ => applyTasks(s, structure, maps(xs.asInstanceOf[Values[Task[t]]])(x => success(x)), show)
			case xs => success(() => { printSettings(xs, logger(s)); s} )
		}
	private[this] def maps[T, S](vs: Values[T])(f: T => S): Values[S] =
		vs map { case KeyValue(k,v) => KeyValue(k, f(v)) }
}