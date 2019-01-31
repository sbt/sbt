package coursier.lmcoursier

import coursier.cache.CacheUrl
import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import sbt.librarymanagement.{InclExclRule, ModuleID}
import sbt.util.Logger

import scala.collection.mutable
import scala.util.Try

object Inputs {

  def configExtends(configurations: Seq[sbt.librarymanagement.Configuration]): Map[Configuration, Seq[Configuration]] =
    configurations
      .map(cfg => Configuration(cfg.name) -> cfg.extendsConfigs.map(c => Configuration(c.name)))
      .toMap

  def coursierConfigurations(
    configurations: Seq[sbt.librarymanagement.Configuration],
    shadedConfig: Option[(String, Configuration)] = None
  ): Map[Configuration, Set[Configuration]] = {

    val configs0 = Inputs.configExtends(configurations)

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

    val map = configs0.map {
      case (config, _) =>
        config -> allExtends(config)
    }

    map ++ shadedConfig.toSeq.flatMap {
      case (baseConfig, shadedConfig) =>
        val baseConfig0 = Configuration(baseConfig)
        Seq(
          baseConfig0 -> (map.getOrElse(baseConfig0, Set(baseConfig0)) + shadedConfig),
          shadedConfig -> map.getOrElse(shadedConfig, Set(shadedConfig))
        )
    }
  }

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

  def exclusions(
    excludeDeps: Seq[InclExclRule],
    sv: String,
    sbv: String,
    log: Logger
  ): Set[(Organization, ModuleName)] = {

    var anyNonSupportedExclusionRule = false

    val res = excludeDeps
      .flatMap { rule =>
        if (rule.artifact != "*" || rule.configurations.nonEmpty) {
          log.warn(s"Unsupported exclusion rule $rule")
          anyNonSupportedExclusionRule = true
          Nil
        } else
          Seq(
            (Organization(rule.organization), ModuleName(FromSbt.sbtCrossVersionName(rule.name, rule.crossVersion, sv, sbv)))
          )
      }
      .toSet

    if (anyNonSupportedExclusionRule)
      log.warn("Only supported exclusion rule fields: organization, name")

    res
  }

  def coursierProject(
    projId: ModuleID,
    dependencies: Seq[ModuleID],
    excludeDeps: Seq[InclExclRule],
    configurations: Seq[sbt.librarymanagement.Configuration],
    sv: String,
    sbv: String,
    log: Logger
  ): Project = {

    val exclusions0 = exclusions(excludeDeps, sv, sbv, log)

    val configMap = configExtends(configurations)

    val proj = FromSbt.project(
      projId,
      dependencies,
      configMap,
      sv,
      sbv
    )

    proj.copy(
      dependencies = proj.dependencies.map {
        case (config, dep) =>
          (config, dep.copy(exclusions = dep.exclusions ++ exclusions0))
      }
    )
  }

  def withAuthenticationByHost(repo: Repository, credentials: Map[String, Authentication]): Repository = {

    def httpHost(s: String) =
      if (s.startsWith("http://") || s.startsWith("https://"))
        Try(CacheUrl.url(s).getHost).toOption
      else
        None

    repo match {
      case m: MavenRepository =>
        if (m.authentication.isEmpty)
          httpHost(m.root).flatMap(credentials.get).fold(m) { auth =>
            m.copy(authentication = Some(auth))
          }
        else
          m
      case i: IvyRepository =>
        if (i.authentication.isEmpty) {
          val base = i.pattern.chunks.takeWhile {
            case _: coursier.ivy.Pattern.Chunk.Const => true
            case _ => false
          }.map(_.string).mkString

          httpHost(base).flatMap(credentials.get).fold(i) { auth =>
            i.copy(authentication = Some(auth))
          }
        } else
          i
      case _ =>
        repo
    }
  }

}
