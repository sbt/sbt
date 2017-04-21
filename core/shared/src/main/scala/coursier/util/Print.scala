package coursier.util

import coursier.core.{ Attributes, Dependency, Module, Orders, Project, Resolution }

object Print {

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
      .groupBy(_.copy(configuration = ""))
      .toVector
      .map { case (k, l) =>
        k.copy(configuration = l.toVector.map(_.configuration).sorted.mkString(";"))
      }
      .sortBy { dep =>
        (dep.module.organization, dep.module.name, dep.module.toString, dep.version)
      }

    deps1.map(dependency(_, printExclusions)).mkString("\n")
  }

  private def compatibleVersions(first: String, second: String): Boolean = {
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

    val (red, yellow, reset) =
      if (colors)
        (Console.RED, Console.YELLOW, Console.RESET)
      else
        ("", "", "")

    final case class Elem(dep: Dependency, excluded: Boolean) {

      lazy val reconciledVersion = resolution.reconciledVersions
        .getOrElse(dep.module, dep.version)

      lazy val repr =
        if (excluded)
          resolution.reconciledVersions.get(dep.module) match {
            case None =>
              s"$yellow(excluded)$reset ${dep.module}:${dep.version}"
            case Some(version) =>
              val versionMsg =
                if (version == dep.version)
                  "this version"
                else
                  s"version $version"

                s"${dep.module}:${dep.version} " +
                  s"$red(excluded, $versionMsg present anyway)$reset"
          }
        else {
          val versionStr =
            if (reconciledVersion == dep.version)
              dep.version
            else {
              val assumeCompatibleVersions = compatibleVersions(dep.version, reconciledVersion)

              (if (assumeCompatibleVersions) yellow else red) +
                s"${dep.version} -> $reconciledVersion" +
                (if (assumeCompatibleVersions || colors) "" else " (possible incompatibility)") +
                reset
            }

          s"${dep.module}:$versionStr"
        }

      lazy val children: Seq[Elem] =
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
                Elem(
                  Dependency(mod, ver, "", Set.empty, Attributes("", ""), false, false),
                  excluded = true
                )
            }

          dependencies.map(Elem(_, excluded = false)) ++
            (if (printExclusions) excluded else Nil)
        }
    }

    if (reverse) {

      final case class Parent(
        module: Module,
        version: String,
        dependsOn: Module,
        wantVersion: String,
        gotVersion: String,
        excluding: Boolean
      ) {
        lazy val repr: String =
          if (excluding)
            s"$yellow(excluded by)$reset $module:$version"
          else if (wantVersion == gotVersion)
            s"$module:$version"
          else {
            val assumeCompatibleVersions = compatibleVersions(wantVersion, gotVersion)

            s"$module:$version " +
              (if (assumeCompatibleVersions) yellow else red) +
              s"(wants $dependsOn:$wantVersion, got $gotVersion)" +
              reset
          }
      }

      val parents: Map[Module, Seq[Parent]] = {
        val links = for {
          dep <- resolution.dependencies.toVector
          elem <- Elem(dep, excluded = false).children
        }
          yield elem.dep.module -> Parent(
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

      Tree(
        resolution
          .dependencies
          .toVector
          .sortBy(dep => (dep.module.organization, dep.module.name, dep.version))
          .map(dep =>
            Parent(dep.module, dep.version, dep.module, dep.version, dep.version, excluding = false)
          )
      )(children, _.repr)
    } else
      Tree(roots.toVector.map(Elem(_, excluded = false)))(_.children, _.repr)
  }

}
