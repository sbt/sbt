/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class GetClassifiersConfiguration private (
  val module: sbt.librarymanagement.GetClassifiersModule,
  val excludes: Vector[scala.Tuple2[sbt.librarymanagement.ModuleID, scala.Vector[sbt.librarymanagement.ConfigRef]]],
  val updateConfiguration: sbt.librarymanagement.UpdateConfiguration,
  val sourceArtifactTypes: Vector[String],
  val docArtifactTypes: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: GetClassifiersConfiguration => (this.module == x.module) && (this.excludes == x.excludes) && (this.updateConfiguration == x.updateConfiguration) && (this.sourceArtifactTypes == x.sourceArtifactTypes) && (this.docArtifactTypes == x.docArtifactTypes)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.GetClassifiersConfiguration".##) + module.##) + excludes.##) + updateConfiguration.##) + sourceArtifactTypes.##) + docArtifactTypes.##)
  }
  override def toString: String = {
    "GetClassifiersConfiguration(" + module + ", " + excludes + ", " + updateConfiguration + ", " + sourceArtifactTypes + ", " + docArtifactTypes + ")"
  }
  private[this] def copy(module: sbt.librarymanagement.GetClassifiersModule = module, excludes: Vector[scala.Tuple2[sbt.librarymanagement.ModuleID, scala.Vector[sbt.librarymanagement.ConfigRef]]] = excludes, updateConfiguration: sbt.librarymanagement.UpdateConfiguration = updateConfiguration, sourceArtifactTypes: Vector[String] = sourceArtifactTypes, docArtifactTypes: Vector[String] = docArtifactTypes): GetClassifiersConfiguration = {
    new GetClassifiersConfiguration(module, excludes, updateConfiguration, sourceArtifactTypes, docArtifactTypes)
  }
  def withModule(module: sbt.librarymanagement.GetClassifiersModule): GetClassifiersConfiguration = {
    copy(module = module)
  }
  def withExcludes(excludes: Vector[scala.Tuple2[sbt.librarymanagement.ModuleID, scala.Vector[sbt.librarymanagement.ConfigRef]]]): GetClassifiersConfiguration = {
    copy(excludes = excludes)
  }
  def withUpdateConfiguration(updateConfiguration: sbt.librarymanagement.UpdateConfiguration): GetClassifiersConfiguration = {
    copy(updateConfiguration = updateConfiguration)
  }
  def withSourceArtifactTypes(sourceArtifactTypes: Vector[String]): GetClassifiersConfiguration = {
    copy(sourceArtifactTypes = sourceArtifactTypes)
  }
  def withDocArtifactTypes(docArtifactTypes: Vector[String]): GetClassifiersConfiguration = {
    copy(docArtifactTypes = docArtifactTypes)
  }
}
object GetClassifiersConfiguration {
  
  def apply(module: sbt.librarymanagement.GetClassifiersModule, excludes: Vector[scala.Tuple2[sbt.librarymanagement.ModuleID, scala.Vector[sbt.librarymanagement.ConfigRef]]], updateConfiguration: sbt.librarymanagement.UpdateConfiguration, sourceArtifactTypes: Vector[String], docArtifactTypes: Vector[String]): GetClassifiersConfiguration = new GetClassifiersConfiguration(module, excludes, updateConfiguration, sourceArtifactTypes, docArtifactTypes)
}
