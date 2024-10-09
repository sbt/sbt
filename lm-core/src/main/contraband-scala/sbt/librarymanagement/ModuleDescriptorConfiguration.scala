/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class ModuleDescriptorConfiguration private (
  validate: Boolean,
  scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo],
  val module: sbt.librarymanagement.ModuleID,
  val moduleInfo: sbt.librarymanagement.ModuleInfo,
  val dependencies: Vector[sbt.librarymanagement.ModuleID],
  val overrides: Vector[sbt.librarymanagement.ModuleID],
  val excludes: Vector[sbt.librarymanagement.InclExclRule],
  val ivyXML: scala.xml.NodeSeq,
  val configurations: Vector[sbt.librarymanagement.Configuration],
  val defaultConfiguration: Option[sbt.librarymanagement.Configuration],
  val conflictManager: sbt.librarymanagement.ConflictManager) extends sbt.librarymanagement.ModuleSettings(validate, scalaModuleInfo) with Serializable {
  
  private def this(module: sbt.librarymanagement.ModuleID, moduleInfo: sbt.librarymanagement.ModuleInfo) = this(false, None, module, moduleInfo, Vector.empty, Vector.empty, Vector.empty, scala.xml.NodeSeq.Empty, sbt.librarymanagement.Configurations.default, Option(sbt.librarymanagement.Configurations.Compile), sbt.librarymanagement.ConflictManager.default)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ModuleDescriptorConfiguration => (this.validate == x.validate) && (this.scalaModuleInfo == x.scalaModuleInfo) && (this.module == x.module) && (this.moduleInfo == x.moduleInfo) && (this.dependencies == x.dependencies) && (this.overrides == x.overrides) && (this.excludes == x.excludes) && (this.ivyXML == x.ivyXML) && (this.configurations == x.configurations) && (this.defaultConfiguration == x.defaultConfiguration) && (this.conflictManager == x.conflictManager)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ModuleDescriptorConfiguration".##) + validate.##) + scalaModuleInfo.##) + module.##) + moduleInfo.##) + dependencies.##) + overrides.##) + excludes.##) + ivyXML.##) + configurations.##) + defaultConfiguration.##) + conflictManager.##)
  }
  override def toString: String = {
    "ModuleDescriptorConfiguration(" + validate + ", " + scalaModuleInfo + ", " + module + ", " + moduleInfo + ", " + dependencies + ", " + overrides + ", " + excludes + ", " + ivyXML + ", " + configurations + ", " + defaultConfiguration + ", " + conflictManager + ")"
  }
  private[this] def copy(validate: Boolean = validate, scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo] = scalaModuleInfo, module: sbt.librarymanagement.ModuleID = module, moduleInfo: sbt.librarymanagement.ModuleInfo = moduleInfo, dependencies: Vector[sbt.librarymanagement.ModuleID] = dependencies, overrides: Vector[sbt.librarymanagement.ModuleID] = overrides, excludes: Vector[sbt.librarymanagement.InclExclRule] = excludes, ivyXML: scala.xml.NodeSeq = ivyXML, configurations: Vector[sbt.librarymanagement.Configuration] = configurations, defaultConfiguration: Option[sbt.librarymanagement.Configuration] = defaultConfiguration, conflictManager: sbt.librarymanagement.ConflictManager = conflictManager): ModuleDescriptorConfiguration = {
    new ModuleDescriptorConfiguration(validate, scalaModuleInfo, module, moduleInfo, dependencies, overrides, excludes, ivyXML, configurations, defaultConfiguration, conflictManager)
  }
  def withValidate(validate: Boolean): ModuleDescriptorConfiguration = {
    copy(validate = validate)
  }
  def withScalaModuleInfo(scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo]): ModuleDescriptorConfiguration = {
    copy(scalaModuleInfo = scalaModuleInfo)
  }
  def withScalaModuleInfo(scalaModuleInfo: sbt.librarymanagement.ScalaModuleInfo): ModuleDescriptorConfiguration = {
    copy(scalaModuleInfo = Option(scalaModuleInfo))
  }
  def withModule(module: sbt.librarymanagement.ModuleID): ModuleDescriptorConfiguration = {
    copy(module = module)
  }
  def withModuleInfo(moduleInfo: sbt.librarymanagement.ModuleInfo): ModuleDescriptorConfiguration = {
    copy(moduleInfo = moduleInfo)
  }
  def withDependencies(dependencies: Vector[sbt.librarymanagement.ModuleID]): ModuleDescriptorConfiguration = {
    copy(dependencies = dependencies)
  }
  def withOverrides(overrides: Vector[sbt.librarymanagement.ModuleID]): ModuleDescriptorConfiguration = {
    copy(overrides = overrides)
  }
  def withExcludes(excludes: Vector[sbt.librarymanagement.InclExclRule]): ModuleDescriptorConfiguration = {
    copy(excludes = excludes)
  }
  def withIvyXML(ivyXML: scala.xml.NodeSeq): ModuleDescriptorConfiguration = {
    copy(ivyXML = ivyXML)
  }
  def withConfigurations(configurations: Vector[sbt.librarymanagement.Configuration]): ModuleDescriptorConfiguration = {
    copy(configurations = configurations)
  }
  def withDefaultConfiguration(defaultConfiguration: Option[sbt.librarymanagement.Configuration]): ModuleDescriptorConfiguration = {
    copy(defaultConfiguration = defaultConfiguration)
  }
  def withDefaultConfiguration(defaultConfiguration: sbt.librarymanagement.Configuration): ModuleDescriptorConfiguration = {
    copy(defaultConfiguration = Option(defaultConfiguration))
  }
  def withConflictManager(conflictManager: sbt.librarymanagement.ConflictManager): ModuleDescriptorConfiguration = {
    copy(conflictManager = conflictManager)
  }
}
object ModuleDescriptorConfiguration extends sbt.librarymanagement.InlineConfigurationFunctions {
  
  def apply(module: sbt.librarymanagement.ModuleID, moduleInfo: sbt.librarymanagement.ModuleInfo): ModuleDescriptorConfiguration = new ModuleDescriptorConfiguration(module, moduleInfo)
  def apply(validate: Boolean, scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo], module: sbt.librarymanagement.ModuleID, moduleInfo: sbt.librarymanagement.ModuleInfo, dependencies: Vector[sbt.librarymanagement.ModuleID], overrides: Vector[sbt.librarymanagement.ModuleID], excludes: Vector[sbt.librarymanagement.InclExclRule], ivyXML: scala.xml.NodeSeq, configurations: Vector[sbt.librarymanagement.Configuration], defaultConfiguration: Option[sbt.librarymanagement.Configuration], conflictManager: sbt.librarymanagement.ConflictManager): ModuleDescriptorConfiguration = new ModuleDescriptorConfiguration(validate, scalaModuleInfo, module, moduleInfo, dependencies, overrides, excludes, ivyXML, configurations, defaultConfiguration, conflictManager)
  def apply(validate: Boolean, scalaModuleInfo: sbt.librarymanagement.ScalaModuleInfo, module: sbt.librarymanagement.ModuleID, moduleInfo: sbt.librarymanagement.ModuleInfo, dependencies: Vector[sbt.librarymanagement.ModuleID], overrides: Vector[sbt.librarymanagement.ModuleID], excludes: Vector[sbt.librarymanagement.InclExclRule], ivyXML: scala.xml.NodeSeq, configurations: Vector[sbt.librarymanagement.Configuration], defaultConfiguration: sbt.librarymanagement.Configuration, conflictManager: sbt.librarymanagement.ConflictManager): ModuleDescriptorConfiguration = new ModuleDescriptorConfiguration(validate, Option(scalaModuleInfo), module, moduleInfo, dependencies, overrides, excludes, ivyXML, configurations, Option(defaultConfiguration), conflictManager)
}
