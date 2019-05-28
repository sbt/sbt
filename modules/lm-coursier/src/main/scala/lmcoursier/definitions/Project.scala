/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
final class Project private (
  val module: Module,
  val version: String,
  val dependencies: Seq[(Configuration, Dependency)],
  val configurations: Map[Configuration, Seq[Configuration]],
  val properties: Seq[(String, String)],
  val packagingOpt: Option[Type],
  val publications: Seq[(Configuration, Publication)],
  val info: Info) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Project => (this.module == x.module) && (this.version == x.version) && (this.dependencies == x.dependencies) && (this.configurations == x.configurations) && (this.properties == x.properties) && (this.packagingOpt == x.packagingOpt) && (this.publications == x.publications) && (this.info == x.info)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.Project".##) + module.##) + version.##) + dependencies.##) + configurations.##) + properties.##) + packagingOpt.##) + publications.##) + info.##)
  }
  override def toString: String = {
    "Project(" + module + ", " + version + ", " + dependencies + ", " + configurations + ", " + properties + ", " + packagingOpt + ", " + publications + ", " + info + ")"
  }
  private[this] def copy(module: Module = module, version: String = version, dependencies: Seq[(Configuration, Dependency)] = dependencies, configurations: Map[Configuration, Seq[Configuration]] = configurations, properties: Seq[(String, String)] = properties, packagingOpt: Option[Type] = packagingOpt, publications: Seq[(Configuration, Publication)] = publications, info: Info = info): Project = {
    new Project(module, version, dependencies, configurations, properties, packagingOpt, publications, info)
  }
  def withModule(module: Module): Project = {
    copy(module = module)
  }
  def withVersion(version: String): Project = {
    copy(version = version)
  }
  def withDependencies(dependencies: Seq[(Configuration, Dependency)]): Project = {
    copy(dependencies = dependencies)
  }
  def withConfigurations(configurations: Map[Configuration, Seq[Configuration]]): Project = {
    copy(configurations = configurations)
  }
  def withProperties(properties: Seq[(String, String)]): Project = {
    copy(properties = properties)
  }
  def withPackagingOpt(packagingOpt: Option[Type]): Project = {
    copy(packagingOpt = packagingOpt)
  }
  def withPublications(publications: Seq[(Configuration, Publication)]): Project = {
    copy(publications = publications)
  }
  def withInfo(info: Info): Project = {
    copy(info = info)
  }
}
object Project {
  
  def apply(module: Module, version: String, dependencies: Seq[(Configuration, Dependency)], configurations: Map[Configuration, Seq[Configuration]], properties: Seq[(String, String)], packagingOpt: Option[Type], publications: Seq[(Configuration, Publication)], info: Info): Project = new Project(module, version, dependencies, configurations, properties, packagingOpt, publications, info)
}
