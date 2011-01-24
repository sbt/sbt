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
		replaceThis(thisScope) compose resolveProject(current, rootProject)

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
	def resolveRef(current: URI, rootProject: URI => String, ref: ProjectRef): (URI, String) =
	{
		val unURI = ref.uri match { case Some(u) => current resolve u; case None => current }
		val uri = unURI.normalize
		(uri, ref.id getOrElse rootProject(uri))
	}

	def display(config: ConfigKey): String = if(config.name == "compile") "" else config.name + "-"
	def display(scope: Scope, sep: String): String = 
	{
			import scope.{project, config, task, extra}
		val projectPrefix = project match { case Select(p) => "(" + p.uri + ")" + p.id; case Global => "*"; case This => "." }
		val configPrefix = config match { case Select(c) => display(c); case _ => "" }
		val taskPostfix = task match { case Select(t) => " for " + t.label; case _ => "" }
		val extraPostfix = extra match { case Select(es) => es.entries.map( _.toString ).toList; case _ => Nil }
		val extras = taskPostfix :: extraPostfix
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
		case Global | This => scope :: Nil
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

			prod ++ projI
	}
	def linearize[T](axis: ScopeAxis[T])(inherit: T => Seq[T]): Seq[ScopeAxis[T]] =
		axis match
		{
			case Select(x) => Dag.topologicalSort(x)(inherit).map(Select.apply) :+ Global
			case Global | This => Global :: Nil
		}
}


sealed trait ScopeAxis[+S]
object This extends ScopeAxis[Nothing]
object Global extends ScopeAxis[Nothing]
final case class Select[S](s: S) extends ScopeAxis[S]

final case class ConfigKey(name: String)
object ConfigKey
{
	implicit def configurationToKey(c: Configuration): ConfigKey = ConfigKey(c.name)
}