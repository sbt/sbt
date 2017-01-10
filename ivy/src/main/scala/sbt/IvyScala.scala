/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.util.Collections.emptyMap
import scala.collection.mutable.HashSet

import org.apache.ivy.core.module.descriptor.{ DefaultExcludeRule, ExcludeRule }
import org.apache.ivy.core.module.descriptor.{ DefaultDependencyDescriptor, DependencyDescriptor, DependencyDescriptorMediator, DefaultModuleDescriptor, ModuleDescriptor, OverrideDependencyDescriptorMediator }
import org.apache.ivy.core.module.id.{ ArtifactId, ModuleId, ModuleRevisionId }
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.namespace.NamespaceTransformer

object ScalaArtifacts {
  import xsbti.ArtifactInfo._
  val Organization = ScalaOrganization
  val LibraryID = ScalaLibraryID
  val CompilerID = ScalaCompilerID
  val ReflectID = "scala-reflect"
  val ActorsID = "scala-actors"
  val ScalapID = "scalap"
  val Artifacts = Seq(LibraryID, CompilerID, ReflectID, ActorsID, ScalapID)
  val DottyIDPrefix = "dotty"

  def dottyID(binaryVersion: String): String = s"${DottyIDPrefix}_${binaryVersion}"

  def libraryDependency(version: String): ModuleID = ModuleID(Organization, LibraryID, version)

  private[sbt] def toolDependencies(org: String, version: String, isDotty: Boolean = false): Seq[ModuleID] =
    if (isDotty)
      Seq(ModuleID(org, DottyIDPrefix, version, Some(Configurations.ScalaTool.name + "->default(compile)"),
        crossVersion = CrossVersion.binary))
    else
      Seq(scalaToolDependency(org, ScalaArtifacts.CompilerID, version),
        scalaToolDependency(org, ScalaArtifacts.LibraryID, version))

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
  def checkModule(module: DefaultModuleDescriptor, conf: String, scalaVersionConfigs: Vector[String], log: Logger)(check: IvyScala): Unit = {
    if (check.checkExplicit)
      checkDependencies(module, check.scalaOrganization, check.scalaBinaryVersion, scalaVersionConfigs, log)
    if (check.filterImplicit)
      excludeScalaJars(module, check.configurations)
    if (check.overrideScalaVersion)
      overrideScalaVersion(module, check.scalaOrganization, check.scalaFullVersion, scalaVersionConfigs)
  }

  class OverrideScalaMediator(scalaOrganization: String, scalaVersion: String, scalaVersionConfigs0: Vector[String]) extends DependencyDescriptorMediator {
    private[this] val scalaVersionConfigs = scalaVersionConfigs0.toSet
    def mediate(dd: DependencyDescriptor): DependencyDescriptor = {
      // Mediate only for the dependencies in scalaVersion configurations. https://github.com/sbt/sbt/issues/2786
      def configQualifies: Boolean =
        (dd.getModuleConfigurations exists { scalaVersionConfigs })
      // Do not rewrite the dependencies of Scala dependencies themselves, this prevents bootstrapping
      // a Scala compiler using another Scala compiler.
      def dependeeQualifies: Boolean =
        dd.getParentRevisionId == null || (
          dd.getParentRevisionId.getName match {
            case name @ (CompilerID | LibraryID | ReflectID | ActorsID | ScalapID) =>
              false
            case _ =>
              true
          }
        )
      val transformer =
        new NamespaceTransformer {
          def transform(mrid: ModuleRevisionId): ModuleRevisionId = {
            if (mrid == null) mrid
            else
              mrid.getName match {
                case name @ (CompilerID | LibraryID | ReflectID | ActorsID | ScalapID) if configQualifies && dependeeQualifies =>
                  ModuleRevisionId.newInstance(scalaOrganization, name, mrid.getBranch, scalaVersion, mrid.getQualifiedExtraAttributes)
                case _ => mrid
              }
          }

          def isIdentity: Boolean = false
        }

      DefaultDependencyDescriptor.transformInstance(dd, transformer, false)
    }
  }

  def overrideScalaVersion(module: DefaultModuleDescriptor, organization: String, version: String, scalaVersionConfigs: Vector[String]): Unit = {
    val mediator = new OverrideScalaMediator(organization, version, scalaVersionConfigs)
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
  private def checkDependencies(module: ModuleDescriptor, scalaOrganization: String, scalaBinaryVersion: String, scalaVersionConfigs0: Vector[String], log: Logger): Unit = {
    val scalaVersionConfigs: String => Boolean = if (scalaVersionConfigs0.isEmpty) (c: String) => false else scalaVersionConfigs0.toSet
    def binaryScalaWarning(dep: DependencyDescriptor): Option[String] =
      {
        val id = dep.getDependencyRevisionId
        val depBinaryVersion = CrossVersion.binaryScalaVersion(id.getRevision)
        def isScalaLangOrg = id.getOrganisation == scalaOrganization
        def isScalaArtifact = Artifacts.contains(id.getName)
        def hasBinVerMismatch = depBinaryVersion != scalaBinaryVersion
        def matchesOneOfTheConfigs = dep.getModuleConfigurations exists { scalaVersionConfigs }
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
}
