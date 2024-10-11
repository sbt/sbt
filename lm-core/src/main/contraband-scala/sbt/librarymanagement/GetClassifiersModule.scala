/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class GetClassifiersModule private (
  val id: sbt.librarymanagement.ModuleID,
  val scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo],
  val dependencies: Vector[sbt.librarymanagement.ModuleID],
  val configurations: Vector[sbt.librarymanagement.Configuration],
  val classifiers: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: GetClassifiersModule => (this.id == x.id) && (this.scalaModuleInfo == x.scalaModuleInfo) && (this.dependencies == x.dependencies) && (this.configurations == x.configurations) && (this.classifiers == x.classifiers)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.GetClassifiersModule".##) + id.##) + scalaModuleInfo.##) + dependencies.##) + configurations.##) + classifiers.##)
  }
  override def toString: String = {
    "GetClassifiersModule(" + id + ", " + scalaModuleInfo + ", " + dependencies + ", " + configurations + ", " + classifiers + ")"
  }
  private[this] def copy(id: sbt.librarymanagement.ModuleID = id, scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo] = scalaModuleInfo, dependencies: Vector[sbt.librarymanagement.ModuleID] = dependencies, configurations: Vector[sbt.librarymanagement.Configuration] = configurations, classifiers: Vector[String] = classifiers): GetClassifiersModule = {
    new GetClassifiersModule(id, scalaModuleInfo, dependencies, configurations, classifiers)
  }
  def withId(id: sbt.librarymanagement.ModuleID): GetClassifiersModule = {
    copy(id = id)
  }
  def withScalaModuleInfo(scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo]): GetClassifiersModule = {
    copy(scalaModuleInfo = scalaModuleInfo)
  }
  def withScalaModuleInfo(scalaModuleInfo: sbt.librarymanagement.ScalaModuleInfo): GetClassifiersModule = {
    copy(scalaModuleInfo = Option(scalaModuleInfo))
  }
  def withDependencies(dependencies: Vector[sbt.librarymanagement.ModuleID]): GetClassifiersModule = {
    copy(dependencies = dependencies)
  }
  def withConfigurations(configurations: Vector[sbt.librarymanagement.Configuration]): GetClassifiersModule = {
    copy(configurations = configurations)
  }
  def withClassifiers(classifiers: Vector[String]): GetClassifiersModule = {
    copy(classifiers = classifiers)
  }
}
object GetClassifiersModule {
  
  def apply(id: sbt.librarymanagement.ModuleID, scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo], dependencies: Vector[sbt.librarymanagement.ModuleID], configurations: Vector[sbt.librarymanagement.Configuration], classifiers: Vector[String]): GetClassifiersModule = new GetClassifiersModule(id, scalaModuleInfo, dependencies, configurations, classifiers)
  def apply(id: sbt.librarymanagement.ModuleID, scalaModuleInfo: sbt.librarymanagement.ScalaModuleInfo, dependencies: Vector[sbt.librarymanagement.ModuleID], configurations: Vector[sbt.librarymanagement.Configuration], classifiers: Vector[String]): GetClassifiersModule = new GetClassifiersModule(id, Option(scalaModuleInfo), dependencies, configurations, classifiers)
}
