/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package coursier.lmcoursier
final class CoursierConfiguration private (
  val log: Option[xsbti.Logger],
  val resolvers: Vector[sbt.librarymanagement.Resolver],
  val otherResolvers: Vector[sbt.librarymanagement.Resolver],
  val reorderResolvers: Boolean,
  val parallelDownloads: Int,
  val maxIterations: Int,
  val sbtScalaOrganization: Option[String],
  val sbtScalaVersion: Option[String],
  val sbtScalaJars: Vector[java.io.File],
  val interProjectDependencies: Vector[coursier.core.Project],
  val excludeDependencies: Vector[(String, String)]) extends Serializable {
  
  private def this() = this(None, sbt.librarymanagement.Resolver.defaults, Vector.empty, true, 6, 100, None, None, Vector.empty, Vector.empty, Vector.empty)
  
  override def equals(o: Any): Boolean = o match {
    case x: CoursierConfiguration => (this.log == x.log) && (this.resolvers == x.resolvers) && (this.otherResolvers == x.otherResolvers) && (this.reorderResolvers == x.reorderResolvers) && (this.parallelDownloads == x.parallelDownloads) && (this.maxIterations == x.maxIterations) && (this.sbtScalaOrganization == x.sbtScalaOrganization) && (this.sbtScalaVersion == x.sbtScalaVersion) && (this.sbtScalaJars == x.sbtScalaJars) && (this.interProjectDependencies == x.interProjectDependencies) && (this.excludeDependencies == x.excludeDependencies)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.lmcoursier.CoursierConfiguration".##) + log.##) + resolvers.##) + otherResolvers.##) + reorderResolvers.##) + parallelDownloads.##) + maxIterations.##) + sbtScalaOrganization.##) + sbtScalaVersion.##) + sbtScalaJars.##) + interProjectDependencies.##) + excludeDependencies.##)
  }
  override def toString: String = {
    "CoursierConfiguration(" + log + ", " + resolvers + ", " + otherResolvers + ", " + reorderResolvers + ", " + parallelDownloads + ", " + maxIterations + ", " + sbtScalaOrganization + ", " + sbtScalaVersion + ", " + sbtScalaJars + ", " + interProjectDependencies + ", " + excludeDependencies + ")"
  }
  private[this] def copy(log: Option[xsbti.Logger] = log, resolvers: Vector[sbt.librarymanagement.Resolver] = resolvers, otherResolvers: Vector[sbt.librarymanagement.Resolver] = otherResolvers, reorderResolvers: Boolean = reorderResolvers, parallelDownloads: Int = parallelDownloads, maxIterations: Int = maxIterations, sbtScalaOrganization: Option[String] = sbtScalaOrganization, sbtScalaVersion: Option[String] = sbtScalaVersion, sbtScalaJars: Vector[java.io.File] = sbtScalaJars, interProjectDependencies: Vector[coursier.core.Project] = interProjectDependencies, excludeDependencies: Vector[(String, String)] = excludeDependencies): CoursierConfiguration = {
    new CoursierConfiguration(log, resolvers, otherResolvers, reorderResolvers, parallelDownloads, maxIterations, sbtScalaOrganization, sbtScalaVersion, sbtScalaJars, interProjectDependencies, excludeDependencies)
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
  def withOtherResolvers(otherResolvers: Vector[sbt.librarymanagement.Resolver]): CoursierConfiguration = {
    copy(otherResolvers = otherResolvers)
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
}
object CoursierConfiguration {
  
  def apply(): CoursierConfiguration = new CoursierConfiguration()
  def apply(log: Option[xsbti.Logger], resolvers: Vector[sbt.librarymanagement.Resolver], otherResolvers: Vector[sbt.librarymanagement.Resolver], reorderResolvers: Boolean, parallelDownloads: Int, maxIterations: Int, sbtScalaOrganization: Option[String], sbtScalaVersion: Option[String], sbtScalaJars: Vector[java.io.File], interProjectDependencies: Vector[coursier.core.Project], excludeDependencies: Vector[(String, String)]): CoursierConfiguration = new CoursierConfiguration(log, resolvers, otherResolvers, reorderResolvers, parallelDownloads, maxIterations, sbtScalaOrganization, sbtScalaVersion, sbtScalaJars, interProjectDependencies, excludeDependencies)
  def apply(log: xsbti.Logger, resolvers: Vector[sbt.librarymanagement.Resolver], otherResolvers: Vector[sbt.librarymanagement.Resolver], reorderResolvers: Boolean, parallelDownloads: Int, maxIterations: Int, sbtScalaOrganization: String, sbtScalaVersion: String, sbtScalaJars: Vector[java.io.File], interProjectDependencies: Vector[coursier.core.Project], excludeDependencies: Vector[(String, String)]): CoursierConfiguration = new CoursierConfiguration(Option(log), resolvers, otherResolvers, reorderResolvers, parallelDownloads, maxIterations, Option(sbtScalaOrganization), Option(sbtScalaVersion), sbtScalaJars, interProjectDependencies, excludeDependencies)
}
