/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class RequestedInputs private (
  val dependencies: Vector[lmcoursier.internal.RequestedDependency],
  val repositories: Vector[String],
  val scalaVersion: Option[String],
  val maxIterations: Int,
  val forceVersions: Vector[lmcoursier.internal.RequestedForceVersion],
  val exclusions: Vector[lmcoursier.internal.RequestedExclusion],
  val strict: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RequestedInputs => (this.dependencies == x.dependencies) && (this.repositories == x.repositories) && (this.scalaVersion == x.scalaVersion) && (this.maxIterations == x.maxIterations) && (this.forceVersions == x.forceVersions) && (this.exclusions == x.exclusions) && (this.strict == x.strict)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.internal.RequestedInputs".##) + dependencies.##) + repositories.##) + scalaVersion.##) + maxIterations.##) + forceVersions.##) + exclusions.##) + strict.##)
  }
  override def toString: String = {
    "RequestedInputs(" + dependencies + ", " + repositories + ", " + scalaVersion + ", " + maxIterations + ", " + forceVersions + ", " + exclusions + ", " + strict + ")"
  }
  private def copy(dependencies: Vector[lmcoursier.internal.RequestedDependency] = dependencies, repositories: Vector[String] = repositories, scalaVersion: Option[String] = scalaVersion, maxIterations: Int = maxIterations, forceVersions: Vector[lmcoursier.internal.RequestedForceVersion] = forceVersions, exclusions: Vector[lmcoursier.internal.RequestedExclusion] = exclusions, strict: Option[String] = strict): RequestedInputs = {
    new RequestedInputs(dependencies, repositories, scalaVersion, maxIterations, forceVersions, exclusions, strict)
  }
  def withDependencies(dependencies: Vector[lmcoursier.internal.RequestedDependency]): RequestedInputs = {
    copy(dependencies = dependencies)
  }
  def withRepositories(repositories: Vector[String]): RequestedInputs = {
    copy(repositories = repositories)
  }
  def withScalaVersion(scalaVersion: Option[String]): RequestedInputs = {
    copy(scalaVersion = scalaVersion)
  }
  def withScalaVersion(scalaVersion: String): RequestedInputs = {
    copy(scalaVersion = Option(scalaVersion))
  }
  def withMaxIterations(maxIterations: Int): RequestedInputs = {
    copy(maxIterations = maxIterations)
  }
  def withForceVersions(forceVersions: Vector[lmcoursier.internal.RequestedForceVersion]): RequestedInputs = {
    copy(forceVersions = forceVersions)
  }
  def withExclusions(exclusions: Vector[lmcoursier.internal.RequestedExclusion]): RequestedInputs = {
    copy(exclusions = exclusions)
  }
  def withStrict(strict: Option[String]): RequestedInputs = {
    copy(strict = strict)
  }
  def withStrict(strict: String): RequestedInputs = {
    copy(strict = Option(strict))
  }
}
object RequestedInputs {
  
  def apply(dependencies: Vector[lmcoursier.internal.RequestedDependency], repositories: Vector[String], scalaVersion: Option[String], maxIterations: Int, forceVersions: Vector[lmcoursier.internal.RequestedForceVersion], exclusions: Vector[lmcoursier.internal.RequestedExclusion], strict: Option[String]): RequestedInputs = new RequestedInputs(dependencies, repositories, scalaVersion, maxIterations, forceVersions, exclusions, strict)
  def apply(dependencies: Vector[lmcoursier.internal.RequestedDependency], repositories: Vector[String], scalaVersion: String, maxIterations: Int, forceVersions: Vector[lmcoursier.internal.RequestedForceVersion], exclusions: Vector[lmcoursier.internal.RequestedExclusion], strict: String): RequestedInputs = new RequestedInputs(dependencies, repositories, Option(scalaVersion), maxIterations, forceVersions, exclusions, Option(strict))
}
