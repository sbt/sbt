/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package coursier.lmcoursier
final class CoursierConfiguration private (
  val log: Option[xsbti.Logger],
  val resolvers: Vector[sbt.librarymanagement.Resolver],
  val reorderResolvers: Boolean,
  val parallelDownloads: Int,
  val maxIterations: Int,
  val sbtScalaOrganization: Option[String],
  val sbtScalaVersion: Option[String],
  val sbtScalaJars: Vector[java.io.File],
  val interProjectDependencies: Vector[coursier.core.Project],
  val excludeDependencies: Vector[(String, String)],
  val fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency],
  val autoScalaLibrary: Boolean) extends Serializable {
  
  private def this() = this(None, sbt.librarymanagement.Resolver.defaults, true, 6, 100, None, None, Vector.empty, Vector.empty, Vector.empty, Vector.empty, true)
  
  override def equals(o: Any): Boolean = o match {
    case x: CoursierConfiguration => (this.log == x.log) && (this.resolvers == x.resolvers) && (this.reorderResolvers == x.reorderResolvers) && (this.parallelDownloads == x.parallelDownloads) && (this.maxIterations == x.maxIterations) && (this.sbtScalaOrganization == x.sbtScalaOrganization) && (this.sbtScalaVersion == x.sbtScalaVersion) && (this.sbtScalaJars == x.sbtScalaJars) && (this.interProjectDependencies == x.interProjectDependencies) && (this.excludeDependencies == x.excludeDependencies) && (this.fallbackDependencies == x.fallbackDependencies) && (this.autoScalaLibrary == x.autoScalaLibrary)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.lmcoursier.CoursierConfiguration".##) + log.##) + resolvers.##) + reorderResolvers.##) + parallelDownloads.##) + maxIterations.##) + sbtScalaOrganization.##) + sbtScalaVersion.##) + sbtScalaJars.##) + interProjectDependencies.##) + excludeDependencies.##) + fallbackDependencies.##) + autoScalaLibrary.##)
  }
  override def toString: String = {
    "CoursierConfiguration(" + log + ", " + resolvers + ", " + reorderResolvers + ", " + parallelDownloads + ", " + maxIterations + ", " + sbtScalaOrganization + ", " + sbtScalaVersion + ", " + sbtScalaJars + ", " + interProjectDependencies + ", " + excludeDependencies + ", " + fallbackDependencies + ", " + autoScalaLibrary + ")"
  }
  private[this] def copy(log: Option[xsbti.Logger] = log, resolvers: Vector[sbt.librarymanagement.Resolver] = resolvers, reorderResolvers: Boolean = reorderResolvers, parallelDownloads: Int = parallelDownloads, maxIterations: Int = maxIterations, sbtScalaOrganization: Option[String] = sbtScalaOrganization, sbtScalaVersion: Option[String] = sbtScalaVersion, sbtScalaJars: Vector[java.io.File] = sbtScalaJars, interProjectDependencies: Vector[coursier.core.Project] = interProjectDependencies, excludeDependencies: Vector[(String, String)] = excludeDependencies, fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency] = fallbackDependencies, autoScalaLibrary: Boolean = autoScalaLibrary): CoursierConfiguration = {
    new CoursierConfiguration(log, resolvers, reorderResolvers, parallelDownloads, maxIterations, sbtScalaOrganization, sbtScalaVersion, sbtScalaJars, interProjectDependencies, excludeDependencies, fallbackDependencies, autoScalaLibrary)
  }
  def withLog(log: Option[xsbti.Logger]): CoursierConfiguration = {
    copy(log = log)
  }
  def withLog(log: xsbti.Logger): CoursierConfiguration = {
    copy(log = Option(log))
  }
  def withResolvers(resolvers: Vector[sbt.librarymanagement.Resolver]): CoursierConfiguration = {
    copy(resolvers = resolvers)
  }
  def withReorderResolvers(reorderResolvers: Boolean): CoursierConfiguration = {
    copy(reorderResolvers = reorderResolvers)
  }
  def withParallelDownloads(parallelDownloads: Int): CoursierConfiguration = {
    copy(parallelDownloads = parallelDownloads)
  }
  def withMaxIterations(maxIterations: Int): CoursierConfiguration = {
    copy(maxIterations = maxIterations)
  }
  def withSbtScalaOrganization(sbtScalaOrganization: Option[String]): CoursierConfiguration = {
    copy(sbtScalaOrganization = sbtScalaOrganization)
  }
  def withSbtScalaOrganization(sbtScalaOrganization: String): CoursierConfiguration = {
    copy(sbtScalaOrganization = Option(sbtScalaOrganization))
  }
  def withSbtScalaVersion(sbtScalaVersion: Option[String]): CoursierConfiguration = {
    copy(sbtScalaVersion = sbtScalaVersion)
  }
  def withSbtScalaVersion(sbtScalaVersion: String): CoursierConfiguration = {
    copy(sbtScalaVersion = Option(sbtScalaVersion))
  }
  def withSbtScalaJars(sbtScalaJars: Vector[java.io.File]): CoursierConfiguration = {
    copy(sbtScalaJars = sbtScalaJars)
  }
  def withInterProjectDependencies(interProjectDependencies: Vector[coursier.core.Project]): CoursierConfiguration = {
    copy(interProjectDependencies = interProjectDependencies)
  }
  def withExcludeDependencies(excludeDependencies: Vector[(String, String)]): CoursierConfiguration = {
    copy(excludeDependencies = excludeDependencies)
  }
  def withFallbackDependencies(fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency]): CoursierConfiguration = {
    copy(fallbackDependencies = fallbackDependencies)
  }
  def withAutoScalaLibrary(autoScalaLibrary: Boolean): CoursierConfiguration = {
    copy(autoScalaLibrary = autoScalaLibrary)
  }
}
object CoursierConfiguration {
  
  def apply(): CoursierConfiguration = new CoursierConfiguration()
  def apply(log: Option[xsbti.Logger], resolvers: Vector[sbt.librarymanagement.Resolver], reorderResolvers: Boolean, parallelDownloads: Int, maxIterations: Int, sbtScalaOrganization: Option[String], sbtScalaVersion: Option[String], sbtScalaJars: Vector[java.io.File], interProjectDependencies: Vector[coursier.core.Project], excludeDependencies: Vector[(String, String)], fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency], autoScalaLibrary: Boolean): CoursierConfiguration = new CoursierConfiguration(log, resolvers, reorderResolvers, parallelDownloads, maxIterations, sbtScalaOrganization, sbtScalaVersion, sbtScalaJars, interProjectDependencies, excludeDependencies, fallbackDependencies, autoScalaLibrary)
  def apply(log: xsbti.Logger, resolvers: Vector[sbt.librarymanagement.Resolver], reorderResolvers: Boolean, parallelDownloads: Int, maxIterations: Int, sbtScalaOrganization: String, sbtScalaVersion: String, sbtScalaJars: Vector[java.io.File], interProjectDependencies: Vector[coursier.core.Project], excludeDependencies: Vector[(String, String)], fallbackDependencies: Vector[coursier.lmcoursier.FallbackDependency], autoScalaLibrary: Boolean): CoursierConfiguration = new CoursierConfiguration(Option(log), resolvers, reorderResolvers, parallelDownloads, maxIterations, Option(sbtScalaOrganization), Option(sbtScalaVersion), sbtScalaJars, interProjectDependencies, excludeDependencies, fallbackDependencies, autoScalaLibrary)
}
