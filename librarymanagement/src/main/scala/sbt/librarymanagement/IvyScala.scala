/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.librarymanagement

import java.util.Collections.emptyMap
import scala.collection.mutable.HashSet

import org.apache.ivy.core.module.descriptor._
import org.apache.ivy.core.module.id.{ ArtifactId, ModuleId, ModuleRevisionId }
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.namespace.NamespaceTransformer
import sbt.util.Logger

object ScalaArtifacts {
  val Organization = "org.scala-lang"
  val LibraryID = "scala-library"
  val CompilerID = "scala-compiler"
  val ReflectID = "scala-reflect"
  val ActorsID = "scala-actors"
  val ScalapID = "scalap"
  val Artifacts = Vector(LibraryID, CompilerID, ReflectID, ActorsID, ScalapID)
  val DottyIDPrefix = "dotty"

  def dottyID(binaryVersion: String): String = s"${DottyIDPrefix}_${binaryVersion}"

  def libraryDependency(version: String): ModuleID = ModuleID(Organization, LibraryID, version)

  private[sbt] def toolDependencies(org: String, version: String, isDotty: Boolean = false): Seq[ModuleID] =
    if (isDotty)
      Seq(ModuleID(org, DottyIDPrefix, version).withConfigurations(Some(Configurations.ScalaTool.name + "->compile"))
        .withCrossVersion(CrossVersion.binary))
    else
      Seq(
        scalaToolDependency(org, ScalaArtifacts.CompilerID, version),
        scalaToolDependency(org, ScalaArtifacts.LibraryID, version)
      )

  private[this] def scalaToolDependency(org: String, id: String, version: String): ModuleID =
    ModuleID(org, id, version).withConfigurations(Some(Configurations.ScalaTool.name + "->default,optional(default)"))
}
object SbtArtifacts {
  val Organization = "org.scala-sbt"
}

import ScalaArtifacts._

private[sbt] abstract class IvyScalaCompanion {
  /** Performs checks/adds filters on Scala dependencies (if enabled in IvyScala). */
  def checkModule(module: DefaultModuleDescriptor, conf: String, log: Logger)(check: IvyScala): Unit = {
    if (check.checkExplicit)
      checkDependencies(module, check.scalaOrganization, check.scalaArtifacts, check.scalaBinaryVersion, check.configurations, log)
    if (check.filterImplicit)
      excludeScalaJars(module, check.configurations)
    if (check.overrideScalaVersion)
      overrideScalaVersion(module, check.scalaOrganization, check.scalaFullVersion)
  }

  class OverrideScalaMediator(scalaOrganization: String, scalaVersion: String) extends DependencyDescriptorMediator {
    def mediate(dd: DependencyDescriptor): DependencyDescriptor = {
      val transformer =
        new NamespaceTransformer {
          def transform(mrid: ModuleRevisionId): ModuleRevisionId = {
            if (mrid == null) mrid
            else
              mrid.getName match {
                case name @ (CompilerID | LibraryID | ReflectID | ActorsID | ScalapID) =>
                  ModuleRevisionId.newInstance(scalaOrganization, name, mrid.getBranch, scalaVersion, mrid.getQualifiedExtraAttributes)
                case _ => mrid
              }
          }

          def isIdentity: Boolean = false
        }

      DefaultDependencyDescriptor.transformInstance(dd, transformer, false)
    }
  }

  def overrideScalaVersion(module: DefaultModuleDescriptor, organization: String, version: String): Unit = {
    val mediator = new OverrideScalaMediator(organization, version)
    module.addDependencyDescriptorMediator(new ModuleId(Organization, "*"), ExactPatternMatcher.INSTANCE, mediator)
    if (organization != Organization)
      module.addDependencyDescriptorMediator(new ModuleId(organization, "*"), ExactPatternMatcher.INSTANCE, mediator)
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
  private def checkDependencies(module: ModuleDescriptor, scalaOrganization: String, scalaArtifacts: Seq[String], scalaBinaryVersion: String, configurations: Iterable[Configuration], log: Logger): Unit = {
    val configSet = if (configurations.isEmpty) (c: String) => true else configurationSet(configurations)
    def binaryScalaWarning(dep: DependencyDescriptor): Option[String] =
      {
        val id = dep.getDependencyRevisionId
        val depBinaryVersion = CrossVersion.binaryScalaVersion(id.getRevision)
        def isScalaLangOrg = id.getOrganisation == scalaOrganization
        def isScalaArtifact = scalaArtifacts.contains(id.getName)
        def hasBinVerMismatch = depBinaryVersion != scalaBinaryVersion
        def matchesOneOfTheConfigs = dep.getModuleConfigurations.exists(configSet)
        val mismatched = isScalaLangOrg && isScalaArtifact && hasBinVerMismatch && matchesOneOfTheConfigs
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

  /**
   * Creates an IncludeRule that includes artifacts with the given module organization and name for
   * the given configurations.
   */
  private[sbt] def includeRule(organization: String, name: String, configurationNames: Iterable[String], includeTypePattern: String): IncludeRule =
    {
      val artifact = new ArtifactId(ModuleId.newInstance(organization, name), "*", includeTypePattern, "*")
      val rule = new DefaultIncludeRule(artifact, ExactPatternMatcher.INSTANCE, emptyMap[AnyRef, AnyRef])
      configurationNames.foreach(rule.addConfiguration)
      rule
    }
}
