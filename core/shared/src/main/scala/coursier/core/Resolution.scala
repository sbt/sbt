package coursier.core

import java.util.regex.Pattern.quote

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.{ \/-, -\/ }

object Resolution {

  type ModuleVersion = (Module, String)

  /**
   * Get the active profiles of `project`, using the current properties `properties`,
   * and `profileActivation` stating if a profile is active.
   */
  def profiles(
    project: Project,
    properties: Map[String, String],
    profileActivation: (String, Activation, Map[String, String]) => Boolean
  ): Seq[Profile] = {

    val activated = project.profiles
      .filter(p => profileActivation(p.id, p.activation, properties))

    def default = project.profiles
      .filter(_.activeByDefault.toSeq.contains(true))

    if (activated.isEmpty) default
    else activated
  }

  object DepMgmt {
    type Key = (String, String, String)

    def key(dep: Dependency): Key =
      (dep.module.organization, dep.module.name, dep.attributes.`type`)

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
    quote("${") + "([a-zA-Z0-9-.]*)" + quote("}")
  ).r

  def substituteProps(s: String, properties: Map[String, String]) = {
    val matches = propRegex
      .findAllMatchIn(s)
      .toList
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
    val (nonParsedConstraints, parsedConstraints) =
      versions
        .map(v => v -> Parse.versionConstraint(v))
        .partition(_._2.isEmpty)

    // FIXME Report this in return type, not this way
    if (nonParsedConstraints.nonEmpty)
      Console.err.println(
        s"Ignoring unparsed versions: ${nonParsedConstraints.map(_._1)}"
      )

    val intervalOpt =
      (Option(VersionInterval.zero) /: parsedConstraints) {
        case (acc, (_, someCstr)) =>
          acc.flatMap(_.merge(someCstr.get.interval))
      }

    intervalOpt
      .map(_.constraint.repr)
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
      .toList
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
      .toList

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
          if (dep.version.isEmpty)
            dep = dep.copy(version = mgmtDep.version)
          if (config.isEmpty)
            config = mgmtConfig

          if (dep.exclusions.isEmpty)
            dep = dep.copy(exclusions = mgmtDep.exclusions)
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

  def withParentConfigurations(config: String, configurations: Map[String, Seq[String]]): Set[String] = {
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

    helper(Set(config0), Set.empty)
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

    // Here, we're substituting properties also in dependencies that
    // come from parents or dependency management. This may not be
    // the right thing to do.

    // FIXME The extra properties should only be added for Maven projects, not Ivy ones
    val properties0 = Seq(
      // some artifacts seem to require these (e.g. org.jmock:jmock-legacy:2.5.1)
      // although I can find no mention of them in any manual / spec
      "pom.groupId"         -> project.module.organization,
      "pom.artifactId"      -> project.module.name,
      "pom.version"         -> project.version
    ) ++ project.properties ++ Seq(
      "project.groupId"     -> project.module.organization,
      "project.artifactId"  -> project.module.name,
      "project.version"     -> project.version
    ) ++ project.parent.toSeq.flatMap {
      case (parModule, parVersion) =>
        Seq(
          "project.parent.groupId"     -> parModule.organization,
          "project.parent.artifactId"  -> parModule.name,
          "project.parent.version"     -> parVersion
        )
    }

    val properties = propertiesMap(properties0)

    val configurations = withParentConfigurations(from.configuration, project.configurations)

    withExclusions(
      depsWithDependencyManagement(
        // Important: properties have to be applied to both,
        //   so that dep mgmt can be matched properly
        // Tested with org.ow2.asm:asm-commons:5.0.2 in CentralTests
        withProperties(project.dependencies, properties),
        withProperties(project.dependencyManagement, properties)
      ),
      from.exclusions
    )
    .map{
      case (config, dep) =>
        (if (config.isEmpty) defaultConfiguration else config) -> {
          if (dep.configuration.isEmpty)
            dep.copy(configuration = defaultConfiguration)
          else
            dep
        }
    }
    .collect{case (config, dep) if configurations(config) =>
      if (from.optional)
        dep.copy(optional = true)
      else
        dep
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
    if (activation.properties.isEmpty)
      false
    else
      activation
        .properties
        .forall {case (name, valueOpt) =>
          props
            .get(name)
            .exists{ v =>
              valueOpt
                .forall { reqValue =>
                  if (reqValue.startsWith("!"))
                    v != reqValue.drop(1)
                  else
                    v == reqValue
                }
            }
        }

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
  filter: Option[Dependency => Boolean],
  profileActivation: Option[(String, Activation, Map[String, String]) => Boolean]
) {

  import Resolution._

  private val finalDependenciesCache =
    new mutable.HashMap[Dependency, Seq[Dependency]]()
  private def finalDependencies0(dep: Dependency) =
    finalDependenciesCache.synchronized {
      finalDependenciesCache.getOrElseUpdate(dep, {
        if (dep.transitive)
          projectCache.get(dep.moduleVersion) match {
            case Some((_, proj)) =>
              finalDependencies(dep, proj)
                .filter(filter getOrElse defaultFilter)
            case None => Nil
          }
        else
          Nil
      })
    }

  /**
   * Transitive dependencies of the current dependencies, according to
   * what there currently is in cache.
   *
   * No attempt is made to solve version conflicts here.
   */
  def transitiveDependencies: Seq[Dependency] =
    (dependencies -- conflicts)
      .toList
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
  def nextDependenciesAndConflicts: (Seq[Dependency], Seq[Dependency], Map[Module, String]) =
    // TODO Provide the modules whose version was forced by dependency overrides too
    merge(
      rootDependencies.map(withDefaultConfig) ++ dependencies ++ transitiveDependencies,
      forceVersions
    )

  /**
   * The modules we miss some info about.
   */
  def missingFromCache: Set[ModuleVersion] = {
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
  def isDone: Boolean = {
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
  def reverseDependencies: Map[Dependency, Vector[Dependency]] = {
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
  def remainingDependencies: Set[Dependency] = {
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
            .toList
            .toMap
        )
    }

    val filteredReverseDependencies = helper(reverseDependencies)

    rootDependencies0 ++ filteredReverseDependencies.keys
  }

  /**
   * The final next dependency set, stripped of no more required ones.
   */
  def newDependencies: Set[Dependency] = {
    val remainingDependencies0 = remainingDependencies

    nextDependenciesAndConflicts._2
      .filter(dep => remainingDependencies0(eraseVersion(dep)))
      .toSet
  }

  private def nextNoMissingUnsafe: Resolution = {
    val (newConflicts, _, _) = nextDependenciesAndConflicts

    copy(
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

    val approxProperties0 =
      project.parent
        .flatMap(projectCache.get)
        .map(_._2.properties)
        .fold(project.properties)(project.properties ++ _)

    val approxProperties = propertiesMap(approxProperties0) ++ Seq(
      "project.groupId"     -> project.module.organization,
      "project.artifactId"  -> project.module.name,
      "project.version"     -> project.version
    )

    val profileDependencies =
      profiles(
        project,
        approxProperties,
        profileActivation getOrElse defaultProfileActivation
      ).flatMap(p => p.dependencies ++ p.dependencyManagement)

    val modules = withProperties(
      project.dependencies ++ project.dependencyManagement ++ profileDependencies,
      approxProperties
    ).collect {
      case ("import", dep) => dep.moduleVersion
    }

    modules.toSet ++ project.parent
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

    // A bit fragile, but seems to work
    // TODO Add non regression test for the touchy  org.glassfish.jersey.core:jersey-client:2.19
    //      (for the way it uses  org.glassfish.hk2:hk2-bom,2.4.0-b25)

    val approxProperties0 =
      project.parent
        .filter(projectCache.contains)
        .map(projectCache(_)._2.properties.toMap)
        .fold(project.properties)(project.properties ++ _)

    val approxProperties = propertiesMap(approxProperties0) ++ Seq(
      "project.groupId"     -> project.module.organization,
      "project.artifactId"  -> project.module.name,
      "project.version"     -> project.version
    )

    val profiles0 = profiles(
      project,
      approxProperties,
      profileActivation getOrElse defaultProfileActivation
    )

    val dependencies0 = addDependencies(
      (project.dependencies +: profiles0.map(_.dependencies)).map(withProperties(_, approxProperties))
    )
    val dependenciesMgmt0 = addDependencies(
      (project.dependencyManagement +: profiles0.map(_.dependencyManagement)).map(withProperties(_, approxProperties))
    )
    val properties0 =
      (project.properties /: profiles0) { (acc, p) =>
        acc ++ p.properties
      }

    val deps0 = (
      dependencies0
        .collect { case ("import", dep) =>
          dep.moduleVersion
        } ++
      dependenciesMgmt0
        .collect { case ("import", dep) =>
          dep.moduleVersion
        } ++
      project.parent
    )

      val deps = deps0.filter(projectCache.contains)

    val projs = deps
      .map(projectCache(_)._2)

    val depMgmt = (
      project.dependencyManagement +: (
        profiles0.map(_.dependencyManagement) ++
        projs.map(_.dependencyManagement)
      )
    )
      .map(withProperties(_, approxProperties))
      .foldLeft(Map.empty[DepMgmt.Key, (String, Dependency)])(DepMgmt.addSeq)

    val depsSet = deps.toSet

    project.copy(
      dependencies =
        dependencies0
          .filterNot{case (config, dep) =>
            config == "import" && depsSet(dep.moduleVersion)
          } ++
        project.parent
          .filter(projectCache.contains)
          .toSeq
          .flatMap(projectCache(_)._2.dependencies),
      dependencyManagement = depMgmt.values.toSeq
        .filterNot{case (config, dep) =>
          config == "import" && depsSet(dep.moduleVersion)
        },
      properties = project.parent
        .filter(projectCache.contains)
        .map(projectCache(_)._2.properties)
        .fold(properties0)(properties0 ++ _)
    )
  }

  def minDependencies: Set[Dependency] =
    Orders.minDependencies(
      dependencies,
      dep =>
        projectCache
          .get(dep)
          .map(_._2.configurations)
          .getOrElse(Map.empty)
    )

  private def artifacts0(overrideClassifiers: Option[Seq[String]]): Seq[Artifact] =
    for {
      dep <- minDependencies.toSeq
      (source, proj) <- projectCache
        .get(dep.moduleVersion)
        .toSeq
      artifact <- source
        .artifacts(dep, proj, overrideClassifiers)
    } yield artifact

  def classifiersArtifacts(classifiers: Seq[String]): Seq[Artifact] =
    artifacts0(Some(classifiers))

  def artifacts: Seq[Artifact] =
    artifacts0(None)

  private def dependencyArtifacts0(overrideClassifiers: Option[Seq[String]]): Seq[(Dependency, Artifact)] =
    for {
      dep <- minDependencies.toSeq
      (source, proj) <- projectCache
        .get(dep.moduleVersion)
        .toSeq
      artifact <- source
        .artifacts(dep, proj, overrideClassifiers)
    } yield dep -> artifact

  def dependencyArtifacts: Seq[(Dependency, Artifact)] =
    dependencyArtifacts0(None)

  def dependencyClassifiersArtifacts(classifiers: Seq[String]): Seq[(Dependency, Artifact)] =
    dependencyArtifacts0(Some(classifiers))

  def errors: Seq[(Dependency, Seq[String])] =
    for {
      dep <- dependencies.toSeq
      err <- errorCache
        .get(dep.moduleVersion)
        .toSeq
    } yield (dep, err)

  def subset(dependencies: Set[Dependency]): Resolution = {
    val (_, _, finalVersions) = nextDependenciesAndConflicts

    def updateVersion(dep: Dependency): Dependency =
      dep.copy(version = finalVersions.getOrElse(dep.module, dep.version))

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

    copy(
      rootDependencies = dependencies,
      dependencies = helper(dependencies.map(updateVersion))
      // don't know if something should be done about conflicts
    )
  }
}
