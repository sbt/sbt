package sbt.librarymanagement

import java.io.File
import scala.xml.{ Node => XNode, NodeSeq }

/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */
final class MakePomConfiguration private (val file: Option[File],
                                          val moduleInfo: Option[ModuleInfo],
                                          val configurations: Option[Vector[Configuration]],
                                          val extra: Option[NodeSeq],
                                          val process: XNode => XNode,
                                          val filterRepositories: MavenRepository => Boolean,
                                          val allRepositories: Boolean,
                                          val includeTypes: Set[String])
    extends Serializable {
  private def this() =
    this(None,
         None,
         None,
         None,
         identity,
         MakePomConfiguration.constTrue,
         true,
         Set(Artifact.DefaultType, Artifact.PomType))

  override def equals(o: Any): Boolean = o match {
    case x: MakePomConfiguration =>
      (this.file == x.file) && (this.moduleInfo == x.moduleInfo) && (this.configurations == x.configurations) && (this.extra == x.extra) && (this.process == x.process) && (this.filterRepositories == x.filterRepositories) && (this.allRepositories == x.allRepositories) && (this.includeTypes == x.includeTypes)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.MakePomConfiguration".##) + file.##) + moduleInfo.##) + configurations.##) + extra.##) + process.##) + filterRepositories.##) + allRepositories.##) + includeTypes.##)
  }
  override def toString: String = {
    "MakePomConfiguration(" + file + ", " + moduleInfo + ", " + configurations + ", " + extra + ", " + process + ", " + filterRepositories + ", " + allRepositories + ", " + includeTypes + ")"
  }
  protected[this] def copy(file: Option[File] = file,
                           moduleInfo: Option[ModuleInfo] = moduleInfo,
                           configurations: Option[Vector[Configuration]] = configurations,
                           extra: Option[NodeSeq] = extra,
                           process: XNode => XNode = process,
                           filterRepositories: MavenRepository => Boolean = filterRepositories,
                           allRepositories: Boolean = allRepositories,
                           includeTypes: Set[String] = includeTypes): MakePomConfiguration = {
    new MakePomConfiguration(file,
                             moduleInfo,
                             configurations,
                             extra,
                             process,
                             filterRepositories,
                             allRepositories,
                             includeTypes)
  }
  def withFile(file: Option[File]): MakePomConfiguration = {
    copy(file = file)
  }
  def withFile(file: File): MakePomConfiguration = {
    copy(file = Option(file))
  }
  def withModuleInfo(moduleInfo: Option[ModuleInfo]): MakePomConfiguration = {
    copy(moduleInfo = moduleInfo)
  }
  def withModuleInfo(moduleInfo: ModuleInfo): MakePomConfiguration = {
    copy(moduleInfo = Option(moduleInfo))
  }
  def withConfigurations(configurations: Option[Vector[Configuration]]): MakePomConfiguration = {
    copy(configurations = configurations)
  }
  def withExtra(extra: Option[NodeSeq]): MakePomConfiguration = {
    copy(extra = extra)
  }
  def withExtra(extra: NodeSeq): MakePomConfiguration = {
    copy(extra = Option(extra))
  }
  def withProcess(process: XNode => XNode): MakePomConfiguration = {
    copy(process = process)
  }
  def withFilterRepositories(
      filterRepositories: MavenRepository => Boolean): MakePomConfiguration = {
    copy(filterRepositories = filterRepositories)
  }
  def withAllRepositories(allRepositories: Boolean): MakePomConfiguration = {
    copy(allRepositories = allRepositories)
  }
  def withIncludeTypes(includeTypes: Set[String]): MakePomConfiguration = {
    copy(includeTypes = includeTypes)
  }
}

object MakePomConfiguration {
  private[sbt] lazy val constTrue: MavenRepository => Boolean = _ => true

  def apply(): MakePomConfiguration =
    new MakePomConfiguration(None,
                             None,
                             None,
                             None,
                             identity,
                             constTrue,
                             true,
                             Set(Artifact.DefaultType, Artifact.PomType))
  def apply(file: Option[File],
            moduleInfo: Option[ModuleInfo],
            configurations: Option[Vector[Configuration]],
            extra: Option[NodeSeq],
            process: XNode => XNode,
            filterRepositories: MavenRepository => Boolean,
            allRepositories: Boolean,
            includeTypes: Set[String]): MakePomConfiguration =
    new MakePomConfiguration(file,
                             moduleInfo,
                             configurations,
                             extra,
                             process,
                             filterRepositories,
                             allRepositories,
                             includeTypes)
  def apply(file: File,
            moduleInfo: ModuleInfo,
            configurations: Vector[Configuration],
            extra: NodeSeq,
            process: XNode => XNode,
            filterRepositories: MavenRepository => Boolean,
            allRepositories: Boolean,
            includeTypes: Set[String]): MakePomConfiguration =
    new MakePomConfiguration(Option(file),
                             Option(moduleInfo),
                             Option(configurations),
                             Option(extra),
                             process,
                             filterRepositories,
                             allRepositories,
                             includeTypes)
}
