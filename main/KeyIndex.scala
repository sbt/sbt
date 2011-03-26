/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.net.URI
	import Project.ScopedKey
	import complete.DefaultParsers.validID

object KeyIndex
{
	def empty: ExtendableKeyIndex = new KeyIndex0(emptyBuildIndex)
	def apply(known: Seq[ScopedKey[_]]): ExtendableKeyIndex =
		(empty /: known) { _ add _ }
	def combine(indices: Seq[KeyIndex]): KeyIndex = new KeyIndex {
		def buildURIs = concat(_.buildURIs)
		def projects(uri: URI) = concat(_.projects(uri))
		def configs(proj: Option[ResolvedReference]) = concat(_.configs(proj))
		def keys(proj: Option[ResolvedReference], conf: Option[String]) = concat(_.keys(proj, conf))
		def concat[T](f: KeyIndex => Set[T]): Set[T] =
			(Set.empty[T] /: indices)( (s,k) => s ++ f(k) )
	}
	private[sbt] def getOr[A,B](m: Map[A,B], key: A, or: B): B  =  m.getOrElse(key, or)
	private[sbt] def keySet[A,B](m: Map[Option[A],B]): Set[A]  =  m.keys.flatten.toSet
	private[sbt] val emptyAKeyIndex = new AKeyIndex(Map.empty)
	private[sbt] val emptyProjectIndex = new ProjectIndex(Map.empty)
	private[sbt] val emptyBuildIndex = new BuildIndex(Map.empty)
}
import KeyIndex._

trait KeyIndex
{
	def buildURIs: Set[URI]
	def projects(uri: URI): Set[String]
	def configs(proj: Option[ResolvedReference]): Set[String]
	def keys(proj: Option[ResolvedReference], conf: Option[String]): Set[String]
}
trait ExtendableKeyIndex extends KeyIndex
{
	def add(scoped: ScopedKey[_]): ExtendableKeyIndex
}
private final class AKeyIndex(val data: Map[ Option[String], Set[String]])
{
	def add(config: Option[String], key: AttributeKey[_]): AKeyIndex =
		new AKeyIndex(data updated (config, keys(config) + key.label))
	def keys(conf: Option[String]): Set[String]  =  getOr(data, conf, Set.empty)
	def configs: Set[String]  =  keySet(data)
}
private final class ProjectIndex(val data: Map[Option[String], AKeyIndex])
{
	def add(id: Option[String], config: Option[String], key: AttributeKey[_]): ProjectIndex =
		new ProjectIndex( data updated(id, confIndex(id).add(config, key) ))
	def confIndex(id: Option[String]): AKeyIndex  =  getOr(data, id, emptyAKeyIndex)
	def projects: Set[String] = keySet(data)
}
private final class BuildIndex(val data: Map[Option[URI], ProjectIndex])
{
	def add(build: Option[URI], project: Option[String], config: Option[String], key: AttributeKey[_]): BuildIndex =
		new BuildIndex( data updated(build, projectIndex(build).add(project,config,key) ) )
	def projectIndex(build: Option[URI]): ProjectIndex  =  getOr(data, build, emptyProjectIndex)
	def builds: Set[URI] = keySet(data)
}
private final class KeyIndex0(val data: BuildIndex) extends ExtendableKeyIndex
{
	def buildURIs: Set[URI] = data.builds
	def projects(uri: URI): Set[String] = data.projectIndex(Some(uri)).projects
	def configs(project: Option[ResolvedReference]): Set[String] = confIndex(project).configs
	def keys(project: Option[ResolvedReference], conf: Option[String]): Set[String] = confIndex(project).keys(conf)

	def confIndex(proj: Option[ResolvedReference]): AKeyIndex =
	{
		val (build, project) = parts(proj)
		data.projectIndex(build).confIndex(project)
	}
	def parts(proj: Option[Reference]): (Option[URI], Option[String]) = 
		proj match
		{
			case Some(ProjectRef(uri, id)) => (Some(uri), Some(id))
			case Some(BuildRef(uri)) => (Some(uri), None)
			case _ => (None, None)
		}

	def add(scoped: ScopedKey[_]): ExtendableKeyIndex =
		if(validID(scoped.key.label)) add0(scoped) else this
	private[this] def add0(scoped: ScopedKey[_]): ExtendableKeyIndex =
	{
		val (build, project) = parts(scoped.scope.project.toOption)
		add(build, project, scoped.scope.config, scoped.key)
	}
	def add(uri: Option[URI], id: Option[String], config: ScopeAxis[ConfigKey], key: AttributeKey[_]): ExtendableKeyIndex =
		new KeyIndex0( data.add(uri, id, config match { case Select(c) => Some(c.name); case _ => None }, key) )
}
