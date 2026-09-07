package sbt

import sbt.*
import sbt.Keys.*
import sbt.io.IO
import sbt.librarymanagement.Configuration
import sbt.librarymanagement.Configurations.*
import sbt.librarymanagement.CrossVersion
import sbt.librarymanagement.ModuleID

import java.io.File
import java.lang.reflect.InvocationTargetException
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.quoted.*
import scala.reflect.ClassTag
import scala.util.Sorting
import scala.util.Try

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

  /** The base directory for the project matrix. */
  def base: File

  def withId(id: String): ProjectMatrix

  /** Sets the base directory for this project matrix. */
  infix def in(dir: File): ProjectMatrix

  /** Adds new configurations directly to this project.  To override an existing configuration, use `overrideConfigs`. */
  def configs(cs: Configuration*): ProjectMatrix

  /** Adds classpath dependencies on internal or external projects. */
  def dependsOn(deps: MatrixClasspathDep[ProjectMatrixReference]*): ProjectMatrix

  /** Adds classpath dependencies on internal or external non-matrix projects. */
  def dependsOn(deps: ClasspathDep[ProjectReference]*)(using
      dummyImplicit: DummyImplicit
  ): ProjectMatrix

  /**
   * Adds projects to be aggregated.  When a user requests a task to run on this project from the command line,
   * the task will also be run in aggregated projects.
   */
  def aggregate(refs: ProjectMatrixReference*): ProjectMatrix

  /**
   * Allows non-matrix projects to be aggregated in a matrix project.
   */
  def aggregate(refs: ProjectReference*)(using dummyImplicit: DummyImplicit): ProjectMatrix

  /** Appends settings to the current settings sequence for this project. */
  def settings(ss: Def.SettingsDefinition*): ProjectMatrix

  /**
   * Sets `crossProjectSources := true` for every row of this matrix.
   * @see [[sbt.Keys.crossProjectSources]] for more information.
   */
  def crossProjectSources: ProjectMatrix = settings(Keys.crossProjectSources := true)

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
      crossVersion: Option[CrossVersion],
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis]
  )(process: Project => Project): ProjectMatrix

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
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix

  def customRow(
      autoScalaLibrary: Boolean,
      axisValues: Seq[VirtualAxis],
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix

  def jvmPlatform(
      autoScalaLibrary: Boolean,
      crossVersion: CrossVersion,
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix
  def jvmPlatform(crossVersion: CrossVersion, scalaVersions: Seq[String]): ProjectMatrix
  def jvmPlatform(scalaVersions: Seq[String]): ProjectMatrix
  def jvmPlatform(autoScalaLibrary: Boolean): ProjectMatrix
  def jvmPlatform(autoScalaLibrary: Boolean, crossVersion: CrossVersion): ProjectMatrix
  def jvmPlatform(scalaVersions: Seq[String], settings: Seq[Def.Setting[?]]): ProjectMatrix
  def jvmPlatform(
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix
  def jvmPlatform(
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      configure: Project => Project
  ): ProjectMatrix
  def jvmPlatform(
      autoScalaLibrary: Boolean,
      scalaVersions: Seq[String],
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix
  def jvm: ProjectFinder

  def jsPlatform(
      autoScalaLibrary: Boolean,
      crossVersion: CrossVersion,
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix
  def jsPlatform(crossVersion: CrossVersion, scalaVersions: Seq[String]): ProjectMatrix
  def jsPlatform(scalaVersions: Seq[String]): ProjectMatrix
  def jsPlatform(scalaVersions: Seq[String], settings: Seq[Def.Setting[?]]): ProjectMatrix
  def jsPlatform(
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix
  def jsPlatform(
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      configure: Project => Project
  ): ProjectMatrix
  def js: ProjectFinder

  def nativePlatform(
      autoScalaLibrary: Boolean,
      crossVersion: CrossVersion,
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix
  def nativePlatform(crossVersion: CrossVersion, scalaVersions: Seq[String]): ProjectMatrix
  def nativePlatform(scalaVersions: Seq[String]): ProjectMatrix
  def nativePlatform(scalaVersions: Seq[String], settings: Seq[Def.Setting[?]]): ProjectMatrix
  def nativePlatform(
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      settings: Seq[Def.Setting[?]]
  ): ProjectMatrix
  def nativePlatform(
      scalaVersions: Seq[String],
      axisValues: Seq[VirtualAxis],
      configure: Project => Project
  ): ProjectMatrix
  def native: ProjectFinder

  def defaultAxes(axes: VirtualAxis*): ProjectMatrix

  def projectRefs: Seq[ProjectReference]

  def filterProjects(axisValues: Seq[VirtualAxis]): Seq[Project]
  def filterProjects(autoScalaLibrary: Boolean, axisValues: Seq[VirtualAxis]): Seq[Project]
  def finder(axisValues: VirtualAxis*): ProjectFinder
  def allProjects(): Seq[(Project, Seq[VirtualAxis])]

  // resolve to the closest match for the given row
  private[sbt] def resolveMatch(thatRow: ProjectMatrix.ProjectRow): ProjectReference
}

/**
 * Represents a reference to a project matrix with an optional configuration string.
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
  import sbt.io.syntax.*

  val jvmIdSuffix: String = "JVM"
  val jvmDirectorySuffix: String = "-jvm"
  val jsIdSuffix: String = "JS"
  val jsDirectorySuffix: String = "-js"
  val nativeIdSuffix: String = "Native"
  val nativeDirectorySuffix: String = "-native"

  private[sbt] val allMatrices: mutable.Map[String, ProjectMatrix] = mutable.Map.empty

  /**
   * A row in the project matrix, typically representing a platform + Scala version.
   */
  final class ProjectRow(
      val autoScalaLibrary: Boolean,
      val axisValues: Seq[VirtualAxis],
      val process: Project => Project
  ) {
    def scalaVersionOpt: Option[String] =
      if (autoScalaLibrary)
        axisValues collectFirst { case sv: VirtualAxis.ScalaVersionAxis =>
          sv.scalaVersion
        }
      else None

    def isMatch(that: ProjectRow): Boolean =
      VirtualAxis.isMatch(this.axisValues, that.axisValues)

    def isSecondaryMatch(that: ProjectRow): Boolean =
      VirtualAxis.isSecondaryMatch(this.axisValues, that.axisValues)

    /** Calculate the idSuffix for this row */
    def idSuffix(defAxes: Seq[VirtualAxis]): String = axisValues
      .sortBy(_.suffixOrder)
      .filterNot(isSortOfDefaultAxis(defAxes))
      .map(_.idSuffix)
      .mkString("")

    private def isSortOfDefaultAxis(defAxes: Seq[VirtualAxis])(a: VirtualAxis): Boolean =
      defAxes.exists: da =>
        VirtualAxis.isPartialVersionEquals(da, a)

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
      val base: File,
      val scalaVersions: Seq[String],
      val rows: Seq[ProjectRow],
      val aggregate: Seq[ProjectMatrixReference],
      val nonMatrixAggregate: Seq[ProjectReference],
      val dependencies: Seq[MatrixClasspathDep[ProjectMatrixReference]],
      val nonMatrixDependencies: Seq[ClasspathDep[ProjectReference]],
      val settings: Seq[Def.Setting[?]],
      val configurations: Seq[Configuration],
      val plugins: Plugins,
      val transforms: Seq[Project => Project],
      val defAxes: Seq[VirtualAxis],
      val pluginClassLoader: ClassLoader
  ) extends ProjectMatrix { self =>
    lazy val resolvedMappings: ListMap[ProjectRow, Project] = resolveMappings
    private def resolveProjectIds: Map[ProjectRow, String] = {
      Map((for {
        r <- rows
      } yield r -> (self.id + r.idSuffix(defAxes)))*)
    }

    private def resolveMappings: ListMap[ProjectRow, Project] = {
      val projectIds = resolveProjectIds
      val projects =
        rows.map { r =>
          val childId = projectIds(r)
          val deps = dependencies.map { resolveMatrixDependency(_, r) } ++ nonMatrixDependencies
          val aggs = aggregate.map { case ref: LocalProjectMatrix =>
            val other = lookupMatrix(ref)
            resolveMatrixAggregate(other, r)
          } ++ nonMatrixAggregate
          val dotSbtMatrix = new java.io.File(".sbt") / "matrix"
          IO.createDirectory(dotSbtMatrix)
          val p = Project(childId, dotSbtMatrix / childId)
            .dependsOn(deps*)
            .aggregate(aggs*)
            .setPlugins(plugins)
            .configs(configurations*)
            .settings(baseSettings ++ rowSettings(r) ++ self.settings)
            .configure(transforms*)

          r -> r.process(p)
        }
      ListMap(projects*)
    }

    override lazy val componentProjects: Seq[Project] = resolvedMappings.values.toList

    // backport of https://github.com/sbt/sbt/pull/5767
    lazy val projectDependenciesTask: Def.Initialize[Task[Seq[ModuleID]]] =
      Def.task {
        val orig = projectDependencies.value
        val sbv = scalaBinaryVersion.value
        val ref = thisProjectRef.value
        val data = settingsData.value
        val deps = buildDependencies.value
        deps.classpath(ref) flatMap { dep =>
          for {
            depProjId <- (dep.project / projectID).get(data)
            depSBV <- (dep.project / scalaBinaryVersion).get(data)
            depCross <- (dep.project / crossVersion).get(data)
          } yield {
            depCross match {
              case b: CrossVersion.Binary if VirtualAxis.isScala2Scala3Sandwich(sbv, depSBV) =>
                depProjId
                  .withCrossVersion(CrossVersion.constant(depSBV))
                  .withConfigurations(dep.configuration)
                  .withExplicitArtifacts(Vector.empty)
              case _ =>
                depProjId.withConfigurations(dep.configuration).withExplicitArtifacts(Vector.empty)
            }
          }
        }
      }

    private lazy val noScalaLibrary: Seq[Def.Setting[?]] =
      Seq(autoScalaLibrary := false, crossPaths := false)

    // Resolve the matrix base against the build root rather than using getAbsoluteFile,
    // which resolves against CWD and breaks source dependencies (issue #8971).
    private lazy val baseSettings: Seq[Def.Setting[?]] = Def.settings(
      name := self.id,
      projectMatrixBaseDirectory := IO.resolve((ThisBuild / baseDirectory).value, base),
      sourceDirectory := projectMatrixBaseDirectory.value / "src",
      unmanagedBase := projectMatrixBaseDirectory.value / "lib",
    )

    private def rowSettings(r: ProjectRow): Seq[Def.Setting[?]] =
      import VirtualAxis.*
      val axes = r.axisValues.sortBy(_.suffixOrder)
      def dirSuffix(axes: Seq[VirtualAxis]): String =
        axes.map(_.directorySuffix).filter(_.nonEmpty).mkString("-")
      val scalaDirSuffix = dirSuffix(axes)
      val nonScalaDirSuffix = dirSuffix(axes.filterNot(_.isInstanceOf[ScalaVersionAxis]))
      val nonScalaNorPlatformDirSuffix = dirSuffix(
        axes.filterNot(_.isInstanceOf[ScalaVersionAxis | PlatformAxis])
      )
      Def.settings(
        r.scalaVersionOpt match {
          case Some(sv) => Seq(scalaVersion := sv)
          case _        => noScalaLibrary
        },
        if nonScalaNorPlatformDirSuffix.nonEmpty then
          Seq(outputPath ~= (o => s"$o/$nonScalaNorPlatformDirSuffix"))
        else Seq.empty,
        ProjectExtra.inConfig(Compile)(makeSources(nonScalaDirSuffix, scalaDirSuffix)),
        ProjectExtra.inConfig(Test)(makeSources(nonScalaDirSuffix, scalaDirSuffix)),
        ProjectExtra.inConfig(Compile)(crossProjectSourceDirs(r, "main")),
        ProjectExtra.inConfig(Test)(crossProjectSourceDirs(r, "test")),
        virtualAxes := axes,
      )

    private def platformOf(r: ProjectRow): Option[String] =
      r.axisValues.collectFirst { case a: VirtualAxis.PlatformAxis => a.value }

    /**
     * The trees sbt reads for each platform this matrix builds for: the platform's own, `shared`,
     * and one per group of platforms that share code. A group of every platform is
     * left out, because `shared` is that group.
     *
     * Keyed by platform rather than computed per row, because every row of a platform reads the
     * same trees, and a lookup that came from `rows` cannot miss.
     */
    private lazy val crossProjectSourceSubdirs: Map[String, Seq[String]] = {
      val platforms = {
        val distinct = Set.newBuilder[String]
        rows.foreach(platformOf(_).foreach(distinct += _))
        distinct.result().toArray
      }
      Sorting.quickSort(platforms)
      val pend = platforms.length - 1
      val fullMask = (1 << pend) - 1 // will exclude it, corresponds to "shared"
      val map = Map.newBuilder[String, Seq[String]]
      var pidx = 0
      while (pidx < platforms.length) {
        val platform = platforms(pidx)
        val subdirs = Seq.newBuilder[String]
        subdirs += platform
        subdirs += "shared"
        var mask = 1 // at least one bit must be set
        while (mask < fullMask) {
          val subdir = new StringBuilder
          var i = 0
          // the bits are consumed from the bottom, so a zero `remmask` has no members left
          var remmask = mask
          while (i < pidx && remmask != 0) {
            if ((remmask & 1) != 0) subdir.append(platforms(i)).append('-')
            remmask >>>= 1
            i += 1
          }
          subdir.append(platform)
          while (i < pend && remmask != 0) {
            i += 1
            if ((remmask & 1) != 0) subdir.append('-').append(platforms(i))
            remmask >>>= 1
          }
          subdirs += subdir.result()
          mask += 1
        }
        map += platform -> subdirs.result()
        pidx += 1
      }
      map.result()
    }

    private def crossProjectEnabled =
      Def.setting(Keys.crossProjectSources.?.value.contains(true))

    private def crossProjectSourceDirs(r: ProjectRow, conf: String): Seq[Def.Setting[?]] = {
      val platformOpt = platformOf(r)
      val trees = platformOpt.fold(Seq.empty[String])(crossProjectSourceSubdirs)
      // the axis, not `scalaBinaryVersion`, so a row that names 3.9 gets a tree of its own
      val version = r.axisValues.collectFirst { case a: VirtualAxis.ScalaVersionAxis => a.value }
      Def.settings(
        unmanagedSourceDirectories ++= (platformOpt match {
          case Some(platform) if crossProjectEnabled.value =>
            val res = Seq.newBuilder[File]
            val dir = projectMatrixBaseDirectory.value
            def append(tree: String, subdir: String) = res += dir / tree / "src" / conf / subdir
            // the shared trees carry Scala alone; the platform's own tree gets Java too
            append(platform, "java")
            val variants = crossProjectScalaDirs(version, crossPaths.value)
            trees.foreach(tree => variants.foreach(append(tree, _)))
            res.result()
          case _ => Nil
        }),
        unmanagedResourceDirectories ++= {
          if !crossProjectEnabled.value then Nil
          else {
            val dir = projectMatrixBaseDirectory.value
            trees.map(dir / _ / "src" / conf / "resources")
          }
        },
      )
    }

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
          ClasspathDep.ClasspathDependency(other.resolveMatch(thisRow), configuration)
      }

    // resolve to the closest match for the given row
    private[sbt] def resolveMatch(thatRow: ProjectRow): ProjectReference =
      (rows.find(r => r.isMatch(thatRow)) orElse
        rows.find(r => r.isSecondaryMatch(thatRow))) match {
        case Some(r) => LocalProject(resolveProjectIds(r))
        case _       => sys.error(s"no rows were found in $id matching $thatRow: $rows")
      }

    private def makeSources(dirSuffix: String, svDirSuffix: String): Def.Setting[?] = {
      unmanagedSourceDirectories ++= Seq(
        scalaSource.value.getParentFile / s"scala${dirSuffix}",
        scalaSource.value.getParentFile / s"scala$svDirSuffix",
        javaSource.value.getParentFile / s"java${dirSuffix}"
      )
    }

    override def withId(id: String): ProjectMatrix = copy(id = id)

    override infix def in(dir: File): ProjectMatrix = copy(base = dir)

    override def configs(cs: Configuration*): ProjectMatrix =
      copy(configurations = configurations ++ cs)

    override def aggregate(refs: ProjectMatrixReference*): ProjectMatrix =
      copy(aggregate = (aggregate: Seq[ProjectMatrixReference]) ++ refs)

    override def aggregate(refs: ProjectReference*)(using
        dummyImplicit: DummyImplicit
    ): ProjectMatrix =
      copy(nonMatrixAggregate = (nonMatrixAggregate: Seq[ProjectReference]) ++ refs)

    override def dependsOn(deps: MatrixClasspathDep[ProjectMatrixReference]*): ProjectMatrix =
      copy(dependencies = dependencies ++ deps)

    override def dependsOn(deps: ClasspathDep[ProjectReference]*)(using
        dummyImplicit: DummyImplicit
    ) =
      copy(nonMatrixDependencies = nonMatrixDependencies ++ deps)

    /** Appends settings to the current settings sequence for this project. */
    override def settings(ss: Def.SettingsDefinition*): ProjectMatrix =
      copy(settings = (settings: Seq[Def.Setting[?]]) ++ Def.settings(ss*))

    override def enablePlugins(ns: Plugins*): ProjectMatrix =
      setPlugins(ns.foldLeft(plugins)(Plugins.overrideWith))

    override def disablePlugins(ps: AutoPlugin*): ProjectMatrix =
      if ps.isEmpty then this
      else
        setPlugins(
          Plugins.overrideWith(
            plugins,
            Plugins.And(ps.map(p => Plugins.Exclude(p)).toList)
          )
        )

    override def configure(ts: (Project => Project)*): ProjectMatrix =
      copy(transforms = transforms ++ ts)

    def setPlugins(ns: Plugins): ProjectMatrix = copy(plugins = ns)

    override def jvmPlatform(
        autoScalaLibrary: Boolean,
        crossVersion: CrossVersion,
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(autoScalaLibrary, Some(crossVersion), scalaVersions, VirtualAxis.jvm +: axisValues):
        p => p.settings(settings)

    override def jvmPlatform(
        autoScalaLibrary: Boolean,
        scalaVersions: Seq[String],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(autoScalaLibrary, crossVersion = None, scalaVersions, Seq(VirtualAxis.jvm)): p =>
        p.settings(settings)

    override def jvmPlatform(
        crossVersion: CrossVersion,
        scalaVersions: Seq[String]
    ): ProjectMatrix =
      jvmPlatform(autoScalaLibrary = true, crossVersion, scalaVersions, Nil, Nil)

    override def jvmPlatform(
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(
        autoScalaLibrary = true,
        crossVersion = None,
        scalaVersions,
        VirtualAxis.jvm +: axisValues
      ): p =>
        p.settings(settings)

    override def jvmPlatform(
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        configure: Project => Project
    ): ProjectMatrix =
      customRow(
        autoScalaLibrary = true,
        crossVersion = None,
        scalaVersions,
        VirtualAxis.jvm +: axisValues
      )(configure)

    override def jvmPlatform(scalaVersions: Seq[String]): ProjectMatrix =
      jvmPlatform(autoScalaLibrary = true, scalaVersions, Nil)

    override def jvmPlatform(autoScalaLibrary: Boolean): ProjectMatrix =
      jvmPlatform(autoScalaLibrary, Nil, Nil)

    override def jvmPlatform(autoScalaLibrary: Boolean, crossVersion: CrossVersion): ProjectMatrix =
      jvmPlatform(autoScalaLibrary, crossVersion, Nil, Nil, Nil)

    override def jvmPlatform(
        scalaVersions: Seq[String],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      jvmPlatform(autoScalaLibrary = true, scalaVersions, settings)

    override def jvm: ProjectFinder = new AxisBaseProjectFinder(Seq(VirtualAxis.jvm))

    private def enableScalaJSPlugin(project: Project): Project =
      project.enablePlugins(
        scalajsPlugin.getOrElse(
          sys.error(
            """Scala.js plugin was not found. Add the sbt-scalajs plugin into project/plugins.sbt:
                    |  addSbtPlugin("org.scala-js" % "sbt-scalajs" % "x.y.z")
                    |""".stripMargin
          )
        )
      )

    override def jsPlatform(
        autoScalaLibrary: Boolean,
        crossVersion: CrossVersion,
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(autoScalaLibrary, Some(crossVersion), scalaVersions, VirtualAxis.js +: axisValues):
        p => enableScalaJSPlugin(p).settings(settings)

    override def jsPlatform(
        scalaVersions: Seq[String],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(autoScalaLibrary = true, crossVersion = None, scalaVersions, Seq(VirtualAxis.js)):
        p => enableScalaJSPlugin(p).settings(settings)

    override def jsPlatform(crossVersion: CrossVersion, scalaVersions: Seq[String]): ProjectMatrix =
      jsPlatform(autoScalaLibrary = true, crossVersion, scalaVersions, Nil, Nil)

    override def jsPlatform(
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(
        autoScalaLibrary = true,
        crossVersion = None,
        scalaVersions,
        VirtualAxis.js +: axisValues
      ): p =>
        enableScalaJSPlugin(p).settings(settings)

    override def jsPlatform(
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        configure: Project => Project
    ): ProjectMatrix =
      customRow(
        autoScalaLibrary = true,
        crossVersion = None,
        scalaVersions,
        VirtualAxis.js +: axisValues
      ): p =>
        configure(enableScalaJSPlugin(p))

    override def jsPlatform(scalaVersions: Seq[String]): ProjectMatrix =
      jsPlatform(scalaVersions, Nil)

    override def defaultAxes(axes: VirtualAxis*): ProjectMatrix =
      copy(defAxes = axes)

    def scalajsPlugin: Try[AutoPlugin] = {
      import ReflectionUtil.*
      withContextClassloader(pluginClassLoader) { loader =>
        getSingletonObject[AutoPlugin](loader, "org.scalajs.sbtplugin.ScalaJSPlugin$")
      }
    }

    override def js: ProjectFinder = new AxisBaseProjectFinder(Seq(VirtualAxis.js))

    override def native: ProjectFinder = new AxisBaseProjectFinder(Seq(VirtualAxis.native))

    private def enableScalaNativePlugin(project: Project): Project =
      project.enablePlugins(
        nativePlugin.getOrElse(
          sys.error(
            """Scala Native plugin was not found. Add the sbt-scala-native plugin into project/plugins.sbt:
                    |  addSbtPlugin("org.scala-native" % "sbt-scala-native" % "x.y.z")
                    |""".stripMargin
          )
        )
      )

    override def nativePlatform(
        autoScalaLibrary: Boolean,
        crossVersion: CrossVersion,
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(
        autoScalaLibrary,
        Some(crossVersion),
        scalaVersions,
        VirtualAxis.native +: axisValues
      ): p =>
        enableScalaNativePlugin(p).settings(settings)

    override def nativePlatform(
        scalaVersions: Seq[String],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(
        autoScalaLibrary = true,
        crossVersion = None,
        scalaVersions,
        Seq(VirtualAxis.native)
      ): p =>
        enableScalaNativePlugin(p).settings(settings)

    override def nativePlatform(
        crossVersion: CrossVersion,
        scalaVersions: Seq[String]
    ): ProjectMatrix =
      nativePlatform(autoScalaLibrary = true, crossVersion, scalaVersions, Nil, Nil)

    override def nativePlatform(
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(
        autoScalaLibrary = true,
        crossVersion = None,
        scalaVersions,
        VirtualAxis.native +: axisValues
      ): p =>
        enableScalaNativePlugin(p).settings(settings)

    override def nativePlatform(
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        configure: Project => Project
    ): ProjectMatrix =
      customRow(
        autoScalaLibrary = true,
        crossVersion = None,
        scalaVersions,
        VirtualAxis.native +: axisValues
      ): p =>
        configure(enableScalaNativePlugin(p))

    override def nativePlatform(scalaVersions: Seq[String]): ProjectMatrix =
      nativePlatform(scalaVersions, Nil)

    def nativePlugin: Try[AutoPlugin] = {
      import ReflectionUtil.*
      withContextClassloader(pluginClassLoader) { loader =>
        getSingletonObject[AutoPlugin](loader, "scala.scalanative.sbtplugin.ScalaNativePlugin$")
      }
    }

    override def projectRefs: Seq[ProjectReference] = componentProjects.map(p => LocalProject(p.id))

    override def filterProjects(axisValues: Seq[VirtualAxis]): Seq[Project] =
      resolvedMappings.toSeq collect {
        case (r, p) if axisValues.forall(v => r.axisValues.contains(v)) => p
      }
    override def filterProjects(
        autoScalaLibrary: Boolean,
        axisValues: Seq[VirtualAxis]
    ): Seq[Project] =
      resolvedMappings.toSeq collect {
        case (r, p)
            if r.autoScalaLibrary == autoScalaLibrary && axisValues
              .forall(v => r.axisValues.contains(v)) =>
          p
      }

    private final class AxisBaseProjectFinder(axisValues: Seq[VirtualAxis]) extends ProjectFinder {
      def get: Seq[Project] = filterProjects(axisValues)
      def apply(sv: String): Project =
        filterProjects(true, axisValues ++ Seq(VirtualAxis.scalaABIVersion(sv))).headOption
          .getOrElse(sys.error(s"project matching $axisValues and $sv was not found"))
      def apply(autoScalaLibrary: Boolean): Project =
        filterProjects(autoScalaLibrary, axisValues).headOption
          .getOrElse(sys.error(s"project matching $axisValues and $autoScalaLibrary was not found"))
    }

    /**
     * If autoScalaLibrary is false, add non-Scala row.
     * Otherwise, add custom rows for each scalaVersions.
     */
    override def customRow(
        autoScalaLibrary: Boolean,
        crossVersion: Option[CrossVersion],
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis]
    )(
        process: Project => Project
    ): ProjectMatrix =
      val process1 = crossVersion match
        case Some(cv) => (p: Project) => process(p.settings(Keys.crossVersion := cv))
        case None     => process
      if autoScalaLibrary then
        scalaVersions.foldLeft(this: ProjectMatrix): (acc, sv) =>
          val scalaAxis =
            if crossVersion == Some(CrossVersion.full) then VirtualAxis.scalaVersionAxis(sv, sv)
            else VirtualAxis.scalaABIVersion(sv)
          acc.customRow(autoScalaLibrary, axisValues ++ Seq(scalaAxis), process1)
      else customRow(autoScalaLibrary, axisValues ++ Seq(VirtualAxis.jvm), process1)

    override def customRow(
        autoScalaLibrary: Boolean,
        axisValues: Seq[VirtualAxis],
        process: Project => Project
    ): ProjectMatrix =
      val newRow: ProjectRow = ProjectRow(autoScalaLibrary, axisValues, process)
      copy(rows = this.rows :+ newRow)

    override def customRow(
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(autoScalaLibrary = true, crossVersion = None, scalaVersions, axisValues): p =>
        p.settings(settings)

    override def customRow(
        autoScalaLibrary: Boolean,
        axisValues: Seq[VirtualAxis],
        settings: Seq[Def.Setting[?]]
    ): ProjectMatrix =
      customRow(autoScalaLibrary, crossVersion = None, Nil, axisValues): (p) =>
        p.settings(settings)

    override def customRow(
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        process: Project => Project
    ): ProjectMatrix =
      customRow(autoScalaLibrary = true, crossVersion = None, scalaVersions, axisValues)(process)

    override def customRow(
        autoScalaLibrary: Boolean,
        scalaVersions: Seq[String],
        axisValues: Seq[VirtualAxis],
        process: Project => Project
    ): ProjectMatrix =
      customRow(autoScalaLibrary, crossVersion = None, scalaVersions, axisValues)(process)

    override def finder(axisValues: VirtualAxis*): ProjectFinder =
      new AxisBaseProjectFinder(axisValues)

    override def allProjects(): Seq[(Project, Seq[VirtualAxis])] =
      resolvedMappings.map { (row, project) =>
        project -> row.axisValues
      }.toSeq

    def copy(
        id: String = id,
        base: File = base,
        scalaVersions: Seq[String] = scalaVersions,
        rows: Seq[ProjectRow] = rows,
        aggregate: Seq[ProjectMatrixReference] = aggregate,
        nonMatrixAggregate: Seq[ProjectReference] = nonMatrixAggregate,
        dependencies: Seq[MatrixClasspathDep[ProjectMatrixReference]] = dependencies,
        nonMatrixDependencies: Seq[ClasspathDep[ProjectReference]] = nonMatrixDependencies,
        settings: Seq[Def.Setting[?]] = settings,
        configurations: Seq[Configuration] = configurations,
        plugins: Plugins = plugins,
        transforms: Seq[Project => Project] = transforms,
        defAxes: Seq[VirtualAxis] = defAxes,
        pluginClassLoader: ClassLoader = pluginClassLoader,
    ): ProjectMatrix = {
      val matrix = unresolved(
        id,
        base,
        scalaVersions,
        rows,
        aggregate,
        nonMatrixAggregate,
        dependencies,
        nonMatrixDependencies,
        settings,
        configurations,
        plugins,
        transforms,
        defAxes,
        pluginClassLoader
      )
      allMatrices(id) = matrix
      matrix
    }
  }

  // called by macro
  def apply(id: String, base: File, pluginClassLoader: ClassLoader): ProjectMatrix = {
    val defaultDefAxes = Seq(VirtualAxis.jvm, VirtualAxis.scalaABIVersion("3.3.3"))
    val matrix = unresolved(
      id,
      base,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Plugins.Empty,
      Nil,
      defaultDefAxes,
      pluginClassLoader
    )
    allMatrices(id) = matrix
    matrix
  }

  /** `scala-2.13.18`, then every shorter prefix of it, then `scala`. */
  private def crossProjectScalaDirs(version: Option[String], crossPaths: Boolean): List[String] =
    version match {
      case Some(v) if crossPaths =>
        val res = List.newBuilder[String]
        var end = v.length
        while (end > 0) {
          res += s"scala-${v.substring(0, end)}"
          end = v.lastIndexOf('.', end - 1)
        }
        res += "scala"
        res.result()
      case _ => "scala" :: Nil
    }

  private[sbt] def unresolved(
      id: String,
      base: File,
      scalaVersions: Seq[String],
      rows: Seq[ProjectRow],
      aggregate: Seq[ProjectMatrixReference],
      nonMatrixAggregate: Seq[ProjectReference],
      dependencies: Seq[MatrixClasspathDep[ProjectMatrixReference]],
      nonMatrixDependencies: Seq[ClasspathDep[ProjectReference]],
      settings: Seq[Def.Setting[?]],
      configurations: Seq[Configuration],
      plugins: Plugins,
      transforms: Seq[Project => Project],
      defAxes: Seq[VirtualAxis],
      pluginClassLoader: ClassLoader
  ): ProjectMatrix =
    new ProjectMatrixDef(
      id,
      base,
      scalaVersions,
      rows,
      aggregate,
      nonMatrixAggregate,
      dependencies,
      nonMatrixDependencies,
      settings,
      configurations,
      plugins,
      transforms,
      defAxes,
      pluginClassLoader
    )

  def lookupMatrix(local: LocalProjectMatrix): ProjectMatrix = {
    allMatrices.getOrElse(local.id, sys.error(s"${local.id} was not found"))
  }

  implicit def projectMatrixToLocalProjectMatrix(m: ProjectMatrix): LocalProjectMatrix =
    LocalProjectMatrix(m.id)

  private object ReflectionUtil:
    def getSingletonObject[A: ClassTag](classLoader: ClassLoader, className: String): Try[A] =
      Try {
        val clazz = classLoader.loadClass(className)
        val t = implicitly[ClassTag[A]].runtimeClass
        Option(clazz.getField("MODULE$").get(null)) match {
          case None =>
            throw new ClassNotFoundException(
              s"Unable to find $className using classloader: $classLoader"
            )
          case Some(c) if !t.isInstance(c) =>
            throw new ClassCastException(s"${clazz.getName} is not a subtype of $t")
          case Some(c) => c.asInstanceOf[A]
        }
      }
        .recover {
          case i: InvocationTargetException if i.getTargetException != null =>
            throw i.getTargetException
        }

    def objectExists(classLoader: ClassLoader, className: String): Boolean =
      try {
        classLoader.loadClass(className).getField("MODULE$").get(null) != null
      } catch {
        case _: Throwable => false
      }

    def withContextClassloader[A](loader: ClassLoader)(body: ClassLoader => A): A = {
      val current = Thread.currentThread().getContextClassLoader
      try {
        Thread.currentThread().setContextClassLoader(loader)
        body(loader)
      } finally Thread.currentThread().setContextClassLoader(current)
    }
  end ReflectionUtil

  def projectMatrixImpl(using Quotes): Expr[ProjectMatrix] = {
    val name = std.KeyMacro.definingValName(
      "projectMatrix must be directly assigned to a val, such as `val x = projectMatrix`. Alternatively, you can use `sbt.ProjectMatrix.apply`"
    )
    val callerThis = std.KeyMacro.callerThis
    '{ ProjectMatrix($name, new File($name), $callerThis.getClass.getClassLoader) }
  }
}

trait ProjectMatrixExtra:
  given Conversion[ProjectMatrix, LocalProjectMatrix] =
    m => LocalProjectMatrix(m.id)

  given [A](using
      Conversion[A, ProjectMatrixReference]
  ): Conversion[A, ProjectMatrix.MatrixClasspathDependency] =
    ref => ProjectMatrix.MatrixClasspathDependency(ref, None)

  given [A](using
      Conversion[A, ProjectMatrixReference]
  ): Conversion[A, ProjectMatrix.ProjectMatrixReferenceSyntax] =
    ref => ProjectMatrix.ProjectMatrixReferenceSyntax(ref)
