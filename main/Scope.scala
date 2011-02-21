/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI

final case class Scope(project: ScopeAxis[ProjectRef], config: ScopeAxis[ConfigKey], task: ScopeAxis[AttributeKey[_]], extra: ScopeAxis[AttributeMap])
object Scope
{
	val ThisScope = Scope(This, This, This, This)
	val GlobalScope = Scope(Global, Global, Global, Global)

	def resolveScope(thisScope: Scope, current: URI, rootProject: URI => String): Scope => Scope =
		resolveProject(current, rootProject) compose replaceThis(thisScope)

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
		
	def resolveProject(uri: URI, rootProject: URI => String): Scope => Scope =
		{
			case Scope(Select(ref), a,b,c) =>
				Scope(Select(mapRef(uri, rootProject, ref)), a,b,c)
			case x => x
		}

	def mapRef(current: URI, rootProject: URI => String, ref: ProjectRef): ProjectRef =
	{
		val (uri, id) = resolveRef(current, rootProject, ref)
		ProjectRef(Some(uri), Some(id))
	}
	def mapRefBuild(current: URI, ref: ProjectRef): ProjectRef = ProjectRef(Some(resolveBuild(current, ref)), ref.id)
		
	def resolveBuild(current: URI, ref: ProjectRef): URI =
		ref.uri match { case Some(u) => resolveBuild(current, u); case None => current }
	def resolveBuild(current: URI, uri: URI): URI = 
		IO.directoryURI(current resolve uri)

	def resolveRef(current: URI, rootProject: URI => String, ref: ProjectRef): (URI, String) =
	{
		val uri = resolveBuild(current, ref)
		(uri, ref.id getOrElse rootProject(uri) )
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
		val pref = if(projectID eq null) This else Select(ProjectRef(None, Some(projectID)))
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
	def delegates(projectInherit: ProjectRef => Seq[ProjectRef],
		configInherit: (ProjectRef, ConfigKey) => Seq[ConfigKey],
		taskInherit: (ProjectRef, AttributeKey[_]) => Seq[AttributeKey[_]],
		extraInherit: (ProjectRef, AttributeMap) => Seq[AttributeMap])(scope: Scope): Seq[Scope] =
	scope.project match
	{
		case Global | This => scope :: GlobalScope :: Nil
		case Select(proj) =>
			val prod =
				for {
					c <- linearize(scope.config)(configInherit(proj, _))
					t <- linearize(scope.task)(taskInherit(proj,_))
					e <- linearize(scope.extra)(extraInherit(proj,_))
				} yield
					Scope(Select(proj),c,t,e)
			val projI =
				linearize(scope.project)(projectInherit) map { p => scope.copy(project = p) }

			(prod ++ projI :+ GlobalScope).toList.removeDuplicates
	}
	def linearize[T](axis: ScopeAxis[T])(inherit: T => Seq[T]): Seq[ScopeAxis[T]] =
		axis match
		{
			case Select(x) => (Global +: Dag.topologicalSort(x)(inherit).map(Select.apply) ).reverse
			case Global | This => Global :: Nil
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
}
case object This extends ScopeAxis[Nothing]
case object Global extends ScopeAxis[Nothing]
final case class Select[S](s: S) extends ScopeAxis[S]

final case class ConfigKey(name: String)
object ConfigKey
{
	implicit def configurationToKey(c: Configuration): ConfigKey = ConfigKey(c.name)
}