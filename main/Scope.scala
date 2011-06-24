/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Types.some

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
			case LocalRootProject => RootProject(current)
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
			case ThisProject | LocalRootProject => ProjectRef(current, rootProject(current))
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
	def delegates[Proj](
		refs: Seq[(ProjectRef, Proj)],
		configurations: Proj => Seq[ConfigKey],
		resolve: Reference => ResolvedReference,
		rootProject: URI => String,
		projectInherit: ProjectRef => Seq[ProjectRef],
		configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey],
		taskInherit: AttributeKey[_] => Seq[AttributeKey[_]],
		extraInherit: (ResolvedReference, AttributeMap) => Seq[AttributeMap]): Scope => Seq[Scope] =
	{
		val index = delegates(refs, configurations, projectInherit, configInherit)
		scope => indexedDelegates(resolve, index, rootProject, taskInherit, extraInherit)(scope)
	}

	def indexedDelegates(
		resolve: Reference => ResolvedReference,
		index: DelegateIndex,
		rootProject: URI => String,
		taskInherit: AttributeKey[_] => Seq[AttributeKey[_]],
		extraInherit: (ResolvedReference, AttributeMap) => Seq[AttributeMap])(rawScope: Scope): Seq[Scope] =
	{
		val scope = Scope.replaceThis(GlobalScope)(rawScope)
	
		def nonProjectScopes(resolvedProj: ResolvedReference)(px: ScopeAxis[ResolvedReference]) =
		{
			val p = px.toOption getOrElse resolvedProj
			val configProj = p match { case pr: ProjectRef => pr; case br: BuildRef => ProjectRef(br.build, rootProject(br.build)) }
			val cLin = scope.config match { case Select(conf) => index.config(configProj, conf); case _ => withGlobalAxis(scope.config) }
			val tLin = scope.task match { case t @ Select(task) => linearize(t)(taskInherit); case _ => withGlobalAxis(scope.task) }
			val eLin = withGlobalAxis(scope.extra)
			for(c <- cLin; t <- tLin; e <- eLin) yield Scope(px, c, t, e)
		}
		scope.project match
		{
			case Global | This => globalProjectDelegates(scope)
			case Select(proj) =>
				val resolvedProj = resolve(proj)
				val projAxes: Seq[ScopeAxis[ResolvedReference]] =
					resolvedProj match
					{
						case pr: ProjectRef => index.project(pr)
						case br: BuildRef => Select(br) :: Global :: Nil
					}
				projAxes flatMap nonProjectScopes(resolvedProj)
		}
	}

	def withGlobalAxis[T](base: ScopeAxis[T]): Seq[ScopeAxis[T]] = if(base.isSelect) base :: Global :: Nil else Global :: Nil
	def withGlobalScope(base: Scope): Seq[Scope] = if(base == GlobalScope) GlobalScope :: Nil else base :: GlobalScope :: Nil
	def withRawBuilds(ps: Seq[ScopeAxis[ProjectRef]]): Seq[ScopeAxis[ResolvedReference]] =
		ps ++ (ps flatMap rawBuild).distinct :+ Global

	def rawBuild(ps: ScopeAxis[ProjectRef]): Seq[ScopeAxis[BuildRef]]  =  ps match { case Select(ref) => Select(BuildRef(ref.build)) :: Nil; case _ => Nil }

	def delegates[Proj](
		refs: Seq[(ProjectRef, Proj)],
		configurations: Proj => Seq[ConfigKey],
		projectInherit: ProjectRef => Seq[ProjectRef],
		configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey]): DelegateIndex =
	{
		val pDelegates = refs map { case (ref, project) =>
			(ref, delegateIndex(ref, configurations(project))(projectInherit, configInherit) )
		} toMap ;
		new DelegateIndex0(pDelegates)
	}
	private[this] def delegateIndex(ref: ProjectRef, confs: Seq[ConfigKey])(projectInherit: ProjectRef => Seq[ProjectRef], configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey]): ProjectDelegates =
	{
		val refDelegates = withRawBuilds(linearize(Select(ref), false)(projectInherit))
		val configs = confs map { c => axisDelegates(configInherit, ref, c) }
		new ProjectDelegates(ref, refDelegates, configs.toMap)
	}
	def axisDelegates[T](direct: (ResolvedReference, T) => Seq[T], ref: ResolvedReference, init: T): (T, Seq[ScopeAxis[T]]) =
		( init,  linearize(Select(init))(direct(ref, _)) )

	def linearize[T](axis: ScopeAxis[T], appendGlobal: Boolean = true)(inherit: T => Seq[T]): Seq[ScopeAxis[T]] =
		axis match
		{
			case Select(x) => topologicalSort(x, appendGlobal)(inherit)
			case Global | This => if(appendGlobal) Global :: Nil else Nil
		}

	def topologicalSort[T](node: T, appendGlobal: Boolean)(dependencies: T => Seq[T]): Seq[ScopeAxis[T]] =
	{
		val o = Dag.topologicalSortUnchecked(node)(dependencies).map(Select.apply)
		if(appendGlobal) o ::: Global :: Nil else o
	}
	def globalProjectDelegates(scope: Scope): Seq[Scope] =
		if(scope == GlobalScope)
			GlobalScope :: Nil
		else
			for( c <- withGlobalAxis(scope.config); t <- withGlobalAxis(scope.task); e <- withGlobalAxis(scope.extra) ) yield Scope(Global, c, t, e)
}


sealed trait ScopeAxis[+S] {
	def foldStrict[T](f: S => T, ifGlobal: T, ifThis: T): T = fold(f, ifGlobal, ifThis)
	def fold[T](f: S => T, ifGlobal: => T, ifThis: => T): T = this match {
		case This => ifThis
		case Global => ifGlobal
		case Select(s) => f(s)
	}
	def toOption: Option[S] = foldStrict(some.fn, None, None)
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

sealed trait DelegateIndex
{
	def project(ref: ProjectRef): Seq[ScopeAxis[ResolvedReference]]
	def config(ref: ProjectRef, conf: ConfigKey): Seq[ScopeAxis[ConfigKey]]
//	def task(ref: ProjectRef, task: ScopedKey[_]): Seq[ScopeAxis[ScopedKey[_]]]
//	def extra(ref: ProjectRef, e: AttributeMap): Seq[ScopeAxis[AttributeMap]]
}
private final class DelegateIndex0(refs: Map[ProjectRef, ProjectDelegates]) extends DelegateIndex
{
	def project(ref: ProjectRef): Seq[ScopeAxis[ResolvedReference]] = refs.get(ref) match { case Some(pd) => pd.refs; case None => Nil }
	def config(ref: ProjectRef, conf: ConfigKey): Seq[ScopeAxis[ConfigKey]] =
		refs.get(ref) match {
			case Some(pd) => pd.confs.get(conf) match { case Some(cs) => cs; case None => Select(conf) :: Global :: Nil }
			case None => Select(conf) :: Global :: Nil
		}
}
private final class ProjectDelegates(val ref: ProjectRef, val refs: Seq[ScopeAxis[ResolvedReference]], val confs: Map[ConfigKey, Seq[ScopeAxis[ConfigKey]]])