package sbt

import java.util.Locale
import scala.collection.immutable.ListMap
import Keys._
import sbt.librarymanagement.CrossVersion.partialVersion
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
   * Sets the [[AutoPlugin]]s of this project.
   * A [[AutoPlugin]] is a common label that is used by plugins to determine what settings, if any, to enable on a project.
   */
  def enablePlugins(ns: Plugins*): ProjectMatrix

  /** Disable the given plugins on this project. */
  def disablePlugins(ps: AutoPlugin*): ProjectMatrix

  def custom(
      idSuffix: String,
      directorySuffix: String,
      scalaVersions: Seq[String],
      process: Project => Project
  ): ProjectMatrix

  def jvmPlatform(scalaVersions: Seq[String]): ProjectMatrix
  def jvmPlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix
  def jvm: ProjectFinder

  def jsPlatform(scalaVersions: Seq[String]): ProjectMatrix
  def jsPlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix
  def js: ProjectFinder

  def crossLibrary(scalaVersions: Seq[String], suffix: String, settings: Seq[Setting[_]]): ProjectMatrix
  def crossLib(suffix: String): ProjectFinder

  def projectRefs: Seq[ProjectReference]
}

/** Represents a reference to a project matrix with an optional configuration string.
 */
sealed trait MatrixClasspathDep[MR <: ProjectMatrixReference] {
  def matrix: MR; def configuration: Option[String]
}

trait ProjectFinder {
  def apply(scalaVersion: String): Project
  def get: Seq[Project]
}

object ProjectMatrix {
  import sbt.io.syntax._

  val jvmIdSuffix: String = "JVM"
  val jvmDirectorySuffix: String = "-jvm"
  val jsIdSuffix: String = "JS"
  val jsDirectorySuffix: String = "-js"

  /** A row in the project matrix, typically representing a platform.
   */
  final class ProjectRow(
      val idSuffix: String,
      val directorySuffix: String,
      val scalaVersions: Seq[String],
      val process: Project => Project
  ) {}

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
      val plugins: Plugins
  ) extends ProjectMatrix { self =>
    lazy val projectMatrix: ListMap[(ProjectRow, String), Project] = {
      ListMap((for {
        r <- rows
        svs = if (r.scalaVersions.nonEmpty) r.scalaVersions
        else if (scalaVersions.nonEmpty) scalaVersions
        else sys.error(s"project matrix $id must specify scalaVersions.")
        sv <- svs
      } yield {
        val idSuffix = r.idSuffix + scalaVersionIdSuffix(sv)
        val svDirSuffix = r.directorySuffix + "-" + scalaVersionDirSuffix(sv)
        val childId = self.id + idSuffix
        val deps = dependencies map {
          case MatrixClasspathDependency(matrix: LocalProjectMatrix, configuration) =>
            ClasspathDependency(LocalProject(matrix.id + idSuffix), configuration)
        }
        val aggs = aggregate map {
          case ref: LocalProjectMatrix => LocalProject(ref.id + idSuffix)
        }
        val p = Project(childId, new sbt.File(childId).getAbsoluteFile)
          .dependsOn(deps: _*)
          .aggregate(aggs: _*)
          .setPlugins(plugins)
          .configs(configurations: _*)
          .settings(
            Keys.scalaVersion := sv,
            target := base.getAbsoluteFile / "target" / svDirSuffix.dropWhile(_ == '-'),
            crossTarget := Keys.target.value,
            sourceDirectory := base.getAbsoluteFile / "src",
            inConfig(Compile)(makeSources(r.directorySuffix, svDirSuffix)),
            inConfig(Test)(makeSources(r.directorySuffix, svDirSuffix))
          )
          .settings(self.settings)

        (r, sv) -> r.process(p)
      }): _*)
    }

    override lazy val componentProjects: Seq[Project] = projectMatrix.values.toList

    private def makeSources(dirSuffix: String, svDirSuffix: String): Setting[_] = {
      unmanagedSourceDirectories ++= Seq(
        scalaSource.value.getParentFile / s"scala${dirSuffix}",
        scalaSource.value.getParentFile / s"scala$svDirSuffix"
      )
    }

    private def scalaVersionIdSuffix(sv: String): String = {
      scalaVersionDirSuffix(sv).toLowerCase(Locale.ENGLISH).replaceAll("""\W+""", "_")
    }

    private def scalaVersionDirSuffix(sv: String): String =
      partialVersion(sv) match {
        case Some((m, n)) => s"$m.$n"
        case _            => sv
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

    def setPlugins(ns: Plugins): ProjectMatrix = copy(plugins = ns)

    override def jvmPlatform(scalaVersions: Seq[String]): ProjectMatrix =
      jvmPlatform(scalaVersions, Nil)
    override def jvmPlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix =
      custom(jvmIdSuffix, jvmDirectorySuffix, scalaVersions, { _.settings(settings) })

    override def jvm: ProjectFinder = new SuffixBaseProjectFinder(jvmIdSuffix)

    override def jsPlatform(scalaVersions: Seq[String]): ProjectMatrix =
      jsPlatform(scalaVersions, Nil)
    
    override def jsPlatform(scalaVersions: Seq[String], settings: Seq[Setting[_]]): ProjectMatrix =
      custom(jsIdSuffix, jsDirectorySuffix, scalaVersions,
        { _
            .enablePlugins(scalajsPlugin(this.getClass.getClassLoader).getOrElse(
              sys.error("""Scala.js plugin was not found. Add the sbt-scalajs plugin into project/plugins.sbt:
                          |  addSbtPlugin("org.scala-js" % "sbt-scalajs" % "x.y.z")
                          |""".stripMargin)
            ))
            .settings(settings)
        })

    def scalajsPlugin(classLoader: ClassLoader): Try[AutoPlugin] = {
      import sbtprojectmatrix.ReflectionUtil._
      withContextClassloader(classLoader) { loader =>
        getSingletonObject[AutoPlugin](loader, "org.scalajs.sbtplugin.ScalaJSPlugin$")
      }
    }

    override def js: ProjectFinder = new SuffixBaseProjectFinder(jsIdSuffix)

    override def crossLibrary(scalaVersions: Seq[String], suffix: String, settings: Seq[Setting[_]]): ProjectMatrix =
      custom(suffix.replaceAllLiterally(".", "_"),
        "-" + suffix.toLowerCase,
        scalaVersions,
        { _.settings(
          Seq(moduleName := name.value + "_" + suffix.toLowerCase) ++ settings
        ) })

    override def crossLib(suffix: String): ProjectFinder =
      new SuffixBaseProjectFinder(suffix.replaceAllLiterally(".", "_"))

    override def projectRefs: Seq[ProjectReference] =
      componentProjects map { case p => (p: ProjectReference) }

    private final class SuffixBaseProjectFinder(idSuffix: String) extends ProjectFinder {
      def get: Seq[Project] = projectMatrix.toSeq collect {
        case ((r, sv), v) if r.idSuffix == idSuffix => v
      }
      def apply(sv: String): Project =
        (projectMatrix.toSeq collectFirst {
          case ((r, `sv`), v) if r.idSuffix == idSuffix => v
        }).getOrElse(sys.error(s"$sv was not found"))
    }

    override def custom(
        idSuffix: String,
        directorySuffix: String,
        scalaVersions: Seq[String],
        process: Project => Project
    ): ProjectMatrix =
      copy(rows = rows :+ new ProjectRow(idSuffix, directorySuffix, scalaVersions, process))

    def copy(
        id: String = id,
        base: sbt.File = base,
        scalaVersions: Seq[String] = scalaVersions,
        rows: Seq[ProjectRow] = rows,
        aggregate: Seq[ProjectMatrixReference] = aggregate,
        dependencies: Seq[MatrixClasspathDep[ProjectMatrixReference]] = dependencies,
        settings: Seq[Setting[_]] = settings,
        configurations: Seq[Configuration] = configurations,
        plugins: Plugins = plugins
    ): ProjectMatrix =
      unresolved(
        id,
        base,
        scalaVersions,
        rows,
        aggregate,
        dependencies,
        settings,
        configurations,
        plugins
      )
  }

  def apply(id: String, base: sbt.File): ProjectMatrix = {
    unresolved(id, base, Nil, Nil, Nil, Nil, Nil, Nil, Plugins.Empty)
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
      plugins: Plugins
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
      plugins
    )

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
