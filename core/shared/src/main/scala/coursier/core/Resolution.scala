package coursier.core

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern.quote

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scalaz.{ \/-, -\/ }

object Resolution {

  type ModuleVersion = (Module, String)

  def profileIsActive(
    profile: Profile,
    properties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[Version],
    userActivations: Option[Map[String, Boolean]]
  ): Boolean = {

    val fromUserOrDefault = userActivations match {
      case Some(activations) =>
        activations.get(profile.id)
      case None =>
        if (profile.activeByDefault.toSeq.contains(true))
          Some(true)
        else
          None
    }

    def fromActivation = profile.activation.isActive(properties, osInfo, jdkVersion)

    fromUserOrDefault.getOrElse(fromActivation)
  }

  /**
   * Get the active profiles of `project`, using the current properties `properties`,
   * and `profileActivations` stating if a profile is active.
   */
  def profiles(
    project: Project,
    properties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[Version],
    userActivations: Option[Map[String, Boolean]]
  ): Seq[Profile] =
    project.profiles.filter { profile =>
      profileIsActive(
        profile,
        properties,
        osInfo,
        jdkVersion,
        userActivations
      )
    }

  object DepMgmt {
    type Key = (String, String, String)

    def key(dep: Dependency): Key =
      (dep.module.organization, dep.module.name, if (dep.attributes.`type`.isEmpty) "jar" else dep.attributes.`type`)

    def add(
      dict: Map[Key, (String, Dependency)],
      item: (String, Dependency)
    ): Map[Key, (String, Dependency)] = {

      val key0 = key(item._2)

      if (dict.contains(key0))
        dict
      else
        dict + (key0 -> item)
    }

    def addSeq(
      dict: Map[Key, (String, Dependency)],
      deps: Seq[(String, Dependency)]
    ): Map[Key, (String, Dependency)] =
      (dict /: deps)(add)
  }

  def addDependencies(deps: Seq[Seq[(String, Dependency)]]): Seq[(String, Dependency)] = {
    val res =
      (deps :\ (Set.empty[DepMgmt.Key], Seq.empty[(String, Dependency)])) {
        case (deps0, (set, acc)) =>
          val deps = deps0
            .filter{case (_, dep) => !set(DepMgmt.key(dep))}

          (set ++ deps.map{case (_, dep) => DepMgmt.key(dep)}, acc ++ deps)
      }

    res._2
  }

  val propRegex = (
    quote("${") + "([^" + quote("{}") + "]*)" + quote("}")
  ).r

  def substituteProps(s: String, properties: Map[String, String]) = {
    val matches = propRegex
      .findAllMatchIn(s)
      .toVector
      .reverse

    if (matches.isEmpty) s
    else {
      val output =
        (new StringBuilder(s) /: matches) { (b, m) =>
          properties
            .get(m.group(1))
            .fold(b)(b.replace(m.start, m.end, _))
        }

      output.result()
    }
  }

  def propertiesMap(props: Seq[(String, String)]): Map[String, String] =
    props.foldLeft(Map.empty[String, String]) {
      case (acc, (k, v0)) =>
        val v = substituteProps(v0, acc)
        acc + (k -> v)
    }

  /**
   * Substitutes `properties` in `dependencies`.
   */
  def withProperties(
    dependencies: Seq[(String, Dependency)],
    properties: Map[String, String]
  ): Seq[(String, Dependency)] = {

    def substituteProps0(s: String) =
      substituteProps(s, properties)

    dependencies
      .map {case (config, dep) =>
        substituteProps0(config) -> dep.copy(
          module = dep.module.copy(
            organization = substituteProps0(dep.module.organization),
            name = substituteProps0(dep.module.name)
          ),
          version = substituteProps0(dep.version),
          attributes = dep.attributes.copy(
            `type` = substituteProps0(dep.attributes.`type`),
            classifier = substituteProps0(dep.attributes.classifier)
          ),
          configuration = substituteProps0(dep.configuration),
          exclusions = dep.exclusions
            .map{case (org, name) =>
              (substituteProps0(org), substituteProps0(name))
            }
          // FIXME The content of the optional tag may also be a property in
          // the original POM. Maybe not parse it that earlier?
        )
      }
  }

  /**
   * Merge several version constraints together.
   *
   * Returns `None` in case of conflict.
   */
  def mergeVersions(versions: Seq[String]): Option[String] = {

    val parseResults = versions.map(v => v -> Parse.versionConstraint(v))

    val nonParsedConstraints = parseResults.collect {
      case (repr, None) => repr
    }

    // FIXME Report this in return type, not this way
    if (nonParsedConstraints.nonEmpty)
      Console.err.println(
        s"Ignoring unparsed versions: $nonParsedConstraints"
      )

    val parsedConstraints = parseResults.collect {
      case (_, Some(c)) => c
    }

    VersionConstraint
      .merge(parsedConstraints: _*)
      .flatMap(_.repr)
  }

  /**
   * Merge several dependencies, solving version constraints of duplicated
   * modules.
   *
   * Returns the conflicted dependencies, and the merged others.
   */
  def merge(
    dependencies: TraversableOnce[Dependency],
    forceVersions: Map[Module, String]
  ): (Seq[Dependency], Seq[Dependency], Map[Module, String]) = {

    val mergedByModVer = dependencies
      .toVector
      .groupBy(dep => dep.module)
      .map { case (module, deps) =>
        module -> {
          val (versionOpt, updatedDeps) = forceVersions.get(module) match {
            case None =>
              if (deps.lengthCompare(1) == 0) (Some(deps.head.version), \/-(deps))
              else {
                val versions = deps
                  .map(_.version)
                  .distinct
                val versionOpt = mergeVersions(versions)

                (versionOpt, versionOpt match {
                  case Some(version) =>
                    \/-(deps.map(dep => dep.copy(version = version)))
                  case None =>
                    -\/(deps)
                })
              }

            case Some(forcedVersion) =>
              (Some(forcedVersion), \/-(deps.map(dep => dep.copy(version = forcedVersion))))
          }

          (updatedDeps, versionOpt)
        }
      }

    val merged = mergedByModVer
      .values
      .toVector

    (
      merged
        .collect { case (-\/(dep), _) => dep }
        .flatten,
      merged
        .collect { case (\/-(dep), _) => dep }
        .flatten,
      mergedByModVer
        .collect { case (mod, (_, Some(ver))) => mod -> ver }
    )
  }

  /**
   * Applies `dependencyManagement` to `dependencies`.
   *
   * Fill empty version / scope / exclusions, for dependencies found in
   * `dependencyManagement`.
   */
  def depsWithDependencyManagement(
    dependencies: Seq[(String, Dependency)],
    dependencyManagement: Seq[(String, Dependency)]
  ): Seq[(String, Dependency)] = {

    // See http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Management

    lazy val dict = DepMgmt.addSeq(Map.empty, dependencyManagement)

    dependencies
      .map {case (config0, dep0) =>
        var config = config0
        var dep = dep0

        for ((mgmtConfig, mgmtDep) <- dict.get(DepMgmt.key(dep0))) {

          if (mgmtDep.version.nonEmpty)
            dep = dep.copy(version = mgmtDep.version)

          if (config.isEmpty)
            config = mgmtConfig

          // FIXME The version and scope/config from dependency management, if any, are substituted
          // no matter what. The same is not done for the exclusions and optionality, for a lack of
          // way of distinguishing empty exclusions from no exclusion section and optional set to
          // false from no optional section in the dependency management for now.

          if (dep.exclusions.isEmpty)
            dep = dep.copy(exclusions = mgmtDep.exclusions)

          if (mgmtDep.optional)
            dep = dep.copy(optional = mgmtDep.optional)
        }

        (config, dep)
      }
  }


  val defaultConfiguration = "compile"

  def withDefaultConfig(dep: Dependency): Dependency =
    if (dep.configuration.isEmpty)
      dep.copy(configuration = defaultConfiguration)
    else
      dep

  /**
   * Filters `dependencies` with `exclusions`.
   */
  def withExclusions(
    dependencies: Seq[(String, Dependency)],
    exclusions: Set[(String, String)]
  ): Seq[(String, Dependency)] = {

    val filter = Exclusions(exclusions)

    dependencies
      .filter{case (_, dep) => filter(dep.module.organization, dep.module.name) }
      .map{case (config, dep) =>
        config -> dep.copy(
          exclusions = Exclusions.minimize(dep.exclusions ++ exclusions)
        )
      }
  }

  def withParentConfigurations(config: String, configurations: Map[String, Seq[String]]): (String, Set[String]) = {
    @tailrec
    def helper(configs: Set[String], acc: Set[String]): Set[String] =
      if (configs.isEmpty)
        acc
      else if (configs.exists(acc))
        helper(configs -- acc, acc)
      else if (configs.exists(!configurations.contains(_))) {
        val (remaining, notFound) = configs.partition(configurations.contains)
        helper(remaining, acc ++ notFound)
      } else {
        val extraConfigs = configs.flatMap(configurations)
        helper(extraConfigs, acc ++ configs)
      }

    val config0 = Parse.withFallbackConfig(config) match {
      case Some((main, fallback)) =>
        if (configurations.contains(main))
          main
        else if (configurations.contains(fallback))
          fallback
        else
          main
      case None => config
    }

    (config0, helper(Set(config0), Set.empty))
  }

  private val mavenScopes = {
    val base = Map[String, Set[String]](
      "compile" -> Set("compile"),
      "optional" -> Set("compile", "optional", "runtime"),
      "provided" -> Set(),
      "runtime" -> Set("compile", "runtime"),
      "test" -> Set()
    )

    base ++ Seq(
      "default" -> base("runtime")
    )
  }

  def projectProperties(project: Project): Seq[(String, String)] = {

    // vague attempt at recovering the POM packaging tag
    def packagingOpt = project.publications.collectFirst {
      case ("compile", pub) =>
        pub.`type`
    }

    // FIXME The extra properties should only be added for Maven projects, not Ivy ones
    val properties0 = Seq(
      // some artifacts seem to require these (e.g. org.jmock:jmock-legacy:2.5.1)
      // although I can find no mention of them in any manual / spec
      "pom.groupId"         -> project.module.organization,
      "pom.artifactId"      -> project.module.name,
      "pom.version"         -> project.version,
      // Required by some dependencies too (org.apache.directory.shared:shared-ldap:0.9.19 in particular)
      "groupId"             -> project.module.organization,
      "artifactId"          -> project.module.name,
      "version"             -> project.version
    ) ++ project.properties ++ Seq(
      "project.groupId"     -> project.module.organization,
      "project.artifactId"  -> project.module.name,
      "project.version"     -> project.version
    ) ++ packagingOpt.toSeq.map { packaging =>
      "project.packaging"   -> packaging
    } ++ project.parent.toSeq.flatMap {
      case (parModule, parVersion) =>
        Seq(
          "project.parent.groupId"     -> parModule.organization,
          "project.parent.artifactId"  -> parModule.name,
          "project.parent.version"     -> parVersion
        )
    }

    // loose attempt at substituting properties in each others in properties0
    // doesn't try to go recursive for now, but that could be made so if necessary

    val done = properties0
      .collect {
        case kv @ (_, value) if propRegex.findFirstIn(value).isEmpty =>
          kv
      }
      .toMap

    properties0.map {
      case (k, v) =>
        k -> substituteProps(v, done)
    }
  }

  /**
   * Get the dependencies of `project`, knowing that it came from dependency
   * `from` (that is, `from.module == project.module`).
   *
   * Substitute properties, update scopes, apply exclusions, and get extra
   * parameters from dependency management along the way.
   */
  def finalDependencies(
    from: Dependency,
    project: Project
  ): Seq[Dependency] = {

    // section numbers in the comments refer to withDependencyManagement

    val properties = propertiesMap(projectProperties(project))

    val (actualConfig, configurations) = withParentConfigurations(from.configuration, project.configurations)

    // Vague attempt at making the Maven scope model fit into the Ivy configuration one

    val config = if (actualConfig.isEmpty) defaultConfiguration else actualConfig
    val keepOpt = mavenScopes.get(config)

    withExclusions(
      // 2.1 & 2.2
      depsWithDependencyManagement(
        // 1.7
        withProperties(project.dependencies, properties),
        withProperties(project.dependencyManagement, properties)
      ),
      from.exclusions
    )
    .flatMap {
      case (config0, dep0) =>
        // Dependencies from Maven verify
        //   dep.configuration.isEmpty
        // and expect dep.configuration to be filled here

        val dep =
          if (from.optional)
            dep0.copy(optional = true)
          else
            dep0

        val config = if (config0.isEmpty) defaultConfiguration else config0

        def default =
          if (configurations(config))
            Seq(dep)
          else
            Nil

        if (dep.configuration.nonEmpty)
          default
        else
          keepOpt.fold(default) { keep =>
            if (keep(config)) {
              val depConfig =
                if (actualConfig == "optional")
                  defaultConfiguration
                else
                  // really keeping the  from.configuration, with its fallback config part
                  from.configuration

              Seq(dep.copy(configuration = depConfig))
            } else
              Nil
          }
    }
  }

  /**
   * Default function checking whether a profile is active, given
   * its id, activation conditions, and the properties of its project.
   */
  def defaultProfileActivation(
    id: String,
    activation: Activation,
    props: Map[String, String]
  ): Boolean =
    activation.properties.nonEmpty &&
      activation.properties.forall {
        case (name, valueOpt) =>
          if (name.startsWith("!")) {
            props.get(name.drop(1)).isEmpty
          } else {
            props.get(name).exists { v =>
              valueOpt.forall { reqValue =>
                if (reqValue.startsWith("!"))
                  v != reqValue.drop(1)
                else
                  v == reqValue
              }
            }
          }
      }

  def userProfileActivation(userProfiles: Set[String])(
    id: String,
    activation: Activation,
    props: Map[String, String]
  ): Boolean =
    userProfiles(id) ||
      defaultProfileActivation(id, activation, props)

  /**
   * Default dependency filter used during resolution.
   *
   * Does not follow optional dependencies.
   */
  def defaultFilter(dep: Dependency): Boolean =
    !dep.optional

}


/**
 * State of a dependency resolution.
 *
 * Done if method `isDone` returns `true`.
 *
 * @param dependencies: current set of dependencies
 * @param conflicts: conflicting dependencies
 * @param projectCache: cache of known projects
 * @param errorCache: keeps track of the modules whose project definition could not be found
 */
final case class Resolution(
  rootDependencies: Set[Dependency],
  dependencies: Set[Dependency],
  forceVersions: Map[Module, String],
  conflicts: Set[Dependency],
  projectCache: Map[Resolution.ModuleVersion, (Artifact.Source, Project)],
  errorCache: Map[Resolution.ModuleVersion, Seq[String]],
  finalDependenciesCache: Map[Dependency, Seq[Dependency]],
  filter: Option[Dependency => Boolean],
  osInfo: Activation.Os,
  jdkVersion: Option[Version],
  userActivations: Option[Map[String, Boolean]],
  mapDependencies: Option[Dependency => Dependency]
) {

  def copyWithCache(
    rootDependencies: Set[Dependency] = rootDependencies,
    dependencies: Set[Dependency] = dependencies,
    forceVersions: Map[Module, String] = forceVersions,
    conflicts: Set[Dependency] = conflicts,
    projectCache: Map[Resolution.ModuleVersion, (Artifact.Source, Project)] = projectCache,
    errorCache: Map[Resolution.ModuleVersion, Seq[String]] = errorCache,
    filter: Option[Dependency => Boolean] = filter,
    osInfo: Activation.Os = osInfo,
    jdkVersion: Option[Version] = jdkVersion,
    userActivations: Option[Map[String, Boolean]] = userActivations
    // don't allow changing mapDependencies here - that would invalidate finalDependenciesCache
  ): Resolution =
    copy(
      rootDependencies,
      dependencies,
      forceVersions,
      conflicts,
      projectCache,
      errorCache,
      finalDependenciesCache ++ finalDependenciesCache0.asScala,
      filter,
      osInfo,
      jdkVersion,
      userActivations
    )

  import Resolution._

  private[core] val finalDependenciesCache0 = new ConcurrentHashMap[Dependency, Seq[Dependency]]

  private def finalDependencies0(dep: Dependency): Seq[Dependency] =
    if (dep.transitive) {
      val deps = finalDependenciesCache.getOrElse(dep, finalDependenciesCache0.get(dep))

      if (deps == null)
        projectCache.get(dep.moduleVersion) match {
          case Some((_, proj)) =>
            val res0 = finalDependencies(dep, proj).filter(filter getOrElse defaultFilter)
            val res = mapDependencies.fold(res0)(res0.map(_))
            finalDependenciesCache0.put(dep, res)
            res
          case None => Nil
        }
      else
        deps
    } else
      Nil

  def dependenciesOf(dep: Dependency, withReconciledVersions: Boolean = true): Seq[Dependency] =
    if (withReconciledVersions)
      finalDependencies0(dep).map { trDep =>
        trDep.copy(
          version = reconciledVersions.getOrElse(trDep.module, trDep.version)
        )
      }
    else
      finalDependencies0(dep)

  /**
   * Transitive dependencies of the current dependencies, according to
   * what there currently is in cache.
   *
   * No attempt is made to solve version conflicts here.
   */
  lazy val transitiveDependencies: Seq[Dependency] =
    (dependencies -- conflicts)
      .toVector
      .flatMap(finalDependencies0)

  /**
   * The "next" dependency set, made of the current dependencies and their
   * transitive dependencies, trying to solve version conflicts.
   * Transitive dependencies are calculated with the current cache.
   *
   * May contain dependencies added in previous iterations, but no more
   * required. These are filtered below, see `newDependencies`.
   *
   * Returns a tuple made of the conflicting dependencies, all
   * the dependencies, and the retained version of each module.
   */
  lazy val nextDependenciesAndConflicts: (Seq[Dependency], Seq[Dependency], Map[Module, String]) =
    // TODO Provide the modules whose version was forced by dependency overrides too
    merge(
      rootDependencies.map(withDefaultConfig) ++ dependencies ++ transitiveDependencies,
      forceVersions
    )

  def reconciledVersions: Map[Module, String] =
    nextDependenciesAndConflicts._3

  /**
   * The modules we miss some info about.
   */
  lazy val missingFromCache: Set[ModuleVersion] = {
    val modules = dependencies
      .map(_.moduleVersion)
    val nextModules = nextDependenciesAndConflicts._2
      .map(_.moduleVersion)

    (modules ++ nextModules)
      .filterNot(mod => projectCache.contains(mod) || errorCache.contains(mod))
  }


  /**
   * Whether the resolution is done.
   */
  lazy val isDone: Boolean = {
    def isFixPoint = {
      val (nextConflicts, _, _) = nextDependenciesAndConflicts

      dependencies == (newDependencies ++ nextConflicts) &&
        conflicts == nextConflicts.toSet
    }

    missingFromCache.isEmpty && isFixPoint
  }

  private def eraseVersion(dep: Dependency) =
    dep.copy(version = "")

  /**
   * Returns a map giving the dependencies that brought each of
   * the dependency of the "next" dependency set.
   *
   * The versions of all the dependencies returned are erased (emptied).
   */
  lazy val reverseDependencies: Map[Dependency, Vector[Dependency]] = {
    val (updatedConflicts, updatedDeps, _) = nextDependenciesAndConflicts

    val trDepsSeq =
      for {
        dep <- updatedDeps
        trDep <- finalDependencies0(dep)
      } yield eraseVersion(trDep) -> eraseVersion(dep)

    val knownDeps = (updatedDeps ++ updatedConflicts)
      .map(eraseVersion)
      .toSet

    trDepsSeq
      .groupBy(_._1)
      .mapValues(_.map(_._2).toVector)
      .filterKeys(knownDeps)
      .toVector.toMap // Eagerly evaluate filterKeys/mapValues
  }

  /**
   * Returns dependencies from the "next" dependency set, filtering out
   * those that are no more required.
   *
   * The versions of all the dependencies returned are erased (emptied).
   */
  lazy val remainingDependencies: Set[Dependency] = {
    val rootDependencies0 = rootDependencies
      .map(withDefaultConfig)
      .map(eraseVersion)

    @tailrec
    def helper(
      reverseDeps: Map[Dependency, Vector[Dependency]]
    ): Map[Dependency, Vector[Dependency]] = {

      val (toRemove, remaining) = reverseDeps
        .partition(kv => kv._2.isEmpty && !rootDependencies0(kv._1))

      if (toRemove.isEmpty)
        reverseDeps
      else
        helper(
          remaining
            .mapValues(broughtBy =>
              broughtBy
                .filter(x => remaining.contains(x) || rootDependencies0(x))
            )
            .toVector
            .toMap
        )
    }

    val filteredReverseDependencies = helper(reverseDependencies)

    rootDependencies0 ++ filteredReverseDependencies.keys
  }

  /**
   * The final next dependency set, stripped of no more required ones.
   */
  lazy val newDependencies: Set[Dependency] = {
    val remainingDependencies0 = remainingDependencies

    nextDependenciesAndConflicts._2
      .filter(dep => remainingDependencies0(eraseVersion(dep)))
      .toSet
  }

  private lazy val nextNoMissingUnsafe: Resolution = {
    val (newConflicts, _, _) = nextDependenciesAndConflicts

    copyWithCache(
      dependencies = newDependencies ++ newConflicts,
      conflicts = newConflicts.toSet
    )
  }

  /**
   * If no module info is missing, the next state of the resolution,
   * which can be immediately calculated. Else, the current resolution.
   */
  @tailrec
  final def nextIfNoMissing: Resolution = {
    val missing = missingFromCache

    if (missing.isEmpty) {
      val next0 = nextNoMissingUnsafe

      if (next0 == this)
        this
      else
        next0.nextIfNoMissing
    } else
      this
  }

  /**
   * Required modules for the dependency management of `project`.
   */
  def dependencyManagementRequirements(
    project: Project
  ): Set[ModuleVersion] = {

    val needsParent =
      project.parent.exists { par =>
        val parentFound = projectCache.contains(par) || errorCache.contains(par)
        !parentFound
      }

    if (needsParent)
      project.parent.toSet
    else {

      val approxProperties0 =
        project.parent
          .flatMap(projectCache.get)
          .map(_._2.properties)
          .fold(project.properties)(project.properties ++ _)

      val approxProperties = propertiesMap(approxProperties0) ++ projectProperties(project)

      val profileDependencies =
        profiles(
          project,
          approxProperties,
          osInfo,
          jdkVersion,
          userActivations
        ).flatMap(p => p.dependencies ++ p.dependencyManagement)

      val modules = withProperties(
        project.dependencies ++ project.dependencyManagement ++ profileDependencies,
        approxProperties
      ).collect {
        case ("import", dep) => dep.moduleVersion
      }

      modules.toSet
    }
  }

  /**
   * Missing modules in cache, to get the full list of dependencies of
   * `project`, taking dependency management / inheritance into account.
   *
   * Note that adding the missing modules to the cache may unveil other
   * missing modules, so these modules should be added to the cache, and
   * `dependencyManagementMissing` checked again for new missing modules.
   */
  def dependencyManagementMissing(project: Project): Set[ModuleVersion] = {

    @tailrec
    def helper(
      toCheck: Set[ModuleVersion],
      done: Set[ModuleVersion],
      missing: Set[ModuleVersion]
    ): Set[ModuleVersion] = {

      if (toCheck.isEmpty)
        missing
      else if (toCheck.exists(done))
        helper(toCheck -- done, done, missing)
      else if (toCheck.exists(missing))
        helper(toCheck -- missing, done, missing)
      else if (toCheck.exists(projectCache.contains)) {
        val (checking, remaining) = toCheck.partition(projectCache.contains)
        val directRequirements = checking
          .flatMap(mod => dependencyManagementRequirements(projectCache(mod)._2))

        helper(remaining ++ directRequirements, done ++ checking, missing)
      } else if (toCheck.exists(errorCache.contains)) {
        val (errored, remaining) = toCheck.partition(errorCache.contains)
        helper(remaining, done ++ errored, missing)
      } else
        helper(Set.empty, done, missing ++ toCheck)
    }

    helper(
      dependencyManagementRequirements(project),
      Set(project.moduleVersion),
      Set.empty
    )
  }

  /**
   * Add dependency management / inheritance related items to `project`,
   * from what's available in cache.
   *
   * It is recommended to have fetched what `dependencyManagementMissing`
   * returned prior to calling this.
   */
  def withDependencyManagement(project: Project): Project = {

    /*

       Loosely following what [Maven says](http://maven.apache.org/components/ref/3.3.9/maven-model-builder/):
       (thanks to @MasseGuillaume for pointing that doc out)

    phase 1
         1.1 profile activation: see available activators. Notice that model interpolation hasn't happened yet, then interpolation for file-based activation is limited to ${basedir} (since Maven 3), System properties and request properties
         1.2 raw model validation: ModelValidator (javadoc), with its DefaultModelValidator implementation (source)
         1.3 model normalization - merge duplicates: ModelNormalizer (javadoc), with its DefaultModelNormalizer implementation (source)
         1.4 profile injection: ProfileInjector (javadoc), with its DefaultProfileInjector implementation (source)
         1.5 parent resolution until super-pom
         1.6 inheritance assembly: InheritanceAssembler (javadoc), with its DefaultInheritanceAssembler implementation (source). Notice that project.url, project.scm.connection, project.scm.developerConnection, project.scm.url and project.distributionManagement.site.url have a special treatment: if not overridden in child, the default value is parent's one with child artifact id appended
         1.7 model interpolation (see below)
     N/A     url normalization: UrlNormalizer (javadoc), with its DefaultUrlNormalizer implementation (source)
    phase 2, with optional plugin processing
     N/A     model path translation: ModelPathTranslator (javadoc), with its DefaultModelPathTranslator implementation (source)
     N/A     plugin management injection: PluginManagementInjector (javadoc), with its DefaultPluginManagementInjector implementation (source)
     N/A     (optional) lifecycle bindings injection: LifecycleBindingsInjector (javadoc), with its DefaultLifecycleBindingsInjector implementation (source)
         2.1 dependency management import (for dependencies of type pom in the <dependencyManagement> section)
         2.2 dependency management injection: DependencyManagementInjector (javadoc), with its DefaultDependencyManagementInjector implementation (source)
         2.3 model normalization - inject default values: ModelNormalizer (javadoc), with its DefaultModelNormalizer implementation (source)
     N/A     (optional) reports configuration: ReportConfigurationExpander (javadoc), with its DefaultReportConfigurationExpander implementation (source)
     N/A     (optional) reports conversion to decoupled site plugin: ReportingConverter (javadoc), with its DefaultReportingConverter implementation (source)
     N/A     (optional) plugins configuration: PluginConfigurationExpander (javadoc), with its DefaultPluginConfigurationExpander implementation (source)
         2.4 effective model validation: ModelValidator (javadoc), with its DefaultModelValidator implementation (source)

    N/A: does not apply here (related to plugins, path of project being built, ...)

     */

    // A bit fragile, but seems to work

    val approxProperties0 =
      project.parent
        .filter(projectCache.contains)
        .map(projectCache(_)._2.properties)
        .fold(project.properties)(_ ++ project.properties)

    // 1.1 (see above)
    val approxProperties = propertiesMap(approxProperties0) ++ projectProperties(project)

    val profiles0 = profiles(
      project,
      approxProperties,
      osInfo,
      jdkVersion,
      userActivations
    )

    // 1.2 made from Pom.scala (TODO look at the very details?)

    // 1.3 & 1.4 (if only vaguely so)
    val properties0 =
      (project.properties /: profiles0) { (acc, p) =>
        acc ++ p.properties
      }

    val project0 = project.copy(
      properties = project.parent  // belongs to 1.5 & 1.6
        .flatMap(projectCache.get)
        .fold(properties0)(_._2.properties ++ properties0)
    )

    val propertiesMap0 = propertiesMap(projectProperties(project0))

    val dependencies0 = addDependencies(
      (project0.dependencies +: profiles0.map(_.dependencies)).map(withProperties(_, propertiesMap0))
    )
    val dependenciesMgmt0 = addDependencies(
      (project0.dependencyManagement +: profiles0.map(_.dependencyManagement)).map(withProperties(_, propertiesMap0))
    )

    val deps0 =
      dependencies0
        .collect { case ("import", dep) =>
          dep.moduleVersion
        } ++
      dependenciesMgmt0
        .collect { case ("import", dep) =>
          dep.moduleVersion
        } ++
      project0.parent // belongs to 1.5 & 1.6

    val deps = deps0.filter(projectCache.contains)

    val projs = deps
      .map(projectCache(_)._2)

    val depMgmt = (
      project0.dependencyManagement +: (
        profiles0.map(_.dependencyManagement) ++
        projs.map(_.dependencyManagement)
      )
    )
      .map(withProperties(_, propertiesMap0))
      .foldLeft(Map.empty[DepMgmt.Key, (String, Dependency)])(DepMgmt.addSeq)

    val depsSet = deps.toSet

    project0.copy(
      version = substituteProps(project0.version, propertiesMap0),
      dependencies =
        dependencies0
          .filterNot{case (config, dep) =>
            config == "import" && depsSet(dep.moduleVersion)
          } ++
        project0.parent  // belongs to 1.5 & 1.6
          .filter(projectCache.contains)
          .toSeq
          .flatMap(projectCache(_)._2.dependencies),
      dependencyManagement = depMgmt.values.toSeq
        .filterNot{case (config, dep) =>
          config == "import" && depsSet(dep.moduleVersion)
        }
    )
  }

  /**
    * Minimized dependency set. Returns `dependencies` with no redundancy.
    *
    * E.g. `dependencies` may contains several dependencies towards module org:name:version,
    * a first one excluding A and B, and a second one excluding A and C. In practice, B and C will
    * be brought anyway, because the first dependency doesn't exclude C, and the second one doesn't
    * exclude B. So having both dependencies is equivalent to having only one dependency towards
    * org:name:version, excluding just A.
    *
    * The same kind of substitution / filtering out can be applied with configurations. If
    * `dependencies` contains several dependencies towards org:name:version, a first one bringing
    * its configuration "runtime", a second one "compile", and the configuration mapping of
    * org:name:version says that "runtime" extends "compile", then all the dependencies brought
    * by the latter will be brought anyway by the former, so that the latter can be removed.
    *
    * @return A minimized `dependencies`, applying this kind of substitutions.
    */
  def minDependencies: Set[Dependency] =
    Orders.minDependencies(
      dependencies,
      dep =>
        projectCache
          .get(dep)
          .map(_._2.configurations)
          .getOrElse(Map.empty)
    )

  private def artifacts0(
    overrideClassifiers: Option[Seq[String]],
    keepAttributes: Boolean,
    optional: Boolean
  ): Seq[Artifact] =
    dependencyArtifacts0(overrideClassifiers, optional).map {
      case (_, artifact) =>
        if (keepAttributes) artifact else artifact.copy(attributes = Attributes("", ""))
    }.distinct

  // keepAttributes to false is a temporary hack :-|
  // if one wants the attributes field of artifacts not to be cleared, call dependencyArtifacts

  def classifiersArtifacts(classifiers: Seq[String]): Seq[Artifact] =
    artifacts0(Some(classifiers), keepAttributes = false, optional = true)

  def artifacts: Seq[Artifact] =
    artifacts0(None, keepAttributes = false, optional = false)

  def artifacts(withOptional: Boolean): Seq[Artifact] =
    artifacts0(None, keepAttributes = false, optional = withOptional)

  private def dependencyArtifacts0(
    overrideClassifiers: Option[Seq[String]],
    optional: Boolean
  ): Seq[(Dependency, Artifact)] =
    for {
      dep <- minDependencies.toSeq
      (source, proj) <- projectCache
        .get(dep.moduleVersion)
        .toSeq
      artifact <- source
        .artifacts(dep, proj, overrideClassifiers)
    } yield dep -> artifact

  def dependencyArtifacts: Seq[(Dependency, Artifact)] =
    dependencyArtifacts0(None, optional = false)

  def dependencyArtifacts(withOptional: Boolean): Seq[(Dependency, Artifact)] =
    dependencyArtifacts0(None, optional = withOptional)

  def dependencyClassifiersArtifacts(classifiers: Seq[String]): Seq[(Dependency, Artifact)] =
    dependencyArtifacts0(Some(classifiers), optional = true)

  /**
    * Returns errors on dependencies
    * @return errors
    */
  def metadataErrors: Seq[(ModuleVersion, Seq[String])] = errorCache.toSeq

  /**
    * Returns errors on dependencies, but that don't have POM-related errors
    * @return errors
    */
  @deprecated("use metadataErrors instead", "1.0.0-RC1")
  def errors: Seq[(Dependency, Seq[String])] =
    for {
      dep <- dependencies.toSeq
      err <- errorCache
        .get(dep.moduleVersion)
        .toSeq
    } yield (dep, err)

  /**
    * Removes from this `Resolution` dependencies that are not in `dependencies` neither brought
    * transitively by them.
    *
    * This keeps the versions calculated by this `Resolution`. The common dependencies of different
    * subsets will thus be guaranteed to have the same versions.
    *
    * @param dependencies: the dependencies to keep from this `Resolution`
    */
  def subset(dependencies: Set[Dependency]): Resolution = {

    def updateVersion(dep: Dependency): Dependency =
      dep.copy(version = reconciledVersions.getOrElse(dep.module, dep.version))

    @tailrec def helper(current: Set[Dependency]): Set[Dependency] = {
      val newDeps = current ++ current
        .flatMap(finalDependencies0)
        .map(updateVersion)

      val anyNewDep = (newDeps -- current).nonEmpty

      if (anyNewDep)
        helper(newDeps)
      else
        newDeps
    }

    copyWithCache(
      rootDependencies = dependencies,
      dependencies = helper(dependencies.map(updateVersion))
      // don't know if something should be done about conflicts
    )
  }
}
