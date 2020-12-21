/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Provides information about the resolution of a module.
 * This information is in the context of a specific configuration.
 */
final class ModuleReport private (
  val module: sbt.librarymanagement.ModuleID,
  val artifacts: Vector[scala.Tuple2[sbt.librarymanagement.Artifact, java.io.File]],
  val missingArtifacts: Vector[sbt.librarymanagement.Artifact],
  val status: Option[String],
  val publicationDate: Option[java.util.Calendar],
  val resolver: Option[String],
  val artifactResolver: Option[String],
  val evicted: Boolean,
  val evictedData: Option[String],
  val evictedReason: Option[String],
  val problem: Option[String],
  val homepage: Option[String],
  val extraAttributes: Map[String, String],
  val isDefault: Option[Boolean],
  val branch: Option[String],
  val configurations: Vector[sbt.librarymanagement.ConfigRef],
  val licenses: Vector[scala.Tuple2[String, Option[String]]],
  val callers: Vector[sbt.librarymanagement.Caller]) extends sbt.librarymanagement.ModuleReportExtra with Serializable {
  
  private def this(module: sbt.librarymanagement.ModuleID, artifacts: Vector[scala.Tuple2[sbt.librarymanagement.Artifact, java.io.File]], missingArtifacts: Vector[sbt.librarymanagement.Artifact]) = this(module, artifacts, missingArtifacts, None, None, None, None, false, None, None, None, None, Map.empty, None, None, Vector.empty, Vector.empty, Vector.empty)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ModuleReport => (this.module == x.module) && (this.artifacts == x.artifacts) && (this.missingArtifacts == x.missingArtifacts) && (this.status == x.status) && (this.publicationDate == x.publicationDate) && (this.resolver == x.resolver) && (this.artifactResolver == x.artifactResolver) && (this.evicted == x.evicted) && (this.evictedData == x.evictedData) && (this.evictedReason == x.evictedReason) && (this.problem == x.problem) && (this.homepage == x.homepage) && (this.extraAttributes == x.extraAttributes) && (this.isDefault == x.isDefault) && (this.branch == x.branch) && (this.configurations == x.configurations) && (this.licenses == x.licenses) && (this.callers == x.callers)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ModuleReport".##) + module.##) + artifacts.##) + missingArtifacts.##) + status.##) + publicationDate.##) + resolver.##) + artifactResolver.##) + evicted.##) + evictedData.##) + evictedReason.##) + problem.##) + homepage.##) + extraAttributes.##) + isDefault.##) + branch.##) + configurations.##) + licenses.##) + callers.##)
  }
  override def toString: String = {
    s"\t\t$module: " +
    (if (arts.size <= 1) "" else "\n\t\t\t") + arts.mkString("\n\t\t\t") + "\n"
  }
  private[this] def copy(module: sbt.librarymanagement.ModuleID = module, artifacts: Vector[scala.Tuple2[sbt.librarymanagement.Artifact, java.io.File]] = artifacts, missingArtifacts: Vector[sbt.librarymanagement.Artifact] = missingArtifacts, status: Option[String] = status, publicationDate: Option[java.util.Calendar] = publicationDate, resolver: Option[String] = resolver, artifactResolver: Option[String] = artifactResolver, evicted: Boolean = evicted, evictedData: Option[String] = evictedData, evictedReason: Option[String] = evictedReason, problem: Option[String] = problem, homepage: Option[String] = homepage, extraAttributes: Map[String, String] = extraAttributes, isDefault: Option[Boolean] = isDefault, branch: Option[String] = branch, configurations: Vector[sbt.librarymanagement.ConfigRef] = configurations, licenses: Vector[scala.Tuple2[String, Option[String]]] = licenses, callers: Vector[sbt.librarymanagement.Caller] = callers): ModuleReport = {
    new ModuleReport(module, artifacts, missingArtifacts, status, publicationDate, resolver, artifactResolver, evicted, evictedData, evictedReason, problem, homepage, extraAttributes, isDefault, branch, configurations, licenses, callers)
  }
  def withModule(module: sbt.librarymanagement.ModuleID): ModuleReport = {
    copy(module = module)
  }
  def withArtifacts(artifacts: Vector[scala.Tuple2[sbt.librarymanagement.Artifact, java.io.File]]): ModuleReport = {
    copy(artifacts = artifacts)
  }
  def withMissingArtifacts(missingArtifacts: Vector[sbt.librarymanagement.Artifact]): ModuleReport = {
    copy(missingArtifacts = missingArtifacts)
  }
  def withStatus(status: Option[String]): ModuleReport = {
    copy(status = status)
  }
  def withPublicationDate(publicationDate: Option[java.util.Calendar]): ModuleReport = {
    copy(publicationDate = publicationDate)
  }
  def withResolver(resolver: Option[String]): ModuleReport = {
    copy(resolver = resolver)
  }
  def withArtifactResolver(artifactResolver: Option[String]): ModuleReport = {
    copy(artifactResolver = artifactResolver)
  }
  def withEvicted(evicted: Boolean): ModuleReport = {
    copy(evicted = evicted)
  }
  def withEvictedData(evictedData: Option[String]): ModuleReport = {
    copy(evictedData = evictedData)
  }
  def withEvictedReason(evictedReason: Option[String]): ModuleReport = {
    copy(evictedReason = evictedReason)
  }
  def withProblem(problem: Option[String]): ModuleReport = {
    copy(problem = problem)
  }
  def withHomepage(homepage: Option[String]): ModuleReport = {
    copy(homepage = homepage)
  }
  def withExtraAttributes(extraAttributes: Map[String, String]): ModuleReport = {
    copy(extraAttributes = extraAttributes)
  }
  def withIsDefault(isDefault: Option[Boolean]): ModuleReport = {
    copy(isDefault = isDefault)
  }
  def withBranch(branch: Option[String]): ModuleReport = {
    copy(branch = branch)
  }
  def withConfigurations(configurations: Vector[sbt.librarymanagement.ConfigRef]): ModuleReport = {
    copy(configurations = configurations)
  }
  def withLicenses(licenses: Vector[scala.Tuple2[String, Option[String]]]): ModuleReport = {
    copy(licenses = licenses)
  }
  def withCallers(callers: Vector[sbt.librarymanagement.Caller]): ModuleReport = {
    copy(callers = callers)
  }
}
object ModuleReport {
  
  def apply(module: sbt.librarymanagement.ModuleID, artifacts: Vector[scala.Tuple2[sbt.librarymanagement.Artifact, java.io.File]], missingArtifacts: Vector[sbt.librarymanagement.Artifact]): ModuleReport = new ModuleReport(module, artifacts, missingArtifacts)
  def apply(module: sbt.librarymanagement.ModuleID, artifacts: Vector[scala.Tuple2[sbt.librarymanagement.Artifact, java.io.File]], missingArtifacts: Vector[sbt.librarymanagement.Artifact], status: Option[String], publicationDate: Option[java.util.Calendar], resolver: Option[String], artifactResolver: Option[String], evicted: Boolean, evictedData: Option[String], evictedReason: Option[String], problem: Option[String], homepage: Option[String], extraAttributes: Map[String, String], isDefault: Option[Boolean], branch: Option[String], configurations: Vector[sbt.librarymanagement.ConfigRef], licenses: Vector[scala.Tuple2[String, Option[String]]], callers: Vector[sbt.librarymanagement.Caller]): ModuleReport = new ModuleReport(module, artifacts, missingArtifacts, status, publicationDate, resolver, artifactResolver, evicted, evictedData, evictedReason, problem, homepage, extraAttributes, isDefault, branch, configurations, licenses, callers)
}
