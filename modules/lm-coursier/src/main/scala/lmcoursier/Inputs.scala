package lmcoursier

import coursier.ivy.IvyXml.{mappings => initialIvyXmlMappings}
import lmcoursier.definitions.{Configuration, Module, ModuleName, Organization, ToCoursier}
import sbt.librarymanagement.{CrossVersion, InclExclRule, ModuleID}
import sbt.util.Logger

import scala.collection.mutable

object Inputs {

  def ivyXmlMappings(mapping: String): Seq[(Configuration, Configuration)] =
    initialIvyXmlMappings(mapping).map {
      case (from, to) =>
        Configuration(from.value) -> Configuration(to.value)
    }

  def configExtendsSeq(configurations: Seq[sbt.librarymanagement.Configuration]): Seq[(Configuration, Seq[Configuration])] =
    configurations
      .map(cfg => Configuration(cfg.name) -> cfg.extendsConfigs.map(c => Configuration(c.name)))

  @deprecated("Now unused internally, to be removed in the future", "2.0.0-RC6-5")
  def configExtends(configurations: Seq[sbt.librarymanagement.Configuration]): Map[Configuration, Seq[Configuration]] =
    configurations
      .map(cfg => Configuration(cfg.name) -> cfg.extendsConfigs.map(c => Configuration(c.name)))
      .toMap

  @deprecated("Use coursierConfigurationsMap instead", "2.0.0-RC6-5")
  def coursierConfigurations(configurations: Seq[sbt.librarymanagement.Configuration], shadedConfigOpt: Option[String] = None): Map[Configuration, Set[Configuration]] =
    coursierConfigurationsMap(configurations)

  def coursierConfigurationsMap(configurations: Seq[sbt.librarymanagement.Configuration]): Map[Configuration, Set[Configuration]] = {

    val configs0 = configExtendsSeq(configurations).toMap

    def allExtends(c: Configuration) = {
      // possibly bad complexity
      def helper(current: Set[Configuration]): Set[Configuration] = {
        val newSet = current ++ current.flatMap(configs0.getOrElse(_, Nil))
        if ((newSet -- current).nonEmpty)
          helper(newSet)
        else
          newSet
      }

      helper(Set(c))
    }

    configs0.map {
      case (config, _) =>
        config -> allExtends(config)
    }
  }

  def orderedConfigurations(
    configurations: Seq[(Configuration, Seq[Configuration])]
  ): Seq[(Configuration, Seq[Configuration])] = {

    val map = configurations.toMap

    def helper(done: Set[Configuration], toAdd: List[Configuration]): Stream[(Configuration, Seq[Configuration])] =
      toAdd match {
        case Nil => Stream.empty
        case config :: rest =>
          val extends0 = map.getOrElse(config, Nil)
          val missingExtends = extends0.filterNot(done)
          if (missingExtends.isEmpty)
            (config, extends0) #:: helper(done + config, rest)
          else
            helper(done, missingExtends.toList ::: toAdd)
      }

    helper(Set.empty, configurations.map(_._1).toList)
      .toVector
  }

  @deprecated("Now unused internally, to be removed in the future", "2.0.0-RC6-5")
  def ivyGraphs(configurations: Map[Configuration, Seq[Configuration]]): Seq[Set[Configuration]] = {

    // probably bad complexity, but that shouldn't matter given the size of the graphs involved...

    final class Wrapper(val set: mutable.HashSet[Configuration]) {
      def ++=(other: Wrapper): this.type = {
        set ++= other.set
        this
      }
    }

    val sets =
      new mutable.HashMap[Configuration, Wrapper] ++= configurations.map {
        case (k, l) =>
          val s = new mutable.HashSet[Configuration]
          s ++= l
          s += k
          k -> new Wrapper(s)
      }

    for (k <- configurations.keys) {
      val s = sets(k)

      var foundNew = true
      while (foundNew) {
        foundNew = false
        for (other <- s.set.toVector) {
          val otherS = sets(other)
          if (!otherS.eq(s)) {
            s ++= otherS
            sets += other -> s
            foundNew = true
          }
        }
      }
    }

    sets.values.toVector.distinct.map(_.set.toSet)
  }

  def exclusionsSeq(
    excludeDeps: Seq[InclExclRule],
    sv: String,
    sbv: String,
    log: Logger
  ): Seq[(Organization, ModuleName)] = {

    var anyNonSupportedExclusionRule = false

    val res = excludeDeps
      .flatMap { rule =>
        if (rule.artifact != "*" || rule.configurations.nonEmpty) {
          log.warn(s"Unsupported exclusion rule $rule")
          anyNonSupportedExclusionRule = true
          Nil
        } else {
          val name = CrossVersion(rule.crossVersion, sv, sbv)
            .fold(rule.name)(_(rule.name))
          Seq((Organization(rule.organization), ModuleName(name)))
        }
      }

    if (anyNonSupportedExclusionRule)
      log.warn("Only supported exclusion rule fields: organization, name")

    res
  }

  def exclusions(
    excludeDeps: Seq[InclExclRule],
    sv: String,
    sbv: String,
    log: Logger
  ): Set[(Organization, ModuleName)] =
    exclusionsSeq(excludeDeps, sv, sbv, log).toSet

  def forceVersions(depOverrides: Seq[ModuleID], sv: String, sbv: String): Seq[(Module, String)] =
    depOverrides.map(FromSbt.moduleVersion(_, sv, sbv))

}
