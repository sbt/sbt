/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.net.URI
	import Def.ScopedKey
	import complete.DefaultParsers.validID
	import Types.{idFun, some}

object KeyIndex
{
	def empty: ExtendableKeyIndex = new KeyIndex0(emptyBuildIndex)
	def apply(known: Iterable[ScopedKey[_]]): ExtendableKeyIndex =
		(empty /: known) { _ add _ }
	def aggregate(known: Iterable[ScopedKey[_]], extra: BuildUtil[_]): ExtendableKeyIndex =
		(empty /: known) { (index, key) => index.addAggregated(key, extra) }

	def combine(indices: Seq[KeyIndex]): KeyIndex = new KeyIndex {
		def buildURIs = concat(_.buildURIs)
		def projects(uri: URI) = concat(_.projects(uri))
		def exists(project: Option[ResolvedReference]): Boolean = indices.exists(_ exists project)
		def configs(proj: Option[ResolvedReference]) = concat(_.configs(proj))
		def tasks(proj: Option[ResolvedReference], conf: Option[String]) = concat(_.tasks(proj, conf))
		def tasks(proj: Option[ResolvedReference], conf: Option[String], key: String) = concat(_.tasks(proj, conf, key))
		def keys(proj: Option[ResolvedReference]) = concat(_.keys(proj))
		def keys(proj: Option[ResolvedReference], conf: Option[String]) = concat(_.keys(proj, conf))
		def keys(proj: Option[ResolvedReference], conf: Option[String], task: Option[AttributeKey[_]]) = concat(_.keys(proj, conf, task))
		def concat[T](f: KeyIndex => Set[T]): Set[T] =
			(Set.empty[T] /: indices)( (s,k) => s ++ f(k) )
	}
	private[sbt] def getOr[A,B](m: Map[A,B], key: A, or: B): B  =  m.getOrElse(key, or)
	private[sbt] def keySet[A,B](m: Map[Option[A],B]): Set[A]  =  m.keys.flatten.toSet
	private[sbt] val emptyAKeyIndex = new AKeyIndex(Relation.empty)
	private[sbt] val emptyConfigIndex = new ConfigIndex(Map.empty)
	private[sbt] val emptyProjectIndex = new ProjectIndex(Map.empty)
	private[sbt] val emptyBuildIndex = new BuildIndex(Map.empty)
}
import KeyIndex._

trait KeyIndex
{
	// TODO, optimize
	def isEmpty(proj: Option[ResolvedReference], conf: Option[String]): Boolean = keys(proj, conf).isEmpty
	def isEmpty(proj: Option[ResolvedReference], conf: Option[String], task: Option[AttributeKey[_]]): Boolean = keys(proj, conf, task).isEmpty

	def buildURIs: Set[URI]
	def projects(uri: URI): Set[String]
	def exists(project: Option[ResolvedReference]): Boolean
	def configs(proj: Option[ResolvedReference]): Set[String]
	def tasks(proj: Option[ResolvedReference], conf: Option[String]): Set[AttributeKey[_]]
	def tasks(proj: Option[ResolvedReference], conf: Option[String], key: String): Set[AttributeKey[_]]
	def keys(proj: Option[ResolvedReference]): Set[String]
	def keys(proj: Option[ResolvedReference], conf: Option[String]): Set[String]
	def keys(proj: Option[ResolvedReference], conf: Option[String], task: Option[AttributeKey[_]]): Set[String]
}
trait ExtendableKeyIndex extends KeyIndex
{
	def add(scoped: ScopedKey[_]): ExtendableKeyIndex
	def addAggregated(scoped: ScopedKey[_], extra: BuildUtil[_]): ExtendableKeyIndex
}
// task axis <-> key
private final class AKeyIndex(val data: Relation[ Option[AttributeKey[_]], String])
{
	def add(task: Option[AttributeKey[_]], key: AttributeKey[_]): AKeyIndex  =  new AKeyIndex(data + (task, key.label))
	def keys(task: Option[AttributeKey[_]]): Set[String]  =  data.forward(task)
	def allKeys: Set[String] = data._2s.toSet
	def tasks: Set[AttributeKey[_]]  =  data._1s.flatten.toSet
	def tasks(key: String): Set[AttributeKey[_]]  =  data.reverse(key).flatten.toSet
}
private final class ConfigIndex(val data: Map[ Option[String], AKeyIndex])
{
	def add(config: Option[String], task: Option[AttributeKey[_]], key: AttributeKey[_]): ConfigIndex =
		new ConfigIndex(data updated (config, keyIndex(config).add(task,key) ))
	def keyIndex(conf: Option[String]): AKeyIndex  =  getOr(data, conf, emptyAKeyIndex)
	def configs: Set[String]  =  keySet(data)
}
private final class ProjectIndex(val data: Map[Option[String], ConfigIndex])
{
	def add(id: Option[String], config: Option[String], task: Option[AttributeKey[_]], key: AttributeKey[_]): ProjectIndex =
		new ProjectIndex( data updated(id, confIndex(id).add(config, task, key) ))
	def confIndex(id: Option[String]): ConfigIndex  =  getOr(data, id, emptyConfigIndex)
	def projects: Set[String] = keySet(data)
}
private final class BuildIndex(val data: Map[Option[URI], ProjectIndex])
{
	def add(build: Option[URI], project: Option[String], config: Option[String], task: Option[AttributeKey[_]], key: AttributeKey[_]): BuildIndex =
		new BuildIndex( data updated(build, projectIndex(build).add(project,config,task,key) ) )
	def projectIndex(build: Option[URI]): ProjectIndex  =  getOr(data, build, emptyProjectIndex)
	def builds: Set[URI] = keySet(data)
}
private final class KeyIndex0(val data: BuildIndex) extends ExtendableKeyIndex
{
	def buildURIs: Set[URI] = data.builds
	def projects(uri: URI): Set[String] = data.projectIndex(Some(uri)).projects
	def exists(proj: Option[ResolvedReference]): Boolean =
	{
		val (build, project) = parts(proj)
		data.data.get(build).flatMap(_.data.get(project)).isDefined
	}
	def configs(project: Option[ResolvedReference]): Set[String] = confIndex(project).configs
	def tasks(proj: Option[ResolvedReference], conf: Option[String]): Set[AttributeKey[_]] = keyIndex(proj, conf).tasks
	def tasks(proj: Option[ResolvedReference], conf: Option[String], key: String): Set[AttributeKey[_]] = keyIndex(proj, conf).tasks(key)
	def keys(proj: Option[ResolvedReference]): Set[String] = (Set.empty[String] /: optConfigs(proj)) { (s,c) => s ++ keys(proj, c) }
	def keys(proj: Option[ResolvedReference], conf: Option[String]): Set[String] = keyIndex(proj, conf).allKeys
	def keys(proj: Option[ResolvedReference], conf: Option[String], task: Option[AttributeKey[_]]): Set[String] = keyIndex(proj, conf).keys(task)

	def keyIndex(proj: Option[ResolvedReference], conf: Option[String]): AKeyIndex =
		confIndex(proj).keyIndex(conf)
	def confIndex(proj: Option[ResolvedReference]): ConfigIndex =
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
	private[this] def optConfigs(project: Option[ResolvedReference]): Seq[Option[String]] = None +: (configs(project).toSeq map some.fn)

	def addAggregated(scoped: ScopedKey[_], extra: BuildUtil[_]): ExtendableKeyIndex =
		if(validID(scoped.key.label))
		{
			val aggregateProjects = Aggregation.aggregate(scoped, ScopeMask(), extra, reverse = true)
			((this: ExtendableKeyIndex) /: aggregateProjects)(_ add _)
		}
		else
			this

	def add(scoped: ScopedKey[_]): ExtendableKeyIndex =
		if(validID(scoped.key.label)) add0(scoped) else this
	private[this] def add0(scoped: ScopedKey[_]): ExtendableKeyIndex =
	{
		val (build, project) = parts(scoped.scope.project.toOption)
		add1(build, project, scoped.scope.config, scoped.scope.task, scoped.key)
	}
	private[this] def add1(uri: Option[URI], id: Option[String], config: ScopeAxis[ConfigKey], task: ScopeAxis[AttributeKey[_]], key: AttributeKey[_]): ExtendableKeyIndex =
		new KeyIndex0( data.add(uri, id, config.toOption.map(_.name), task.toOption, key) )
}
