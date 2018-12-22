/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.net.URI
import Def.ScopedKey
import sbt.internal.util.complete.DefaultParsers.validID
import sbt.internal.util.Types.some
import sbt.internal.util.{ AttributeKey, Relation }
import sbt.librarymanagement.Configuration

object KeyIndex {
  def empty: ExtendableKeyIndex = new KeyIndex0(emptyBuildIndex)
  def apply(
      known: Iterable[ScopedKey[_]],
      projects: Map[URI, Set[String]],
      configurations: Map[String, Seq[Configuration]]
  ): ExtendableKeyIndex =
    (base(projects, configurations) /: known) { _ add _ }
  def aggregate(
      known: Iterable[ScopedKey[_]],
      extra: BuildUtil[_],
      projects: Map[URI, Set[String]],
      configurations: Map[String, Seq[Configuration]]
  ): ExtendableKeyIndex =
    (base(projects, configurations) /: known) { (index, key) =>
      index.addAggregated(key, extra)
    }
  private[this] def base(
      projects: Map[URI, Set[String]],
      configurations: Map[String, Seq[Configuration]]
  ): ExtendableKeyIndex = {
    val data = for {
      (uri, ids) <- projects
    } yield {
      val data = ids map { id =>
        val configs = configurations.getOrElse(id, Seq())
        val namedConfigs = configs.map { config =>
          (config.name, ConfigData(Some(config.id), emptyAKeyIndex))
        }.toMap
        val inverse = configs.map(config => config.id -> config.name).toMap
        Option(id) -> new ConfigIndex(namedConfigs, inverse, emptyAKeyIndex)
      }
      Option(uri) -> new ProjectIndex(data.toMap)
    }
    new KeyIndex0(new BuildIndex(data))
  }

  def combine(indices: Seq[KeyIndex]): KeyIndex = new KeyIndex {
    def buildURIs = concat(_.buildURIs)
    def projects(uri: URI) = concat(_.projects(uri))
    def exists(project: Option[ResolvedReference]): Boolean = indices.exists(_ exists project)
    def configs(proj: Option[ResolvedReference]) = concat(_.configs(proj))
    private[sbt] def configIdents(proj: Option[ResolvedReference]) = concat(_.configIdents(proj))
    private[sbt] def fromConfigIdent(proj: Option[ResolvedReference])(configIdent: String): String =
      indices.find(idx => idx.exists(proj)) match {
        case Some(idx) => idx.fromConfigIdent(proj)(configIdent)
        case _         => Scope.unguessConfigIdent(configIdent)
      }
    def tasks(proj: Option[ResolvedReference], conf: Option[String]) = concat(_.tasks(proj, conf))
    def tasks(proj: Option[ResolvedReference], conf: Option[String], key: String) =
      concat(_.tasks(proj, conf, key))
    def keys(proj: Option[ResolvedReference]) = concat(_.keys(proj))
    def keys(proj: Option[ResolvedReference], conf: Option[String]) = concat(_.keys(proj, conf))
    def keys(proj: Option[ResolvedReference], conf: Option[String], task: Option[AttributeKey[_]]) =
      concat(_.keys(proj, conf, task))
    def concat[T](f: KeyIndex => Set[T]): Set[T] =
      (Set.empty[T] /: indices)((s, k) => s ++ f(k))
  }
  private[sbt] def getOr[A, B](m: Map[A, B], key: A, or: B): B = m.getOrElse(key, or)
  private[sbt] def keySet[A, B](m: Map[Option[A], B]): Set[A] = m.keys.flatten.toSet
  private[sbt] val emptyAKeyIndex = new AKeyIndex(Relation.empty)
  private[sbt] val emptyConfigIndex = new ConfigIndex(Map.empty, Map.empty, emptyAKeyIndex)
  private[sbt] val emptyProjectIndex = new ProjectIndex(Map.empty)
  private[sbt] val emptyBuildIndex = new BuildIndex(Map.empty)
}
import KeyIndex._

trait KeyIndex {
  // TODO, optimize
  def isEmpty(proj: Option[ResolvedReference], conf: Option[String]): Boolean =
    keys(proj, conf).isEmpty
  def isEmpty(
      proj: Option[ResolvedReference],
      conf: Option[String],
      task: Option[AttributeKey[_]]
  ): Boolean = keys(proj, conf, task).isEmpty

  def buildURIs: Set[URI]
  def projects(uri: URI): Set[String]
  def exists(project: Option[ResolvedReference]): Boolean
  def configs(proj: Option[ResolvedReference]): Set[String]
  def tasks(proj: Option[ResolvedReference], conf: Option[String]): Set[AttributeKey[_]]
  def tasks(
      proj: Option[ResolvedReference],
      conf: Option[String],
      key: String
  ): Set[AttributeKey[_]]
  def keys(proj: Option[ResolvedReference]): Set[String]
  def keys(proj: Option[ResolvedReference], conf: Option[String]): Set[String]
  def keys(
      proj: Option[ResolvedReference],
      conf: Option[String],
      task: Option[AttributeKey[_]]
  ): Set[String]
  private[sbt] def configIdents(project: Option[ResolvedReference]): Set[String]
  private[sbt] def fromConfigIdent(proj: Option[ResolvedReference])(configIdent: String): String
}
trait ExtendableKeyIndex extends KeyIndex {
  def add(scoped: ScopedKey[_]): ExtendableKeyIndex
  def addAggregated(scoped: ScopedKey[_], extra: BuildUtil[_]): ExtendableKeyIndex
}
// task axis <-> key
private[sbt] final class AKeyIndex(val data: Relation[Option[AttributeKey[_]], String]) {
  def add(task: Option[AttributeKey[_]], key: AttributeKey[_]): AKeyIndex =
    new AKeyIndex(data + (task, key.label))
  def keys(task: Option[AttributeKey[_]]): Set[String] = data.forward(task)
  def allKeys: Set[String] = data._2s.toSet
  def tasks: Set[AttributeKey[_]] = data._1s.flatten.toSet
  def tasks(key: String): Set[AttributeKey[_]] = data.reverse(key).flatten
}

private[sbt] case class IdentifiableConfig(name: String, ident: Option[String])

private[sbt] case class ConfigData(ident: Option[String], keys: AKeyIndex)

/*
 * data contains the mapping between a configuration name and its ident and keys.
 * inverse contains the mapping between a configuration ident and its name
 * noConfigKeys contains the keys without a configuration.
 */
private[sbt] final class ConfigIndex(
    val data: Map[String, ConfigData],
    val inverse: Map[String, String],
    val noConfigKeys: AKeyIndex
) {
  def add(
      config: Option[IdentifiableConfig],
      task: Option[AttributeKey[_]],
      key: AttributeKey[_]
  ): ConfigIndex = {
    config match {
      case Some(c) => addKeyWithConfig(c, task, key)
      case None    => addKeyWithoutConfig(task, key)
    }
  }

  def addKeyWithConfig(
      config: IdentifiableConfig,
      task: Option[AttributeKey[_]],
      key: AttributeKey[_]
  ): ConfigIndex = {
    val oldConfigData = data.getOrElse(config.name, ConfigData(None, emptyAKeyIndex))
    val newConfigData = ConfigData(
      ident = oldConfigData.ident.orElse(config.ident),
      keys = oldConfigData.keys.add(task, key)
    )
    val newData = data.updated(config.name, newConfigData)
    val newInverse = (inverse.updated _).tupled(ConfigIndex.invert(config.name, newConfigData))
    new ConfigIndex(newData, newInverse, noConfigKeys)
  }

  def addKeyWithoutConfig(task: Option[AttributeKey[_]], key: AttributeKey[_]): ConfigIndex = {
    new ConfigIndex(data, inverse, noConfigKeys.add(task, key))
  }

  def keyIndex(conf: Option[String]): AKeyIndex = conf match {
    case Some(c) => data.get(c).map(_.keys).getOrElse(emptyAKeyIndex)
    case None    => noConfigKeys
  }

  def configs: Set[String] = data.keySet

  // guess Configuration name from an identifier.
  // There's a guessing involved because we could have scoped key that Project is not aware of.
  private[sbt] def fromConfigIdent(ident: String): String =
    inverse.getOrElse(ident, Scope.unguessConfigIdent(ident))
}
private[sbt] object ConfigIndex {
  def invert(name: String, data: ConfigData): (String, String) = data match {
    case ConfigData(Some(ident), _) => ident -> name
    case ConfigData(None, _)        => Scope.guessConfigIdent(name) -> name
  }
}
private[sbt] final class ProjectIndex(val data: Map[Option[String], ConfigIndex]) {
  def add(
      id: Option[String],
      config: Option[IdentifiableConfig],
      task: Option[AttributeKey[_]],
      key: AttributeKey[_]
  ): ProjectIndex =
    new ProjectIndex(data updated (id, confIndex(id).add(config, task, key)))
  def confIndex(id: Option[String]): ConfigIndex = getOr(data, id, emptyConfigIndex)
  def projects: Set[String] = keySet(data)
}
private[sbt] final class BuildIndex(val data: Map[Option[URI], ProjectIndex]) {
  def add(
      build: Option[URI],
      project: Option[String],
      config: Option[IdentifiableConfig],
      task: Option[AttributeKey[_]],
      key: AttributeKey[_]
  ): BuildIndex =
    new BuildIndex(data updated (build, projectIndex(build).add(project, config, task, key)))
  def projectIndex(build: Option[URI]): ProjectIndex = getOr(data, build, emptyProjectIndex)
  def builds: Set[URI] = keySet(data)
}
private[sbt] final class KeyIndex0(val data: BuildIndex) extends ExtendableKeyIndex {
  def buildURIs: Set[URI] = data.builds
  def projects(uri: URI): Set[String] = data.projectIndex(Some(uri)).projects
  def exists(proj: Option[ResolvedReference]): Boolean = {
    val (build, project) = parts(proj)
    data.data.get(build).flatMap(_.data.get(project)).isDefined
  }
  def configs(project: Option[ResolvedReference]): Set[String] = confIndex(project).configs

  private[sbt] def configIdents(project: Option[ResolvedReference]): Set[String] =
    confIndex(project).configs

  private[sbt] def fromConfigIdent(proj: Option[ResolvedReference])(configIdent: String): String =
    confIndex(proj).fromConfigIdent(configIdent)

  def tasks(proj: Option[ResolvedReference], conf: Option[String]): Set[AttributeKey[_]] =
    keyIndex(proj, conf).tasks
  def tasks(
      proj: Option[ResolvedReference],
      conf: Option[String],
      key: String
  ): Set[AttributeKey[_]] = keyIndex(proj, conf).tasks(key)
  def keys(proj: Option[ResolvedReference]): Set[String] =
    (Set.empty[String] /: optConfigs(proj)) { (s, c) =>
      s ++ keys(proj, c)
    }
  def keys(proj: Option[ResolvedReference], conf: Option[String]): Set[String] =
    keyIndex(proj, conf).allKeys
  def keys(
      proj: Option[ResolvedReference],
      conf: Option[String],
      task: Option[AttributeKey[_]]
  ): Set[String] = keyIndex(proj, conf).keys(task)

  def keyIndex(proj: Option[ResolvedReference], conf: Option[String]): AKeyIndex =
    confIndex(proj).keyIndex(conf)
  def confIndex(proj: Option[ResolvedReference]): ConfigIndex = {
    val (build, project) = parts(proj)
    data.projectIndex(build).confIndex(project)
  }
  def parts(proj: Option[Reference]): (Option[URI], Option[String]) =
    proj match {
      case Some(ProjectRef(uri, id)) => (Some(uri), Some(id))
      case Some(BuildRef(uri))       => (Some(uri), None)
      case _                         => (None, None)
    }
  private[this] def optConfigs(project: Option[ResolvedReference]): Seq[Option[String]] =
    None +: (configs(project).toSeq map some.fn)

  def addAggregated(scoped: ScopedKey[_], extra: BuildUtil[_]): ExtendableKeyIndex =
    if (validID(scoped.key.label)) {
      val aggregateProjects = Aggregation.aggregate(scoped, ScopeMask(), extra, reverse = true)
      ((this: ExtendableKeyIndex) /: aggregateProjects)(_ add _)
    } else
      this

  def add(scoped: ScopedKey[_]): ExtendableKeyIndex =
    if (validID(scoped.key.label)) add0(scoped) else this
  private[this] def add0(scoped: ScopedKey[_]): ExtendableKeyIndex = {
    val (build, project) = parts(scoped.scope.project.toOption)
    add1(build, project, scoped.scope.config, scoped.scope.task, scoped.key)
  }
  private[this] def add1(
      uri: Option[URI],
      id: Option[String],
      config: ScopeAxis[ConfigKey],
      task: ScopeAxis[AttributeKey[_]],
      key: AttributeKey[_]
  ): ExtendableKeyIndex = {
    val keyConfig = config.toOption.map(c => IdentifiableConfig(c.name, None))
    new KeyIndex0(data.add(uri, id, keyConfig, task.toOption, key))
  }
}
