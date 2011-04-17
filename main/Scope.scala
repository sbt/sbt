/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI

final case class Scope(project: ScopeAxis[Reference], config: ScopeAxis[ConfigKey], task: ScopeAxis[AttributeKey[_]], extra: ScopeAxis[AttributeMap])
object Scope
{
	val ThisScope = Scope(This, This, This, This)
	val GlobalScope = Scope(Global, Global, Global, Global)

	def resolveScope(thisScope: Scope, current: URI, rootProject: URI => String): Scope => Scope =
		resolveProject(current, rootProject) compose replaceThis(thisScope)

	def resolveBuildScope(thisScope: Scope, current: URI): Scope => Scope =
		buildResolve(current) compose replaceThis(thisScope)

	def replaceThis(thisScope: Scope): Scope => Scope = (scope: Scope) =>
		Scope(subThis(thisScope.project, scope.project), subThis(thisScope.config, scope.config), subThis(thisScope.task, scope.task), subThis(thisScope.extra, scope.extra))
		
	def subThis[T](sub: ScopeAxis[T], into: ScopeAxis[T]): ScopeAxis[T] =
		if(into == This) sub else into

	def fillTaskAxis(scope: Scope, key: AttributeKey[_]): Scope =
		scope.task match
		{
			case _: Select[_] => scope
			case _ => scope.copy(task = Select(key))
		}
		
	def mapReference(f: Reference => Reference): Scope => Scope =
		{
			case Scope(Select(ref), a,b,c) => Scope(Select(f(ref)), a,b,c)
			case x => x
		}
	def resolveProject(uri: URI, rootProject: URI => String): Scope => Scope =
		mapReference(ref => resolveReference(uri, rootProject, ref))
	def buildResolve(uri: URI): Scope => Scope =
		mapReference(ref => resolveBuildOnly(uri, ref))

	def resolveBuildOnly(current: URI, ref: Reference): Reference =
		ref match
		{
			case br: BuildReference => resolveBuild(current, br)
			case pr: ProjectReference => resolveProjectBuild(current, pr)
		}
	def resolveBuild(current: URI, ref: BuildReference): BuildReference =
		ref match
		{
			case ThisBuild => BuildRef(current)
			case BuildRef(uri) => BuildRef(resolveBuild(current, uri))
		}
	def resolveProjectBuild(current: URI, ref: ProjectReference): ProjectReference =
		ref match
		{
			case ThisProject => RootProject(current)
			case LocalProject(id) => ProjectRef(current, id)
			case RootProject(uri) => RootProject(resolveBuild(current, uri))
			case ProjectRef(uri, id) => ProjectRef(resolveBuild(current, uri), id)
		}
	def resolveBuild(current: URI, uri: URI): URI = 
		IO.directoryURI(current resolve uri)

	def resolveReference(current: URI, rootProject: URI => String, ref: Reference): ResolvedReference =
		ref match
		{
			case br: BuildReference => resolveBuildRef(current, br)
			case pr: ProjectReference => resolveProjectRef(current, rootProject, pr)
		}
		
	def resolveProjectRef(current: URI, rootProject: URI => String, ref: ProjectReference): ProjectRef =
		ref match
		{
			case ThisProject => ProjectRef(current, rootProject(current))
			case LocalProject(id) => ProjectRef(current, id)
			case RootProject(uri) => val res = resolveBuild(current, uri); ProjectRef(res, rootProject(res))
			case ProjectRef(uri, id) => ProjectRef(resolveBuild(current, uri), id)
		}
	def resolveBuildRef(current: URI, ref: BuildReference): BuildRef =
		ref match
		{
			case ThisBuild => BuildRef(current)
			case BuildRef(uri) => BuildRef(resolveBuild(current, uri))
		}

	def display(config: ConfigKey): String = if(config.name == "compile") "" else config.name + ":"
	def display(scope: Scope, sep: String): String = 
	{
			import scope.{project, config, task, extra}
		val projectPrefix = project.foldStrict(Project.display, "*", ".")
		val configPrefix = config.foldStrict(display, "*:", ".:")
		val taskPostfix = task.foldStrict(x => ("for " + x.label) :: Nil, Nil, Nil)
		val extraPostfix = extra.foldStrict(_.entries.map( _.toString ).toList, Nil, Nil)
		val extras = taskPostfix ::: extraPostfix
		val postfix = if(extras.isEmpty) "" else extras.mkString("(", ", ", ")")
		projectPrefix + "/" + configPrefix + sep + postfix
	}

	def parseScopedKey(command: String): (Scope, String) =
	{
		val ScopedKeyRegex(_, projectID, _, config, key) = command
		val pref = if(projectID eq null) This else Select(LocalProject(projectID))
		val conf = if(config eq null) This else Select(ConfigKey(config))
		(Scope(pref, conf, This, This), transformTaskName(key))
	}
	val ScopedKeyRegex = """((\w+)\/)?((\w+)\:)?([\w\-]+)""".r
	
	def transformTaskName(s: String) =
	{
		val parts = s.split("-+")
		(parts.take(1) ++ parts.drop(1).map(_.capitalize)).mkString
	}

	// *Inherit functions should be immediate delegates and not include argument itself.  Transitivity will be provided by this method
	def delegates(
		resolve: Reference => ResolvedReference,
		projectInherit: ResolvedReference => Seq[ResolvedReference],
		configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey],
		taskInherit: (ResolvedReference, AttributeKey[_]) => Seq[AttributeKey[_]],
		extraInherit: (ResolvedReference, AttributeMap) => Seq[AttributeMap])(rawScope: Scope): Seq[Scope] =
	{
		val scope = Scope.replaceThis(GlobalScope)(rawScope)
	
		def nonProjectScopes(resolvedProj: ResolvedReference)(px: ScopeAxis[ResolvedReference]) =
		{
			val p = px.toOption getOrElse resolvedProj
			val cLin = linearize(scope.config)(configInherit(p, _))
			val tLin = linearize(scope.task)(taskInherit(p,_))
			val eLin = linearize(scope.extra)(extraInherit(p,_))
			for(c <- cLin; t <- tLin; e <- eLin) yield Scope(px, c, t, e)
		}
		scope.project match
		{
			case Global => withGlobalScope(scope)
			case This => withGlobalScope(scope.copy(project = Global))
			case Select(proj) =>
				val resolvedProj = resolve(proj)
				val prod = withRawBuilds(linearize(scope.project map resolve, Nil)(projectInherit)) flatMap nonProjectScopes(resolvedProj)
				(prod :+ GlobalScope).distinct
		}
	}

	def withGlobalScope(base: Scope): Seq[Scope] = if(base == GlobalScope) GlobalScope :: Nil else base :: GlobalScope :: Nil
	def withRawBuilds(ps: Seq[ScopeAxis[ResolvedReference]]): Seq[ScopeAxis[ResolvedReference]] =
		(ps ++ (ps flatMap rawBuilds).map(Select.apply) :+ Global).distinct

	def rawBuilds(ps: ScopeAxis[ResolvedReference]): Seq[ResolvedReference]  =  ps match { case Select(ref) => rawBuilds(ref); case _ => Nil }
	def rawBuilds(ps: ResolvedReference): Seq[ResolvedReference]  =  (Reference.uri(ps) map BuildRef.apply).toList

	def linearize[T](axis: ScopeAxis[T], append: Seq[ScopeAxis[T]] = Global :: Nil)(inherit: T => Seq[T]): Seq[ScopeAxis[T]] =
		axis match
		{
			case Select(x) => (Dag.topologicalSort(x)(inherit).map(Select.apply).reverse ++ append).distinct
			case Global | This => append
		}
}


sealed trait ScopeAxis[+S] {
	def foldStrict[T](f: S => T, ifGlobal: T, ifThis: T): T = fold(f, ifGlobal, ifThis)
	def fold[T](f: S => T, ifGlobal: => T, ifThis: => T): T = this match {
		case This => ifThis
		case Global => ifGlobal
		case Select(s) => f(s)
	}
	def toOption: Option[S] = foldStrict(Some.apply, None, None)
	def map[T](f: S => T): ScopeAxis[T] = foldStrict(s => Select(f(s)), Global, This)
	def isSelect: Boolean = false
}
case object This extends ScopeAxis[Nothing]
case object Global extends ScopeAxis[Nothing]
final case class Select[S](s: S) extends ScopeAxis[S] {
	override def isSelect = true
}
object ScopeAxis
{
	implicit def scopeAxisToScope(axis: ScopeAxis[Nothing]): Scope =
		Scope(axis, axis, axis, axis)
}

final case class ConfigKey(name: String)
object ConfigKey
{
	implicit def configurationToKey(c: Configuration): ConfigKey = ConfigKey(c.name)
}