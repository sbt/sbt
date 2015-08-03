/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.util.Collections.emptyMap
import scala.collection.mutable.HashSet

import org.apache.ivy.core.module.descriptor.{ DefaultExcludeRule, ExcludeRule }
import org.apache.ivy.core.module.descriptor.{ DependencyDescriptor, DefaultModuleDescriptor, ModuleDescriptor, OverrideDependencyDescriptorMediator }
import org.apache.ivy.core.module.id.{ ArtifactId, ModuleId, ModuleRevisionId }
import org.apache.ivy.plugins.matcher.ExactPatternMatcher

object ScalaArtifacts {
  import xsbti.ArtifactInfo._
  val Organization = ScalaOrganization
  val LibraryID = ScalaLibraryID
  val CompilerID = ScalaCompilerID
  val ReflectID = "scala-reflect"
  def libraryDependency(version: String): ModuleID = ModuleID(Organization, LibraryID, version)

  private[sbt] def toolDependencies(org: String, version: String): Seq[ModuleID] = Seq(
    scalaToolDependency(org, ScalaArtifacts.CompilerID, version),
    scalaToolDependency(org, ScalaArtifacts.LibraryID, version)
  )
  private[this] def scalaToolDependency(org: String, id: String, version: String): ModuleID =
    ModuleID(org, id, version, Some(Configurations.ScalaTool.name + "->default,optional(default)"))
}
object SbtArtifacts {
  import xsbti.ArtifactInfo._
  val Organization = SbtOrganization
}

import ScalaArtifacts._

final case class IvyScala(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Iterable[Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean, scalaOrganization: String = ScalaArtifacts.Organization)

private object IvyScala {
  /** Performs checks/adds filters on Scala dependencies (if enabled in IvyScala). */
  def checkModule(module: DefaultModuleDescriptor, conf: String, log: Logger)(check: IvyScala): Unit = {
    if (check.checkExplicit)
      checkDependencies(module, check.scalaBinaryVersion, check.configurations, log)
    if (check.filterImplicit)
      excludeScalaJars(module, check.configurations)
    if (check.overrideScalaVersion)
      overrideScalaVersion(module, check.scalaFullVersion)
  }
  def overrideScalaVersion(module: DefaultModuleDescriptor, version: String): Unit = {
    overrideVersion(module, Organization, LibraryID, version)
    overrideVersion(module, Organization, CompilerID, version)
    overrideVersion(module, Organization, ReflectID, version)
  }
  def overrideVersion(module: DefaultModuleDescriptor, org: String, name: String, version: String): Unit = {
    val id = new ModuleId(org, name)
    val over = new OverrideDependencyDescriptorMediator(null, version)
    module.addDependencyDescriptorMediator(id, ExactPatternMatcher.INSTANCE, over)
  }

  /**
   * Checks the immediate dependencies of module for dependencies on scala jars and verifies that the version on the
   * dependencies matches scalaVersion.
   */
  private def checkDependencies(module: ModuleDescriptor, scalaBinaryVersion: String, configurations: Iterable[Configuration], log: Logger): Unit = {
    val configSet = if (configurations.isEmpty) (c: String) => true else configurationSet(configurations)
    def binaryScalaWarning(dep: DependencyDescriptor): Option[String] =
      {
        val id = dep.getDependencyRevisionId
        val depBinaryVersion = CrossVersion.binaryScalaVersion(id.getRevision)
        def isScalaLangOrg = id.getOrganisation == Organization
        def isNotScalaActorsMigration = !(id.getName startsWith "scala-actors-migration") // Exception to the rule: sbt/sbt#1818
        def isNotScalaPickling = !(id.getName startsWith "scala-pickling") // Exception to the rule: sbt/sbt#1899
        def hasBinVerMismatch = depBinaryVersion != scalaBinaryVersion
        def matchesOneOfTheConfigs = dep.getModuleConfigurations.exists(configSet)
        val mismatched = isScalaLangOrg && isNotScalaActorsMigration && isNotScalaPickling && hasBinVerMismatch && matchesOneOfTheConfigs
        if (mismatched)
          Some("Binary version (" + depBinaryVersion + ") for dependency " + id +
            "\n\tin " + module.getModuleRevisionId +
            " differs from Scala binary version in project (" + scalaBinaryVersion + ").")
        else
          None
      }
    module.getDependencies.toList.flatMap(binaryScalaWarning).toSet foreach { (s: String) => log.warn(s) }
  }
  private def configurationSet(configurations: Iterable[Configuration]) = configurations.map(_.toString).toSet

  /**
   * Adds exclusions for the scala library and compiler jars so that they are not downloaded.  This is
   * done because these jars are provided by the ScalaInstance of the project.  The version of Scala to use
   * is done by setting scalaVersion in the project definition.
   */
  private def excludeScalaJars(module: DefaultModuleDescriptor, configurations: Iterable[Configuration]): Unit = {
    val configurationNames =
      {
        val names = module.getConfigurationsNames
        if (configurations.isEmpty)
          names
        else {
          val configSet = configurationSet(configurations)
          configSet.intersect(HashSet(names: _*))
          configSet.toArray
        }
      }
    def excludeScalaJar(name: String): Unit =
      module.addExcludeRule(excludeRule(Organization, name, configurationNames, "jar"))
    excludeScalaJar(LibraryID)
    excludeScalaJar(CompilerID)
  }
  /**
   * Creates an ExcludeRule that excludes artifacts with the given module organization and name for
   * the given configurations.
   */
  private[sbt] def excludeRule(organization: String, name: String, configurationNames: Iterable[String], excludeTypePattern: String): ExcludeRule =
    {
      val artifact = new ArtifactId(ModuleId.newInstance(organization, name), "*", excludeTypePattern, "*")
      val rule = new DefaultExcludeRule(artifact, ExactPatternMatcher.INSTANCE, emptyMap[AnyRef, AnyRef])
      configurationNames.foreach(rule.addConfiguration)
      rule
    }
}
