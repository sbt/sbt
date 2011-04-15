/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.util.Collections
import scala.collection.mutable.HashSet

import org.apache.ivy.{core, plugins}
import core.module.descriptor.{DefaultExcludeRule, ExcludeRule}
import core.module.descriptor.{DefaultModuleDescriptor, ModuleDescriptor, OverrideDependencyDescriptorMediator}
import core.module.id.{ArtifactId,ModuleId, ModuleRevisionId}
import plugins.matcher.ExactPatternMatcher

object ScalaArtifacts
{
	val Organization = "org.scala-lang"
	val LibraryID = "scala-library"
	val CompilerID = "scala-compiler"
}

import ScalaArtifacts._

final class IvyScala(val scalaVersion: String, val configurations: Iterable[Configuration], val checkExplicit: Boolean, val filterImplicit: Boolean, val overrideScalaVersion: Boolean)
private object IvyScala
{
	/** Performs checks/adds filters on Scala dependencies (if enabled in IvyScala). */
	def checkModule(module: DefaultModuleDescriptor, conf: String)(check: IvyScala)
	{
		if(check.checkExplicit)
			checkDependencies(module, check.scalaVersion, check.configurations)
		if(check.filterImplicit)
			excludeScalaJars(module, check.configurations)
		if(check.overrideScalaVersion)
			overrideScalaVersion(module, check.scalaVersion)
	}
	def overrideScalaVersion(module: DefaultModuleDescriptor, version: String)
	{
		overrideVersion(module, Organization, LibraryID, version)
		overrideVersion(module, Organization, CompilerID, version)
	}
	def overrideVersion(module: DefaultModuleDescriptor, org: String, name: String, version: String)
	{
		val id = new ModuleId(org, name)
		val over = new OverrideDependencyDescriptorMediator(null, version)
		module.addDependencyDescriptorMediator(id, ExactPatternMatcher.INSTANCE, over)
	}
                
	/** Checks the immediate dependencies of module for dependencies on scala jars and verifies that the version on the
	* dependencies matches scalaVersion. */
	private def checkDependencies(module: ModuleDescriptor, scalaVersion: String, configurations: Iterable[Configuration])
	{
		val configSet = configurationSet(configurations)
		for(dep <- module.getDependencies.toList)
		{
			val id = dep.getDependencyRevisionId
			if(id.getOrganisation == Organization && id.getRevision != scalaVersion && dep.getModuleConfigurations.exists(configSet.contains))
				error("Different Scala version specified in dependency ("+ id.getRevision + ") than in project (" + scalaVersion + ").")
		}
	}
	private def configurationSet(configurations: Iterable[Configuration]) = HashSet(configurations.map(_.toString).toSeq : _*)
	/** Adds exclusions for the scala library and compiler jars so that they are not downloaded.  This is
	* done because normally these jars are already on the classpath and cannot/should not be overridden.  The version
	* of Scala to use is done by setting build.scala.versions in the project definition. */
	private def excludeScalaJars(module: DefaultModuleDescriptor, configurations: Iterable[Configuration])
	{
		val configurationNames =
		{
			val names = module.getConfigurationsNames
			if(configurations.isEmpty)
				names
			else
			{
				val configSet = configurationSet(configurations)
				configSet.intersect(HashSet(names : _*))
				configSet.toArray
			}
		}
		def excludeScalaJar(name: String): Unit =
			module.addExcludeRule(excludeRule(Organization, name, configurationNames))
		excludeScalaJar(LibraryID)
		excludeScalaJar(CompilerID)
	}
	/** Creates an ExcludeRule that excludes artifacts with the given module organization and name for
	* the given configurations. */
	private def excludeRule(organization: String, name: String, configurationNames: Iterable[String]): ExcludeRule =
	{
		val artifact = new ArtifactId(ModuleId.newInstance(organization, name), "*", "jar", "*")
		val rule = new DefaultExcludeRule(artifact, ExactPatternMatcher.INSTANCE, Collections.emptyMap[AnyRef,AnyRef])
		configurationNames.foreach(rule.addConfiguration)
		rule
	}
}