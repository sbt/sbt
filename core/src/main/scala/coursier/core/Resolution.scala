package coursier.core

import java.util.regex.Pattern.quote

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.concurrent.Task
import scalaz.{EitherT, \/-, \/, -\/}

object Resolution {

  type ModuleVersion = (Module, String)

  /**
   * Try to find `module` among `repositories`.
   *
   * Look at `repositories` from the left, one-by-one, and stop at first success.
   * Else, return all errors, in the same order.
   *
   * The `version` field of the returned `Project` in case of success may not be
   * equal to the provided one, in case the latter is not a specific
   * version (e.g. version interval). Which version get chosen depends on
   * the repository implementation.
   */
  def find(repositories: Seq[Repository],
           module: Module,
           version: String): EitherT[Task, List[String], (Repository, Project)] = {

    val lookups = repositories.map(repo => repo -> repo.find(module, version).run)
    val task = lookups.foldLeft(Task.now(-\/(Nil)): Task[List[String] \/ (Repository, Project)]) {
      case (acc, (repo, t)) =>
        acc.flatMap {
          case -\/(errors) =>
            t.map(res => res
              .flatMap(project =>
                if (project.module == module) \/-((repo, project))
                else -\/(s"Wrong module returned (expected: $module, got: ${project.module})")
              )
              .leftMap(error => error :: errors)
            )

          case res @ \/-(_) =>
            Task.now(res)
        }
    }

    EitherT(task.map(_.leftMap(_.reverse))).map { case x @ (_, proj) =>
      assert(proj.module == module)
      x
    }
  }

  /**
   * Get the active profiles of `project`, using the current properties `properties`,
   * and `profileActivation` stating if a profile is active.
   */
  def profiles(project: Project,
               properties: Map[String, String],
               profileActivation: (String, Activation, Map[String, String]) => Boolean): Seq[Profile] = {

    val activated = project.profiles
      .filter(p => profileActivation(p.id, p.activation, properties))

    def default = project.profiles
      .filter(_.activeByDefault.toSeq.contains(true))

    if (activated.isEmpty) default
    else activated
  }

  type DepMgmtKey = (String, String, String)
  def dependencyManagementKey(dep: Dependency): DepMgmtKey =
    (dep.module.organization, dep.module.name, dep.attributes.`type`)
  def dependencyManagementAdd(m: Map[DepMgmtKey, Dependency], dep: Dependency): Map[DepMgmtKey, Dependency] = {
    val key = dependencyManagementKey(dep)
    if (m.contains(key)) m else m + (key -> dep)
  }
  def dependencyManagementAddSeq(m: Map[DepMgmtKey, Dependency], deps: Seq[Dependency]): Map[DepMgmtKey, Dependency] =
    (m /: deps)(dependencyManagementAdd)

  def mergeProperties(m: Map[String, String], other: Map[String, String]): Map[String, String] = {
    m ++ other.filterKeys(!m.contains(_))
  }

  def addDependencies(deps: Seq[Seq[Dependency]]): Seq[Dependency] = {
    val res =
      deps.foldRight((Set.empty[DepMgmtKey], Seq.empty[Dependency])) {
        case (deps0, (set, acc)) =>
          val deps = deps0.filter(dep => !set(dependencyManagementKey(dep)))
          (set ++ deps.map(dependencyManagementKey), acc ++ deps)
      }

    res._2
  }

  val propRegex = (quote("${") + "([a-zA-Z0-9-.]*)" + quote("}")).r

  /**
   * Substitutes `properties` in `dependencies`.
   */
  def withProperties(dependencies: Seq[Dependency],
                     properties: Map[String, String]): Seq[Dependency] = {

    def substituteProps(s: String) = {
      val matches = propRegex.findAllMatchIn(s).toList.reverse
      if (matches.isEmpty) s
      else {
        val output = (new StringBuilder(s) /: matches) {
          (b, m) => properties.get(m.group(1)).fold(b)(b.replace(m.start, m.end, _))
        }
        output.result()
      }
    }

    dependencies.map{ dep =>
      dep.copy(
        module = dep.module.copy(
          organization = substituteProps(dep.module.organization),
          name = substituteProps(dep.module.name)
        ),
        version = substituteProps(dep.version),
        attributes = dep.attributes.copy(
          `type` = substituteProps(dep.attributes.`type`),
          classifier = substituteProps(dep.attributes.classifier)
        ),
        scope = Parse.scope(substituteProps(dep.scope.name)),
        exclusions = dep.exclusions
          .map{case (org, name) => (substituteProps(org), substituteProps(name))}
        // FIXME The content of the optional tag may also be a property in the original POM.
        // Maybe not parse it that earlier?
      )
    }
  }

  /**
   * Merge several version constraints together. Returns `None` in case of conflict.
   */
  def mergeVersions(versions: Seq[String]): Option[String] = {
    val (nonParsedConstraints, parsedConstraints) =
      versions
        .map(v => v -> Parse.versionConstraint(v))
        .partition(_._2.isEmpty)

    // FIXME Report this in return type, not this way
    if (nonParsedConstraints.nonEmpty)
      Console.err.println(s"Ignoring unparsed versions: ${nonParsedConstraints.map(_._1)}")

    val constraintOpt =
      (Option(VersionInterval.zero) /: parsedConstraints.map(_._2.get.interval)) {
        case (acc, itv) => acc.flatMap(_.merge(itv))
      } .map(_.constraint)

    constraintOpt.map(_.repr)
  }

  /**
   * Merge several dependencies, solving version constraints of duplicated modules.
   * Returns the conflicted dependencies, and the (merged) others.
   */
  def merge(dependencies: TraversableOnce[Dependency]): (Seq[Dependency], Seq[Dependency]) = {
    val m = dependencies
      .toList
      .groupBy(dep => dep.module)
      .mapValues{ deps =>
        if (deps.lengthCompare(1) == 0) \/-(deps)
        else {
          val versions = deps.map(_.version).distinct
          val versionOpt = mergeVersions(versions)

          versionOpt match {
            case Some(version) => \/-(deps.map(dep => dep.copy(version = version)))
            case None => -\/(deps)
          }
        }
      }

    val l = m.values.toList
    (l.collect{case -\/(dep) => dep}.flatten, l.collect{case \/-(dep) => dep}.flatten)
  }

  /**
   * If one of our dependency has scope `base`, and a transitive dependency of it has scope `transitive`,
   * return the scope of the latter for us, if any. If empty, means the transitive dependency
   * should not be considered a dependency for us.
   *
   * See https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope.
   */
  def resolveScope(base: Scope,
                   transitive: Scope): Option[Scope] =
    (base, transitive) match {
      case (Scope.Compile, other) => Some(other)
      case (Scope.Runtime, Scope.Compile) => Some(Scope.Runtime)
      case (Scope.Runtime, other) => Some(other)
      case _ => None
    }

  /**
   * Applies `dependencyManagement` to `dependencies`.
   *
   * Fill empty version / scope / exclusions, for dependencies found in `dependencyManagement`.
   */
  def depsWithDependencyManagement(dependencies: Seq[Dependency],
                                   dependencyManagement: Seq[Dependency]): Seq[Dependency] = {

    // See http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Management

    lazy val m = dependencyManagementAddSeq(Map.empty, dependencyManagement)

    dependencies.map { dep0 =>
      var dep = dep0

      for (mgmtDep <- m.get(dependencyManagementKey(dep0))) {
        if (dep.version.isEmpty)
          dep = dep.copy(version = mgmtDep.version)
        if (dep.scope.name.isEmpty)
          dep = dep.copy(scope = mgmtDep.scope)

        if (dep.exclusions.isEmpty)
          dep = dep.copy(exclusions = mgmtDep.exclusions)
      }
      
      dep
    }
  }


  def withDefaultScope(dep: Dependency): Dependency =
    if (dep.scope.name.isEmpty) dep.copy(scope = Scope.Compile)
    else dep

  /**
   * Filters `dependencies` with `exclusions`.
   */
  def withExclusions(dependencies: Seq[Dependency],
                     exclusions: Set[(String, String)]): Seq[Dependency] = {

    val filter = Exclusions(exclusions)

    dependencies
      .filter(dep => filter(dep.module.organization, dep.module.name))
      .map(dep =>
        dep.copy(exclusions = Exclusions.minimize(dep.exclusions ++ exclusions))
      )
  }

  /**
   * Get the dependencies of `project`, knowing that it came from dependency `from` (that is,
   * `from.module == project.module`).
   *
   * Substitute properties, update scopes, apply exclusions, and get extra parameters from
   * dependency management along the way.
   */
  def finalDependencies(from: Dependency,
                        project: Project): Seq[Dependency] = {

    // Here, we're substituting properties also in dependencies that come from parents
    // or dependency management. This may not be the right thing to do.

    val properties = mergeProperties(
      project.properties,
      Map(
        "project.groupId" -> project.module.organization,
        "project.artifactId" -> project.module.name,
        "project.version" -> project.version
      )
    )

    val deps =
      withExclusions(
        depsWithDependencyManagement(
          // important: properties have to be applied to both, so that dep mgmt can be matched properly
          // See the added test with org.ow2.asm:asm-commons:5.0.2
          withProperties(project.dependencies, properties),
          withProperties(project.dependencyManagement, properties)
        ),
        from.exclusions
      )
      .map(withDefaultScope)

    deps.flatMap { trDep =>
      resolveScope(from.scope, trDep.scope)
        .map(scope => trDep.copy(scope = scope, optional = trDep.optional || from.optional))
    }
  }

  /**
   * Default function checking whether a profile is active, given its id, activation conditions,
   * and the properties of its project.
   */
  def defaultProfileActivation(id: String,
                               activation: Activation,
                               props: Map[String, String]): Boolean = {

    if (activation.properties.isEmpty) false
    else {
      activation.properties.forall { case (name, valueOpt) =>
        props.get(name).exists{ v =>
          valueOpt.forall { reqValue =>
            if (reqValue.startsWith("!")) v != reqValue.drop(1)
            else v == reqValue
          }
        }
      }
    }
  }

  /**
   * Default dependency filter used during resolution.
   *
   * Only follows compile scope / non-optional dependencies.
   */
  def defaultFilter(dep: Dependency): Boolean =
    !dep.optional && dep.scope == Scope.Compile

}


/**
 * State of a dependency resolution.
 *
 * Done if method `isDone` returns `true`.
 *
 * @param dependencies: current set of dependencies
 * @param conflicts: conflicting dependencies
 * @param projectsCache: cache of known projects
 * @param errors: keeps track of the modules whose project definition could not be found
 */
case class Resolution(rootDependencies: Set[Dependency],
                      dependencies: Set[Dependency],
                      conflicts: Set[Dependency],
                      projectsCache: Map[Resolution.ModuleVersion, (Repository, Project)],
                      errors: Map[Resolution.ModuleVersion, Seq[String]],
                      filter: Option[Dependency => Boolean],
                      profileActivation: Option[(String, Activation, Map[String, String]) => Boolean]) {
  import Resolution._

  private val finalDependenciesCache = new mutable.HashMap[Dependency, Seq[Dependency]]()
  private def finalDependencies0(dep: Dependency) = finalDependenciesCache.synchronized {
    finalDependenciesCache.getOrElseUpdate(dep,
      projectsCache.get(dep.moduleVersion) match {
        case Some((_, proj)) => finalDependencies(dep, proj).filter(filter getOrElse defaultFilter)
        case None => Nil
      }
    )
  }

  /**
   * Transitive dependencies of the current dependencies, according to what there currently is in cache.
   * No attempt is made to solve version conflicts here.
   */
  def transitiveDependencies: Seq[Dependency] =
    (dependencies -- conflicts)
      .toList
      .flatMap(finalDependencies0)

  /**
   * The "next" dependency set, made of the current dependencies and their transitive dependencies,
   * trying to solve version conflicts. Transitive dependencies are calculated with the current cache.
   *
   * May contain dependencies added in previous iterations, but no more required. These are filtered below, see
   * `newDependencies`.
   *
   * Returns a tuple made of the conflicting dependencies, and all the dependencies.
   */
  def nextDependenciesAndConflicts: (Seq[Dependency], Seq[Dependency]) = {
    merge(rootDependencies.map(withDefaultScope) ++ dependencies ++ transitiveDependencies)
  }

  /**
   * The modules we miss some info about.
   */
  def missingFromCache: Set[ModuleVersion] = {
    val modules = dependencies.map(_.moduleVersion)
    val nextModules = nextDependenciesAndConflicts._2.map(_.moduleVersion)

    (modules ++ nextModules)
      .filterNot(mod => projectsCache.contains(mod) || errors.contains(mod))
  }


  /**
   * Whether the resolution is done.
   */
  def isDone: Boolean = {
    def isFixPoint = {
      val (nextConflicts, _) = nextDependenciesAndConflicts
      dependencies == (newDependencies ++ nextConflicts) && conflicts == nextConflicts.toSet
    }

    missingFromCache.isEmpty && isFixPoint
  }

  private def eraseVersion(dep: Dependency) = dep.copy(version = "")

  /**
   * Returns a map giving the dependencies that brought each of the dependency of the "next" dependency set.
   *
   * The versions of all the dependencies returned are erased (emptied).
   */
  def reverseDependencies: Map[Dependency, Vector[Dependency]] = {
    val (updatedConflicts, updatedDeps) = nextDependenciesAndConflicts

    val trDepsSeq =
      for {
        dep <- updatedDeps
        trDep <- finalDependencies0(dep)
      } yield eraseVersion(trDep) -> eraseVersion(dep)

    val knownDeps = (updatedDeps ++ updatedConflicts).map(eraseVersion).toSet

    trDepsSeq
      .groupBy(_._1)
      .mapValues(_.map(_._2).toVector)
      .filterKeys(knownDeps)
      .toList.toMap // Eagerly evaluate filterKeys/mapValues
  }

  /**
   * Returns dependencies from the "next" dependency set, filtering out
   * those that are no more required.
   *
   * The versions of all the dependencies returned are erased (emptied).
   */
  def remainingDependencies: Set[Dependency] = {
    val rootDependencies0 = rootDependencies.map(withDefaultScope).map(eraseVersion)

    @tailrec
    def helper(reverseDeps: Map[Dependency, Vector[Dependency]]): Map[Dependency, Vector[Dependency]] = {
      val (toRemove, remaining) = reverseDeps.partition(kv => kv._2.isEmpty && !rootDependencies0(kv._1))

      if (toRemove.isEmpty) reverseDeps
      else helper(remaining.mapValues(_.filter(x => remaining.contains(x) || rootDependencies0(x))).toList.toMap)
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
    val (newConflicts, _) = nextDependenciesAndConflicts
    copy(dependencies = newDependencies ++ newConflicts, conflicts = newConflicts.toSet)
  }

  /**
   * If no module info is missing, the next state of the resolution, which can be immediately calculated.
   * Else, the current resolution itself.
   */
  def nextIfNoMissing: Resolution = {
    val missing = missingFromCache
    if (missing.isEmpty) nextNoMissingUnsafe
    else this
  }

  /**
   * Do a new iteration, fetching the missing modules along the way.
   */
  def next(fetchModule: ModuleVersion => EitherT[Task, List[String], (Repository, Project)]): Task[Resolution] = {
    val missing = missingFromCache
    if (missing.isEmpty) Task.now(nextNoMissingUnsafe)
    else fetch(missing.toList, fetchModule).map(_.nextIfNoMissing)
  }

  /**
   * Required modules for the dependency management of `project`.
   */
  def dependencyManagementRequirements(project: Project): Set[ModuleVersion] = {
    val approxProperties =
      project.parent
        .flatMap(projectsCache.get)
        .map(_._2.properties)
        .fold(project.properties)(mergeProperties(project.properties, _))

    val profileDependencies =
      profiles(project, approxProperties, profileActivation getOrElse defaultProfileActivation)
        .flatMap(_.dependencies)

    val modules =
      (project.dependencies ++ profileDependencies)
        .collect{ case dep if dep.scope == Scope.Import => dep.moduleVersion } ++
      project.parent

    modules.toSet
  }

  /**
   * Missing modules in cache, to get the full list of dependencies of `project`, taking
   * dependency management / inheritance into account.
   *
   * Note that adding the missing modules to the cache may unveil other missing modules, so
   * these modules should be added to the cache, and `dependencyManagementMissing` checked again
   * for new missing modules.
   */
  def dependencyManagementMissing(project: Project): Set[ModuleVersion] = {

    @tailrec
    def helper(toCheck: Set[ModuleVersion],
               done: Set[ModuleVersion],
               missing: Set[ModuleVersion]): Set[ModuleVersion] = {

      if (toCheck.isEmpty) missing
      else if (toCheck.exists(done)) helper(toCheck -- done, done, missing)
      else if (toCheck.exists(missing)) helper(toCheck -- missing, done, missing)
      else if (toCheck.exists(projectsCache.contains)) {
        val (checking, remaining) = toCheck.partition(projectsCache.contains)
        val directRequirements = checking.flatMap(mod => dependencyManagementRequirements(projectsCache(mod)._2))

        helper(remaining ++ directRequirements, done ++ checking, missing)
      } else if (toCheck.exists(errors.contains)) {
        val (errored, remaining) = toCheck.partition(errors.contains)
        helper(remaining, done ++ errored, missing)
      } else
        helper(Set.empty, done, missing ++ toCheck)
    }

    helper(dependencyManagementRequirements(project), Set(project.moduleVersion), Set.empty)
  }

  /**
   * Add dependency management / inheritance related items to `project`, from what's available in cache.
   * It is recommended to have fetched what `dependencyManagementMissing` returned prior to calling
   * `withDependencyManagement`.
   */
  def withDependencyManagement(project: Project): Project = {

    val approxProperties =
      project.parent
        .filter(projectsCache.contains)
        .map(projectsCache(_)._2.properties)
        .fold(project.properties)(mergeProperties(project.properties, _))

    val profiles0 = profiles(project, approxProperties, profileActivation getOrElse defaultProfileActivation)

    val dependencies0 = addDependencies(project.dependencies +: profiles0.map(_.dependencies))
    val properties0 = (project.properties /: profiles0)((acc, p) => mergeProperties(acc, p.properties))

    val deps =
      dependencies0
        .collect{ case dep if dep.scope == Scope.Import && projectsCache.contains(dep.moduleVersion) => dep.moduleVersion } ++
        project.parent.filter(projectsCache.contains)
    val projs = deps.map(projectsCache(_)._2)

    val depMgmt =
      (project.dependencyManagement +: (profiles0.map(_.dependencyManagement) ++ projs.map(_.dependencyManagement)))
        .foldLeft(Map.empty[DepMgmtKey, Dependency])(dependencyManagementAddSeq)

    val depsSet = deps.toSet

    project.copy(
      dependencies = dependencies0
        .filterNot(dep => dep.scope == Scope.Import && depsSet(dep.moduleVersion)) ++
        project.parent
          .filter(projectsCache.contains)
          .toSeq
          .flatMap(projectsCache(_)._2.dependencies),
      dependencyManagement = depMgmt.values.toSeq,
      properties = project.parent
        .filter(projectsCache.contains)
        .map(projectsCache(_)._2.properties)
        .fold(properties0)(mergeProperties(properties0, _))
    )
  }

  /**
   * Fetch `modules` with `fetchModules`, and add the resulting errors and projects to the cache.
   */
  def fetch(modules: Seq[ModuleVersion],
            fetchModule: ModuleVersion => EitherT[Task, List[String], (Repository, Project)]): Task[Resolution] = {

    val lookups = modules.map(dep => fetchModule(dep).run.map(dep -> _))
    val gatheredLookups = Task.gatherUnordered(lookups, exceptionCancels = true)
    gatheredLookups.flatMap{ lookupResults =>
      val errors0 = errors ++ lookupResults.collect{case (modVer, -\/(repoErrors)) => modVer -> repoErrors}
      val newProjects = lookupResults.collect{case (modVer, \/-(proj)) => modVer -> proj}

      /*
       * newProjects are project definitions, fresh from the repositories. We need to add
       * dependency management / inheritance-related bits to them.
       */

      newProjects.foldLeft(Task.now(copy(errors = errors0))) { case (accTask, (modVer, (repo, proj))) =>
        for {
          current <- accTask
          updated <- current.fetch(current.dependencyManagementMissing(proj).toList, fetchModule)
          proj0 = updated.withDependencyManagement(proj)
        } yield updated.copy(projectsCache = updated.projectsCache + (modVer -> (repo, proj0)))
      }
    }
  }

  def last(fetchModule: ModuleVersion => EitherT[Task, List[String], (Repository, Project)], maxIterations: Int = -1): Task[Resolution] = {
    if (maxIterations == 0 || isDone) Task.now(this)
    else {
      next(fetchModule)
        .flatMap(_.last(fetchModule, if (maxIterations > 0) maxIterations - 1 else maxIterations))
    }
  }

  def stream(fetchModule: ModuleVersion => EitherT[Task, List[String], (Repository, Project)], run: Task[Resolution] => Resolution): Stream[Resolution] = {
    this #:: {
      if (isDone) Stream.empty
      else run(next(fetchModule)).stream(fetchModule, run)
    }
  }

  def minDependencies: Set[Dependency] =
    Orders.minDependencies(dependencies)
}
