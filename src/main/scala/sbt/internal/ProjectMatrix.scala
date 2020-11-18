package sbt
package internal

import java.util.Locale
import scala.collection.immutable.ListMap
import scala.collection.mutable
import Keys._
import scala.util.Try
import sbt.internal.inc.ReflectUtilities

import sbtprojectmatrix.ProjectMatrixKeys

/**
 * A project matrix is an implementation of a composite project
 * that represents cross building across some axis (such as platform)
 * and Scala version.
 *
 *  {{{
 *  lazy val core = (projectMatrix in file("core"))
 *    .settings(
 *      name := "core"
 *    )
 *    .jvmPlatform(Seq("2.12.6", "2.11.12"))
 *  }}}
 */
sealed trait ProjectMatrix extends CompositeProject {
  def id: String

  /** The base directory for the project matrix.*/
  def base: sbt.File

  def withId(id: String): ProjectMatrix

  /** Sets the base directory for this project matrix.*/
  def in(dir: sbt.File): ProjectMatrix

  /** Adds new configurations directly to this project.  To override an existing configuration, use `overrideConfigs`. */
  def configs(cs: Configuration*): ProjectMatrix

  /** Adds classpath dependencies on internal or external projects. */
  def dependsOn(deps: MatrixClasspathDep[ProjectMatrixReference]*): ProjectMatrix

  /**
   * Adds projects to be aggregated.  When a user requests a task to run on this project from the command line,
   * the task will also be run in aggregated projects.
   */
  def aggregate(refs: ProjectMatrixReference*): ProjectMatrix

  /** Appends settings to the current settings sequence for this project. */
  def settings(ss: Def.SettingsDefinition*): ProjectMatrix

  /**
   * Sets the [[sbt.AutoPlugin]]s of this project.
   * An [[sbt.AutoPlugin]] is a common label that is used by plugins to determine what settings, if any, to enable on a project.
   */
  def enablePlugins(ns: Plugins*): ProjectMatrix

  /** Disable the given plugins on this project. */
  def disablePlugins(ps: AutoPlugin*): ProjectMatrix

  /**
   * Applies the given functions to this Project.
   * The second function is applied to the result of applying the first to this Project and so on.
   * The intended use is a convenience for applying default configuration provided by a plugin.
   */
  def configure(transforms: (Project => Project)*): ProjectMatrix

  /**
   * If autoScalaLibrary is false, add non-Scala row.
   * Otherwise, add custom rows for each scalaVersions.
   */
  def customRow(
    autoScalaLibrary: Boolean,
    scalaVersions: Seq[String],
    axisValues: Seq[VirtualAxis],
    process: Project => Project
  ): ProjectMatrix

  def customRow(
    scalaVersions: Seq[String],
    axisValues: Seq[VirtualAxis],
    process: Project => Project
  ): ProjectMatrix

  def customRow(
    autoScalaLibrary: Boolean,
    axisValues: Seq[VirtualAxis],
    process: Project => Project
  ): ProjectMatrix

  def customRow(
    scalaVersions: Seq[String],
    axisValues: Seq[VirtualAxis],
    settings: Seq[Setting[_]]
  ): ProjectMatrix

  def customRow(
    autoScalaLibrary: Boolean,
    axisValues: Seq[VirtualAxis],
    settings: Seq[Setting[_]]
  ): ProjectMatrix

  def jvmPlatform(scalaVersions: Seq[String]): ProjectMatrix
  def jvmPlatform(autoScalaLibrary: Boolean): ProjectMatrix
  def jvmPlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix
  def jvmPlatform(autoScalaLibrary: Boolean, scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix
  def jvm: ProjectFinder

  def jsPlatform(scalaVersions: Seq[String]): ProjectMatrix
  def jsPlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix
  def js: ProjectFinder

  def nativePlatform(scalaVersions: Seq[String]): ProjectMatrix
  def nativePlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix
  def native: ProjectFinder

  def defaultAxes(axes: VirtualAxis*): ProjectMatrix

  def projectRefs: Seq[ProjectReference]

  def filterProjects(axisValues: Seq[VirtualAxis]): Seq[Project]
  def filterProjects(autoScalaLibrary: Boolean, axisValues: Seq[VirtualAxis]): Seq[Project]
  def finder(axisValues: VirtualAxis*): ProjectFinder

  // resolve to the closest match for the given row
  private[sbt] def resolveMatch(thatRow: ProjectMatrix.ProjectRow): ProjectReference
}

/** Represents a reference to a project matrix with an optional configuration string.
 */
sealed trait MatrixClasspathDep[MR <: ProjectMatrixReference] {
  def matrix: MR; def configuration: Option[String]
}

trait ProjectFinder {
  def apply(scalaVersion: String): Project
  def apply(autoScalaLibrary: Boolean): Project
  def get: Seq[Project]
}

object ProjectMatrix {
  import sbt.io.syntax._

  val jvmIdSuffix: String = "JVM"
  val jvmDirectorySuffix: String = "-jvm"
  val jsIdSuffix: String = "JS"
  val jsDirectorySuffix: String = "-js"
  val nativeIdSuffix: String = "Native"
  val nativeDirectorySuffix: String = "-native"

  private[sbt] val allMatrices: mutable.Map[String, ProjectMatrix] = mutable.Map.empty

  /** A row in the project matrix, typically representing a platform + Scala version.
   */
  final class ProjectRow(
      val autoScalaLibrary: Boolean,
      val axisValues: Seq[VirtualAxis],
      val process: Project => Project
  ) {
    def scalaVersionOpt: Option[String] =
      if (autoScalaLibrary)
        (axisValues collect {
          case sv: VirtualAxis.ScalaVersionAxis => sv.scalaVersion
        }).headOption
      else None

    def isMatch(that: ProjectRow): Boolean =
      VirtualAxis.isMatch(this.axisValues, that.axisValues)

    def isSecondaryMatch(that: ProjectRow): Boolean =
      VirtualAxis.isSecondaryMatch(this.axisValues, that.axisValues)

    override def toString: String = s"ProjectRow($autoScalaLibrary, $axisValues)"
  }

  final class ProjectMatrixReferenceSyntax(m: ProjectMatrixReference) {
    def %(conf: String): ProjectMatrix.MatrixClasspathDependency =
      ProjectMatrix.MatrixClasspathDependency(m, Some(conf))

    def %(conf: Configuration): ProjectMatrix.MatrixClasspathDependency =
      ProjectMatrix.MatrixClasspathDependency(m, Some(conf.name))
  }

  final case class MatrixClasspathDependency(
      matrix: ProjectMatrixReference,
      configuration: Option[String]
  ) extends MatrixClasspathDep[ProjectMatrixReference]

  private final class ProjectMatrixDef(
      val id: String,
      val base: sbt.File,
      val scalaVersions: Seq[String],
      val rows: Seq[ProjectRow],
      val aggregate: Seq[ProjectMatrixReference],
      val dependencies: Seq[MatrixClasspathDep[ProjectMatrixReference]],
      val settings: Seq[Def.Setting[_]],
      val configurations: Seq[Configuration],
      val plugins: Plugins,
      val transforms: Seq[Project => Project],
      val defAxes: Seq[VirtualAxis],
  ) extends ProjectMatrix { self =>
    lazy val resolvedMappings: ListMap[ProjectRow, Project] = resolveMappings
    private def resolveProjectIds: Map[ProjectRow, String] = {
      Map((for {
        r <- rows
      } yield {
        val axes = r.axisValues.sortBy(_.suffixOrder)
          .filterNot(isSortOfDefaultAxis)
        val idSuffix = axes.map(_.idSuffix).mkString("")
        val childId = self.id + idSuffix
        r -> childId
      }): _*)
    }

    private def isSortOfDefaultAxis(a: VirtualAxis): Boolean =
      defAxes exists { da => VirtualAxis.isPartialVersionEquals(da, a) }

    private def resolveMappings: ListMap[ProjectRow, Project] = {
      val projectIds = resolveProjectIds

      ListMap((for {
        r <- rows
      } yield {
        val axes = r.axisValues.sortBy(_.suffixOrder)
        val svDirSuffix = axes.map(_.directorySuffix).mkString("-")
        val nonScalaDirSuffix = (axes filter {
          case _: VirtualAxis.ScalaVersionAxis => false
          case _                               => true
        }).map(_.directorySuffix).mkString("-")

        val platform = (axes collect {
          case pa: VirtualAxis.PlatformAxis => pa
        }).headOption.getOrElse(sys.error(s"platform axis is missing in $axes"))
        val childId = projectIds(r)
        val deps = dependencies map { resolveMatrixDependency(_, r) }
        val aggs = aggregate map {
          case ref: LocalProjectMatrix =>
            val other = lookupMatrix(ref)
            resolveMatrixAggregate(other, r)
        }
        val dotSbtMatrix = new java.io.File(".sbt") / "matrix"
        IO.createDirectory(dotSbtMatrix)
        val p = Project(childId, dotSbtMatrix / childId)
          .dependsOn(deps: _*)
          .aggregate(aggs: _*)
          .setPlugins(plugins)
          .configs(configurations: _*)
          .settings(
            name := self.id
          )
          .settings(
            r.scalaVersionOpt match {
              case Some(sv) =>
                List(Keys.scalaVersion := sv)
              case _ =>
                List(Keys.autoScalaLibrary := false, Keys.crossPaths := false)
            }
          )
          .settings(
            target := base.getAbsoluteFile / "target" / svDirSuffix.dropWhile(_ == '-'),
            crossTarget := Keys.target.value,
            sourceDirectory := base.getAbsoluteFile / "src",
            inConfig(Compile)(makeSources(nonScalaDirSuffix, svDirSuffix)),
            inConfig(Test)(makeSources(nonScalaDirSuffix, svDirSuffix)),
            projectDependencies := projectDependenciesTask.value,
            ProjectMatrixKeys.projectAxes := axes
          )
          .settings(self.settings)
          .configure(transforms: _*)

        r -> r.process(p)
      }): _*)
    }

  // backport of https://github.com/sbt/sbt/pull/5767
  def projectDependenciesTask: Def.Initialize[Task[Seq[ModuleID]]] =
    Def.task {
      val orig = projectDependencies.value
      val sbv = scalaBinaryVersion.value
      val ref = thisProjectRef.value
      val data = settingsData.value
      val deps = buildDependencies.value
      val sbtV = VersionNumber(sbtVersion.value)

      if (sbtV._1.getOrElse(0L) == 1 && (sbtV._2.getOrElse(0L) < 4)) {
        deps.classpath(ref) flatMap { dep =>
          val depProjIdOpt = (dep.project / projectID).get(data)
          val depSVOpt = (dep.project / scalaVersion).get(data)
          val depSBVOpt = (dep.project / scalaBinaryVersion).get(data)
          val depCrossOpt = (dep.project / crossVersion).get(data)
          (depProjIdOpt, depSVOpt, depSBVOpt, depCrossOpt) match {
            case (Some(depProjId), Some(depSV), Some(depSBV), Some(depCross)) =>
              if (sbv == depSBV || depCross != CrossVersion.binary)
                Some(
                  depProjId.withConfigurations(dep.configuration)
                    .withExplicitArtifacts(Vector.empty)
                )
              else if (VirtualAxis.isScala2Scala3Sandwich(sbv, depSBV) && depCross == CrossVersion.binary)
                Some(
                  depProjId
                    .withCrossVersion(CrossVersion.constant(depSBV))
                    .withConfigurations(dep.configuration)
                    .withExplicitArtifacts(Vector.empty)
                )
              else sys.error(s"scalaBinaryVersion mismatch: expected $sbv but found ${depSBV} in $depProjId")
            case _ => None
          }
        }
      } else {
        orig
      }
    }

    override lazy val componentProjects: Seq[Project] = resolvedMappings.values.toList

    private def resolveMatrixAggregate(
      other: ProjectMatrix,
      thisRow: ProjectRow,
    ): ProjectReference = other.resolveMatch(thisRow)

    private def resolveMatrixDependency(
      dep: MatrixClasspathDep[ProjectMatrixReference],
      thisRow: ProjectRow
    ): ClasspathDep[ProjectReference] =
      dep match {
        case MatrixClasspathDependency(matrix0: LocalProjectMatrix, configuration) =>
          val other = lookupMatrix(matrix0)
          ClasspathDependency(other.resolveMatch(thisRow), configuration)
      }

    // resolve to the closest match for the given row
    private[sbt] def resolveMatch(thatRow: ProjectRow): ProjectReference =
      (rows.find(r => r.isMatch(thatRow)) orElse
        rows.find(r => r.isSecondaryMatch(thatRow))) match {
        case Some(r) => LocalProject(resolveProjectIds(r))
        case _       => sys.error(s"no rows were found in $id matching $thatRow: $rows")
      }

    private def makeSources(dirSuffix: String, svDirSuffix: String): Setting[_] = {
      unmanagedSourceDirectories ++= Seq(
        scalaSource.value.getParentFile / s"scala${dirSuffix}",
        scalaSource.value.getParentFile / s"scala$svDirSuffix"
      )
    }

    override def withId(id: String): ProjectMatrix = copy(id = id)

    override def in(dir: sbt.File): ProjectMatrix = copy(base = dir)

    override def configs(cs: Configuration*): ProjectMatrix =
      copy(configurations = configurations ++ cs)

    override def aggregate(refs: ProjectMatrixReference*): ProjectMatrix =
      copy(aggregate = (aggregate: Seq[ProjectMatrixReference]) ++ refs)

    override def dependsOn(deps: MatrixClasspathDep[ProjectMatrixReference]*): ProjectMatrix =
      copy(dependencies = dependencies ++ deps)

    /** Appends settings to the current settings sequence for this project. */
    override def settings(ss: Def.SettingsDefinition*): ProjectMatrix =
      copy(settings = (settings: Seq[Def.Setting[_]]) ++ Def.settings(ss: _*))

    override def enablePlugins(ns: Plugins*): ProjectMatrix =
      setPlugins(ns.foldLeft(plugins)(Plugins.and))

    override def disablePlugins(ps: AutoPlugin*): ProjectMatrix =
      setPlugins(Plugins.and(plugins, Plugins.And(ps.map(p => Plugins.Exclude(p)).toList)))

    override def configure(ts: (Project => Project)*): ProjectMatrix =
      copy(transforms = transforms ++ ts)

    def setPlugins(ns: Plugins): ProjectMatrix = copy(plugins = ns)

    override def jvmPlatform(scalaVersions: Seq[String]): ProjectMatrix =
      jvmPlatform(scalaVersions, Nil)
    override def jvmPlatform(autoScalaLibrary: Boolean): ProjectMatrix =
      jvmPlatform(autoScalaLibrary, Nil, Nil)
    override def jvmPlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix =
      jvmPlatform(true, scalaVersions, settings)
    override def jvmPlatform(autoScalaLibrary: Boolean, scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix =
      customRow(autoScalaLibrary, scalaVersions, Seq(VirtualAxis.jvm), { _.settings(settings) })

    override def jvm: ProjectFinder = new AxisBaseProjectFinder(Seq(VirtualAxis.jvm))

    override def jsPlatform(scalaVersions: Seq[String]): ProjectMatrix =
      jsPlatform(scalaVersions, Nil)

    override def jsPlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix =
      customRow(true, scalaVersions, Seq(VirtualAxis.js),
        { _
            .enablePlugins(scalajsPlugin(this.getClass.getClassLoader).getOrElse(
              sys.error("""Scala.js plugin was not found. Add the sbt-scalajs plugin into project/plugins.sbt:
                          |  addSbtPlugin("org.scala-js" % "sbt-scalajs" % "x.y.z")
                          |""".stripMargin)
            ))
            .settings(settings)
        })

    override def defaultAxes(axes: VirtualAxis*): ProjectMatrix =
      copy(defAxes = axes.toSeq)

    def scalajsPlugin(classLoader: ClassLoader): Try[AutoPlugin] = {
      import sbtprojectmatrix.ReflectionUtil._
      withContextClassloader(classLoader) { loader =>
        getSingletonObject[AutoPlugin](loader, "org.scalajs.sbtplugin.ScalaJSPlugin$")
      }
    }

    override def js: ProjectFinder = new AxisBaseProjectFinder(Seq(VirtualAxis.js))

    override def native: ProjectFinder = new AxisBaseProjectFinder(Seq(VirtualAxis.native))

    override def nativePlatform(scalaVersions: Seq[String]): ProjectMatrix =
      nativePlatform(scalaVersions, Nil)

    override def nativePlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix =
      customRow(true, scalaVersions, Seq(VirtualAxis.native),
        { _
          .enablePlugins(nativePlugin(this.getClass.getClassLoader).getOrElse(
            sys.error("""Scala Native plugin was not found. Add the sbt-scala-native plugin into project/plugins.sbt:
                        |  addSbtPlugin("org.scala-native" % "sbt-scala-native" % "x.y.z")
                        |""".stripMargin)
          ))
          .settings(settings)
        })

    def nativePlugin(classLoader: ClassLoader): Try[AutoPlugin] = {
      import sbtprojectmatrix.ReflectionUtil._
      withContextClassloader(classLoader) { loader =>
        getSingletonObject[AutoPlugin](loader, "scala.scalanative.sbtplugin.ScalaNativePlugin$")
      }
    }

    override def projectRefs: Seq[ProjectReference] =
      componentProjects map { case p => (p: ProjectReference) }

    override def filterProjects(axisValues: Seq[VirtualAxis]): Seq[Project] =
      resolvedMappings.toSeq collect {
        case (r, p) if axisValues.forall(v => r.axisValues.contains(v)) => p
      }
    override def filterProjects(autoScalaLibrary: Boolean, axisValues: Seq[VirtualAxis]): Seq[Project] =
      resolvedMappings.toSeq collect {
        case (r, p) if r.autoScalaLibrary == autoScalaLibrary && axisValues.forall(v => r.axisValues.contains(v)) => p
      }

    private final class AxisBaseProjectFinder(axisValues: Seq[VirtualAxis]) extends ProjectFinder {
      def get: Seq[Project] = filterProjects(axisValues)
      def apply(sv: String): Project =
        filterProjects(true, axisValues ++ Seq(VirtualAxis.scalaPartialVersion(sv))).headOption
        .getOrElse(sys.error(s"project matching $axisValues and $sv was not found"))
      def apply(autoScalaLibrary: Boolean): Project =
        filterProjects(autoScalaLibrary, axisValues).headOption
        .getOrElse(sys.error(s"project matching $axisValues and $autoScalaLibrary was not found"))
    }

    override def customRow(
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      settings: Seq[Setting[_]]
    ): ProjectMatrix = customRow(true, scalaVersions, axisValues, { _.settings(settings) })

    override def customRow(
      autoScalaLibrary: Boolean,
      axisValues: Seq[VirtualAxis],
      settings: Seq[Setting[_]]
    ): ProjectMatrix = customRow(autoScalaLibrary, Nil, axisValues, { _.settings(settings) })

    override def customRow(
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      process: Project => Project
    ): ProjectMatrix = customRow(true, scalaVersions, axisValues, process)

    override def customRow(
      autoScalaLibrary: Boolean,
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      process: Project => Project
    ): ProjectMatrix =
      if (autoScalaLibrary) {
        scalaVersions.foldLeft(this: ProjectMatrix) { (acc, sv) =>
          acc.customRow(autoScalaLibrary, axisValues ++ Seq(VirtualAxis.scalaPartialVersion(sv)), process)
        }
      } else {
        customRow(autoScalaLibrary, Seq(VirtualAxis.jvm), process)
      }

    override def customRow(
      autoScalaLibrary: Boolean,
      axisValues: Seq[VirtualAxis],
      process: Project => Project
    ): ProjectMatrix = {
      val newRow: ProjectRow = new ProjectRow(autoScalaLibrary, axisValues, process)
      copy(rows = this.rows :+ newRow)
    }

    override def finder(axisValues: VirtualAxis*): ProjectFinder =
      new AxisBaseProjectFinder(axisValues.toSeq)

    def copy(
        id: String = id,
        base: sbt.File = base,
        scalaVersions: Seq[String] = scalaVersions,
        rows: Seq[ProjectRow] = rows,
        aggregate: Seq[ProjectMatrixReference] = aggregate,
        dependencies: Seq[MatrixClasspathDep[ProjectMatrixReference]] = dependencies,
        settings: Seq[Setting[_]] = settings,
        configurations: Seq[Configuration] = configurations,
        plugins: Plugins = plugins,
        transforms: Seq[Project => Project] = transforms,
        defAxes: Seq[VirtualAxis] = defAxes,
    ): ProjectMatrix = {
      val matrix = unresolved(
        id,
        base,
        scalaVersions,
        rows,
        aggregate,
        dependencies,
        settings,
        configurations,
        plugins,
        transforms,
        defAxes,
      )
      allMatrices(id) = matrix
      matrix
    }
  }

  // called by macro
  def apply(id: String, base: sbt.File): ProjectMatrix = {
    val defaultDefAxes = Seq(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion("2.13.3"))
    val matrix = unresolved(id, base, Nil, Nil, Nil, Nil, Nil, Nil, Plugins.Empty, Nil, defaultDefAxes)
    allMatrices(id) = matrix
    matrix
  }

  private[sbt] def unresolved(
      id: String,
      base: sbt.File,
      scalaVersions: Seq[String],
      rows: Seq[ProjectRow],
      aggregate: Seq[ProjectMatrixReference],
      dependencies: Seq[MatrixClasspathDep[ProjectMatrixReference]],
      settings: Seq[Def.Setting[_]],
      configurations: Seq[Configuration],
      plugins: Plugins,
      transforms: Seq[Project => Project],
      defAxes: Seq[VirtualAxis],
  ): ProjectMatrix =
    new ProjectMatrixDef(
      id,
      base,
      scalaVersions,
      rows,
      aggregate,
      dependencies,
      settings,
      configurations,
      plugins,
      transforms,
      defAxes,
    )

  def lookupMatrix(local: LocalProjectMatrix): ProjectMatrix = {
    allMatrices.getOrElse(local.id, sys.error(s"${local.id} was not found"))
  }

  implicit def projectMatrixToLocalProjectMatrix(m: ProjectMatrix): LocalProjectMatrix =
    LocalProjectMatrix(m.id)

  import scala.reflect.macros._

  def projectMatrixMacroImpl(c: blackbox.Context): c.Expr[ProjectMatrix] = {
    import c.universe._
    val enclosingValName = std.KeyMacro.definingValName(
      c,
      methodName =>
        s"""$methodName must be directly assigned to a val, such as `val x = $methodName`. Alternatively, you can use `sbt.ProjectMatrix.apply`"""
    )
    val name = c.Expr[String](Literal(Constant(enclosingValName)))
    reify { ProjectMatrix(name.splice, new sbt.File(name.splice)) }
  }
}
