/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class MakePomConfiguration private (
  val file: Option[java.io.File],
  val moduleInfo: Option[sbt.librarymanagement.ModuleInfo],
  val configurations: Option[scala.Vector[sbt.librarymanagement.Configuration]],
  val extra: Option[scala.xml.NodeSeq],
  val process: scala.Function1[scala.xml.Node, scala.xml.Node],
  val filterRepositories: scala.Function1[sbt.librarymanagement.MavenRepository, Boolean],
  val allRepositories: Boolean,
  val includeTypes: Set[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: MakePomConfiguration => (this.file == x.file) && (this.moduleInfo == x.moduleInfo) && (this.configurations == x.configurations) && (this.extra == x.extra) && (this.process == x.process) && (this.filterRepositories == x.filterRepositories) && (this.allRepositories == x.allRepositories) && (this.includeTypes == x.includeTypes)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.MakePomConfiguration".##) + file.##) + moduleInfo.##) + configurations.##) + extra.##) + process.##) + filterRepositories.##) + allRepositories.##) + includeTypes.##)
  }
  override def toString: String = {
    "MakePomConfiguration(" + file + ", " + moduleInfo + ", " + configurations + ", " + extra + ", " + process + ", " + filterRepositories + ", " + allRepositories + ", " + includeTypes + ")"
  }
  private[this] def copy(file: Option[java.io.File] = file, moduleInfo: Option[sbt.librarymanagement.ModuleInfo] = moduleInfo, configurations: Option[scala.Vector[sbt.librarymanagement.Configuration]] = configurations, extra: Option[scala.xml.NodeSeq] = extra, process: scala.Function1[scala.xml.Node, scala.xml.Node] = process, filterRepositories: scala.Function1[sbt.librarymanagement.MavenRepository, Boolean] = filterRepositories, allRepositories: Boolean = allRepositories, includeTypes: Set[String] = includeTypes): MakePomConfiguration = {
    new MakePomConfiguration(file, moduleInfo, configurations, extra, process, filterRepositories, allRepositories, includeTypes)
  }
  def withFile(file: Option[java.io.File]): MakePomConfiguration = {
    copy(file = file)
  }
  def withFile(file: java.io.File): MakePomConfiguration = {
    copy(file = Option(file))
  }
  def withModuleInfo(moduleInfo: Option[sbt.librarymanagement.ModuleInfo]): MakePomConfiguration = {
    copy(moduleInfo = moduleInfo)
  }
  def withModuleInfo(moduleInfo: sbt.librarymanagement.ModuleInfo): MakePomConfiguration = {
    copy(moduleInfo = Option(moduleInfo))
  }
  def withConfigurations(configurations: Option[scala.Vector[sbt.librarymanagement.Configuration]]): MakePomConfiguration = {
    copy(configurations = configurations)
  }
  def withConfigurations(configurations: scala.Vector[sbt.librarymanagement.Configuration]): MakePomConfiguration = {
    copy(configurations = Option(configurations))
  }
  def withExtra(extra: Option[scala.xml.NodeSeq]): MakePomConfiguration = {
    copy(extra = extra)
  }
  def withExtra(extra: scala.xml.NodeSeq): MakePomConfiguration = {
    copy(extra = Option(extra))
  }
  def withProcess(process: scala.Function1[scala.xml.Node, scala.xml.Node]): MakePomConfiguration = {
    copy(process = process)
  }
  def withFilterRepositories(filterRepositories: scala.Function1[sbt.librarymanagement.MavenRepository, Boolean]): MakePomConfiguration = {
    copy(filterRepositories = filterRepositories)
  }
  def withAllRepositories(allRepositories: Boolean): MakePomConfiguration = {
    copy(allRepositories = allRepositories)
  }
  def withIncludeTypes(includeTypes: Set[String]): MakePomConfiguration = {
    copy(includeTypes = includeTypes)
  }
}
object MakePomConfiguration extends sbt.librarymanagement.MakePomConfigurationFunctions {
  
  def apply(file: Option[java.io.File], moduleInfo: Option[sbt.librarymanagement.ModuleInfo], configurations: Option[scala.Vector[sbt.librarymanagement.Configuration]], extra: Option[scala.xml.NodeSeq], process: scala.Function1[scala.xml.Node, scala.xml.Node], filterRepositories: scala.Function1[sbt.librarymanagement.MavenRepository, Boolean], allRepositories: Boolean, includeTypes: Set[String]): MakePomConfiguration = new MakePomConfiguration(file, moduleInfo, configurations, extra, process, filterRepositories, allRepositories, includeTypes)
  def apply(file: java.io.File, moduleInfo: sbt.librarymanagement.ModuleInfo, configurations: scala.Vector[sbt.librarymanagement.Configuration], extra: scala.xml.NodeSeq, process: scala.Function1[scala.xml.Node, scala.xml.Node], filterRepositories: scala.Function1[sbt.librarymanagement.MavenRepository, Boolean], allRepositories: Boolean, includeTypes: Set[String]): MakePomConfiguration = new MakePomConfiguration(Option(file), Option(moduleInfo), Option(configurations), Option(extra), process, filterRepositories, allRepositories, includeTypes)
}
