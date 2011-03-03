/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.net.URI
	import Project.ScopedKey

object KeyIndex
{
	def empty: ExtendableKeyIndex = new KeyIndex0(Map.empty)
	def apply(known: Seq[ScopedKey[_]]): ExtendableKeyIndex =
		(empty /: known) { _ add _ }
	def combine(indices: Seq[KeyIndex]): KeyIndex = new KeyIndex {
		def buildURIs = concat(_.buildURIs)
		def projects(uri: URI) = concat(_.projects(uri))
		def configs(proj: ResolvedReference) = concat(_.configs(proj))
		def keys(proj: ResolvedReference, conf: Option[String]) = concat(_.keys(proj, conf))
		def concat[T](f: KeyIndex => Set[T]): Set[T] =
			(Set.empty[T] /: indices)( (s,k) => s ++ f(k) )
	}
}

trait KeyIndex
{
	def buildURIs: Set[URI]
	def projects(uri: URI): Set[String]
	def configs(proj: ResolvedReference): Set[String]
	def keys(proj: ResolvedReference, conf: Option[String]): Set[String]
}
trait ExtendableKeyIndex extends KeyIndex
{
	def add(scoped: ScopedKey[_]): ExtendableKeyIndex
}
private final class KeyIndex0(val data: Map[URI, Map[Option[String], Map[ Option[String], Set[String]] ]]) extends ExtendableKeyIndex
{
	def buildURIs: Set[URI] = data.keys.toSet
	def projects(uri: URI): Set[String] = get(data, uri).keys.flatten.toSet
	def configs(project: ResolvedReference): Set[String] = confMap(project).keys.flatten.toSet
	def keys(project: ResolvedReference, conf: Option[String]): Set[String] = get(confMap(project), conf)

	def confMap(proj: ResolvedReference): Map[Option[String], Set[String]] =
		proj match
		{
			case ProjectRef(uri, id) => get( get(data, uri), Some(id))
			case BuildRef(uri) => get( get(data, uri), None)
		}

	private[this] def get[A,B,C](m: Map[A,Map[B,C]], key: A): Map[B,C] = getOr(m, key, Map.empty)
	private[this] def get[A,B](m: Map[A,Set[B]], key: A): Set[B] = getOr(m, key, Set.empty)
	private[this] def getOr[A,B](m: Map[A,B], key: A, or: B): B = m.getOrElse(key, or)

	def add(scoped: ScopedKey[_]): ExtendableKeyIndex =
		scoped.scope match
		{
			case Scope(Select(ref: ResolvedReference), config, _, _) => addRef(ref, config, scoped.key)
			case _ => this
		}
	def addRef(ref: ResolvedReference, config: ScopeAxis[ConfigKey], key: AttributeKey[_]): ExtendableKeyIndex =
		ref match
		{
			case BuildRef(uri) => add(uri, None, config, key)
			case ProjectRef(uri, id) => add(uri, Some(id), config, key)
		}
	def add(uri: URI, id: Option[String], config: ScopeAxis[ConfigKey], key: AttributeKey[_]): ExtendableKeyIndex =
		add(uri, id, config match { case Select(c) => Some(c.name); case _ => None }, key)
	def add(uri: URI, id: Option[String], config: Option[String], key: AttributeKey[_]): ExtendableKeyIndex =
	{
		val projectMap = get(data, uri)
		val configMap = get(projectMap, id)
		val newSet = get(configMap, config) + key.label
		val newProjectMap = projectMap.updated(id, configMap.updated(config, newSet))
		new KeyIndex0( data.updated(uri, newProjectMap) )
	}
}
