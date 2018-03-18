package coursier.util

import coursier.core.{ Attributes, Dependency, Module, Orders, Project, Resolution }

object Print {

  object Colors {
    private val `with`: Colors = Colors(Console.RED, Console.YELLOW, Console.RESET)
    private val `without`: Colors = Colors("", "", "")

    def get(colors: Boolean): Colors = if (colors) `with` else `without`
  }

  case class Colors private(red: String, yellow: String, reset: String)

  trait Renderable {
    def repr(colors: Colors): String
  }

  trait Elem extends Renderable {
    def dep: Dependency
    def excluded: Boolean
    def reconciledVersion: String
    def children: Seq[Elem]
  }

  trait Parent extends Renderable {
    def module: Module
    def version: String
    def dependsOn: Module
    def wantVersion: String
    def reconciledVersion: String
    def excluding: Boolean
  }

  def dependency(dep: Dependency): String =
    dependency(dep, printExclusions = false)

  def dependency(dep: Dependency, printExclusions: Boolean): String = {

    def exclusionsStr = dep
      .exclusions
      .toVector
      .sorted
      .map {
        case (org, name) =>
          s"\n  exclude($org, $name)"
      }
      .mkString

    s"${dep.module}:${dep.version}:${dep.configuration}" + (if (printExclusions) exclusionsStr else "")
  }

  def dependenciesUnknownConfigs(deps: Seq[Dependency], projects: Map[(Module, String), Project]): String =
    dependenciesUnknownConfigs(deps, projects, printExclusions = false)

  def dependenciesUnknownConfigs(
    deps: Seq[Dependency],
    projects: Map[(Module, String), Project],
    printExclusions: Boolean
  ): String = {

    val deps0 = deps.map { dep =>
      dep.copy(
        version = projects
          .get(dep.moduleVersion)
          .fold(dep.version)(_.version)
      )
    }

    val minDeps = Orders.minDependencies(
      deps0.toSet,
      _ => Map.empty
    )

    val deps1 = minDeps
      .groupBy(_.copy(configuration = "", attributes = Attributes("", "")))
      .toVector
      .map { case (k, l) =>
        k.copy(configuration = l.toVector.map(_.configuration).sorted.distinct.mkString(";"))
      }
      .sortBy { dep =>
        (dep.module.organization, dep.module.name, dep.module.toString, dep.version)
      }

    deps1.map(dependency(_, printExclusions)).mkString("\n")
  }

  def compatibleVersions(first: String, second: String): Boolean = {
    // too loose for now
    // e.g. RCs and milestones should not be considered compatible with subsequent non-RC or
    // milestone versions - possibly not with each other either

    first.split('.').take(2).toSeq == second.split('.').take(2).toSeq
  }

  def dependencyTree(
    roots: Seq[Dependency],
    resolution: Resolution,
    printExclusions: Boolean,
    reverse: Boolean
  ): String =
    dependencyTree(roots, resolution, printExclusions, reverse, colors = true)

  def dependencyTree(
    roots: Seq[Dependency],
    resolution: Resolution,
    printExclusions: Boolean,
    reverse: Boolean,
    colors: Boolean
  ): String = {
    val colorsCase = Colors.get(colors)

    if (reverse) {
      reverseTree(resolution.dependencies.toSeq, resolution, printExclusions).render(_.repr(colorsCase))
    } else {
      normalTree(roots, resolution, printExclusions).render(_.repr(colorsCase))
    }

  }

  private def getElemFactory(resolution: Resolution, withExclusions: Boolean): Dependency => Elem = {
    final case class ElemImpl(dep: Dependency, excluded: Boolean) extends Elem {

      val reconciledVersion: String = resolution.reconciledVersions
        .getOrElse(dep.module, dep.version)

      def repr(colors: Colors): String =
        if (excluded)
          resolution.reconciledVersions.get(dep.module) match {
            case None =>
              s"${colors.yellow}(excluded)${colors.reset} ${dep.module}:${dep.version}"
            case Some(version) =>
              val versionMsg =
                if (version == dep.version)
                  "this version"
                else
                  s"version $version"

              s"${dep.module}:${dep.version} " +
                s"${colors.red}(excluded, $versionMsg present anyway)${colors.reset}"
          }
        else {
          val versionStr =
            if (reconciledVersion == dep.version)
              dep.version
            else {
              val assumeCompatibleVersions = compatibleVersions(dep.version, reconciledVersion)

              (if (assumeCompatibleVersions) colors.yellow else colors.red) +
                s"${dep.version} -> $reconciledVersion" +
                (if (assumeCompatibleVersions) "" else " (possible incompatibility)") +
                colors.reset
            }

          s"${dep.module}:$versionStr"
        }

      val children: Seq[Elem] =
        if (excluded)
          Nil
        else {
          val dep0 = dep.copy(version = reconciledVersion)

          val dependencies = resolution.dependenciesOf(
            dep0,
            withReconciledVersions = false
          ).sortBy { trDep =>
            (trDep.module.organization, trDep.module.name, trDep.version)
          }

          def excluded = resolution
            .dependenciesOf(
              dep0.copy(exclusions = Set.empty),
              withReconciledVersions = false
            )
            .sortBy { trDep =>
              (trDep.module.organization, trDep.module.name, trDep.version)
            }
            .map(_.moduleVersion)
            .filterNot(dependencies.map(_.moduleVersion).toSet).map {
            case (mod, ver) =>
              ElemImpl(
                Dependency(mod, ver, "", Set.empty, Attributes("", ""), false, false),
                excluded = true
              )
          }

          dependencies.map(ElemImpl(_, excluded = false)) ++
            (if (withExclusions) excluded else Nil)
        }
    }

    a => ElemImpl(a, excluded = false)
  }

  def normalTree(roots: Seq[Dependency], resolution: Resolution, withExclusions: Boolean): Tree[Elem] = {
    val elemFactory = getElemFactory(resolution, withExclusions)
    Tree[Elem](roots.toVector.map(elemFactory), (elem: Elem) => elem.children)
  }

  def reverseTree(roots: Seq[Dependency], resolution: Resolution, withExclusions: Boolean): Tree[Parent] = {
    val elemFactory = getElemFactory(resolution, withExclusions)

    final case class ParentImpl(
                                 module: Module,
                                 version: String,
                                 dependsOn: Module,
                                 wantVersion: String,
                                 reconciledVersion: String,
                                 excluding: Boolean
                               ) extends Parent {
      def repr(colors: Colors): String =
        if (excluding)
          s"${colors.yellow}(excluded by)${colors.reset} $module:$version"
        else if (wantVersion == reconciledVersion)
          s"$module:$version"
        else {
          val assumeCompatibleVersions = compatibleVersions(wantVersion, reconciledVersion)

          s"$module:$version " +
            (if (assumeCompatibleVersions) colors.yellow else colors.red) +
            s"$dependsOn:$wantVersion -> $reconciledVersion" +
            colors.reset
        }
    }

    val parents: Map[Module, Seq[Parent]] = {
      val links = for {
        dep <- resolution.dependencies.toVector
        elem <- elemFactory(dep).children
      }
        yield elem.dep.module -> ParentImpl(
          dep.module,
          dep.version,
          elem.dep.module,
          elem.dep.version,
          elem.reconciledVersion,
          elem.excluded
        )

      links
        .groupBy(_._1)
        .mapValues(_.map(_._2).distinct.sortBy(par => (par.module.organization, par.module.name)))
        .iterator
        .toMap
    }

    def children(par: Parent) =
      if (par.excluding)
        Nil
      else
        parents.getOrElse(par.module, Nil)

    Tree[Parent](roots
      .toVector
      .sortBy(dep => (dep.module.organization, dep.module.name, dep.version))
      .map(dep => {
        ParentImpl(dep.module, dep.version, dep.module, dep.version, dep.version, excluding = false)
      }), (par: Parent) => children(par))
  }

}
