/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI

final case class Scope(project: ScopeAxis[Reference], config: ScopeAxis[ConfigKey], task: ScopeAxis[AttributeKey[_]], extra: ScopeAxis[AttributeMap])
{
	def in(project: Reference, config: ConfigKey): Scope = copy(project = Select(project), config = Select(config))
	def in(config: ConfigKey, task: AttributeKey[_]): Scope = copy(config = Select(config), task = Select(task))
	def in(project: Reference, task: AttributeKey[_]): Scope = copy(project = Select(project), task = Select(task))
	def in(project: Reference, config: ConfigKey, task: AttributeKey[_]): Scope = copy(project = Select(project), config = Select(config), task = Select(task))
	def in(project: Reference): Scope = copy(project = Select(project))
	def in(config: ConfigKey): Scope = copy(config = Select(config))
	def in(task: AttributeKey[_]): Scope = copy(task = Select(task))
}
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
		if(!uri.isAbsolute && current.isOpaque && uri.getSchemeSpecificPart == ".")
			current // this handles the shortcut of referring to the current build using "."
		else
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

	def display(config: ConfigKey): String = config.name + ":"
	def display(scope: Scope, sep: String): String = displayMasked(scope, sep, showProject, ScopeMask())
	def displayMasked(scope: Scope, sep: String, mask: ScopeMask): String = displayMasked(scope, sep, showProject, mask)
	def display(scope: Scope, sep: String, showProject: Reference => String): String =  displayMasked(scope, sep, showProject, ScopeMask())
	def displayMasked(scope: Scope, sep: String, showProject: Reference => String, mask: ScopeMask): String =
	{
			import scope.{project, config, task, extra}
		val configPrefix = config.foldStrict(display, "*:", ".:")
		val taskPrefix = task.foldStrict(_.label + "::", "", ".::")
		val extras = extra.foldStrict(_.entries.map( _.toString ).toList, Nil, Nil)
		val postfix = if(extras.isEmpty) "" else extras.mkString("(", ", ", ")")
		mask.concatShow(projectPrefix(project, showProject), configPrefix, taskPrefix, sep, postfix)
	}

	def equal(a: Scope, b: Scope, mask: ScopeMask): Boolean =
		(!mask.project || a.project == b.project) &&
		(!mask.config || a.config == b.config) &&
		(!mask.task || a.task == b.task) &&
		(!mask.extra || a.extra == b.extra)

	def projectPrefix(project: ScopeAxis[Reference], show: Reference => String = showProject): String = project.foldStrict(show, "*/", "./")
	def showProject = (ref: Reference) => Reference.display(ref) + "/"

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
		configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey],
		taskInherit: AttributeKey[_] => Seq[AttributeKey[_]]): Scope => Seq[Scope] =
	{
		val index = delegates(refs, rootProject, configurations, configInherit)
		scope => indexedDelegates(resolve, index, rootProject, taskInherit)(scope)
	}

	def indexedDelegates(
		resolve: Reference => ResolvedReference,
		index: DelegateIndex,
		rootProject: URI => String,
		taskInherit: AttributeKey[_] => Seq[AttributeKey[_]])(rawScope: Scope): Seq[Scope] =
	{
		val scope = Scope.replaceThis(GlobalScope)(rawScope)

		def linearizeTask(t: AttributeKey[_]): Seq[ScopeAxis[AttributeKey[_]]] =
			linearize[AttributeKey[_]](t)(taskInherit)

		def nonProjectScopes(resolvedProj: ResolvedReference)(px: ScopeAxis[ResolvedReference]) =
		{
			val p = px.toOption getOrElse resolvedProj
			val cLin = scope.config match { case Select(conf) => index.config(p, conf); case _ => withGlobalAxis(scope.config) }
			val tLin = scope.task match { case t @ Select(task) => linearizeTask(task); case _ => withGlobalAxis(scope.task) }
			for(c <- cLin; t <- tLin) yield Scope(px, c, t, scope.extra)
		}
		scope.project match
		{
			case Global | This => globalProjectDelegates(scope)
			case Select(proj) =>
				val resolvedProj = resolve(proj)
				val projAxes: Seq[ScopeAxis[ResolvedReference]] =
					resolvedProj match
					{
						case pr: ProjectRef => Select(pr) :: Select(BuildRef(pr.build)) :: Global :: Nil
						case br: BuildRef => Select(br) :: Global :: Nil
					}
				projAxes flatMap nonProjectScopes(resolvedProj)
		}
	}

	def withGlobalAxis[T](base: ScopeAxis[T]): Seq[ScopeAxis[T]] = if(base.isSelect) base :: Global :: Nil else Global :: Nil
	def withGlobalScope(base: Scope): Seq[Scope] = if(base == GlobalScope) GlobalScope :: Nil else base :: GlobalScope :: Nil

	private[this] def delegates[Proj](
		refs: Seq[(ProjectRef, Proj)],
		rootProject: URI => String,
		configurations: Proj => Seq[ConfigKey],
		configInherit: (ResolvedReference, ConfigKey) => Seq[ConfigKey]): DelegateIndex =
	{
		val pDelegates = refs map { case (ref, project) =>
			val index = new ProjectDelegates(configurations(project).map( c => axisDelegates(configInherit, ref, c) ).toMap)
			(ref, index)
		} toMap ;
		val buildURIs = refs.map(_._1.build).distinct
		val bDelegates = for(build <- buildURIs) yield {
			val ref = ProjectRef(build, rootProject(build))
			(BuildRef(build), pDelegates(ref))
		}
		new DelegateIndex0(pDelegates ++ bDelegates)
	}

	def axisDelegates[T](direct: (ResolvedReference, T) => Seq[T], ref: ResolvedReference, init: T): (T, Seq[ScopeAxis[T]]) =
		( init,  linearize(init)(direct(ref, _)) )

	def globalProjectDelegates(scope: Scope): Seq[Scope] =
		if(scope == GlobalScope)
			GlobalScope :: Nil
		else
			for( c <- withGlobalAxis(scope.config); t <- withGlobalAxis(scope.task) ) yield Scope(Global, c, t, scope.extra)

	// inlined topologicalSort for performance
	private[this] def linearize[T](node: T)(dependencies: T => Seq[T]): Seq[ScopeAxis[T]] =
	{
		val discovered = new java.util.HashSet[T]
		// always include Global at the end
		var finished: List[ScopeAxis[T]] = Global :: Nil

		def visitAll(nodes: Seq[T]) = nodes foreach visit
		def visit(node : T){
			if (!discovered.contains(node)) {
				discovered.add(node)
				visitAll(dependencies(node))
				finished ::= Select(node);
			}
		}

		visit(node)
		finished
	}
}
