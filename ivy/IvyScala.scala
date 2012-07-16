/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.util.Collections.emptyMap
import java.util.regex.Pattern
import scala.collection.mutable.{HashMap, HashSet}
import scala.util.matching.Regex

import org.apache.ivy.{core, plugins}
import core.module.descriptor.{DefaultExcludeRule, ExcludeRule}
import core.module.descriptor.{DependencyDescriptor, DefaultModuleDescriptor, ModuleDescriptor, OverrideDependencyDescriptorMediator}
import core.module.id.{ArtifactId,ModuleId, ModuleRevisionId}
import plugins.matcher.{Matcher, PatternMatcher, ExactPatternMatcher}

object ScalaArtifacts
{
		import xsbti.ArtifactInfo._
	val Organization = ScalaOrganization
	val LibraryID = ScalaLibraryID
	val CompilerID = ScalaCompilerID
	def libraryDependency(version: String): ModuleID = ModuleID(Organization, LibraryID, version)
}
object SbtArtifacts
{
		import xsbti.ArtifactInfo._
	val Organization = SbtOrganization
}

import ScalaArtifacts._

final case class IvyScala(scalaFullVersion: String, scalaBinaryVersion: String, configurations: Iterable[Configuration], checkExplicit: Boolean, filterImplicit: Boolean, overrideScalaVersion: Boolean)

private object IvyScala
{
	/** Performs checks/adds filters on Scala dependencies (if enabled in IvyScala). */
	def checkModule(module: DefaultModuleDescriptor, conf: String, log: Logger)(check: IvyScala)
	{
		if(check.checkExplicit)
			checkDependencies(module, check.scalaBinaryVersion, check.configurations, log)
		if(check.filterImplicit)
			excludeScalaJars(module, check.configurations)
		if(check.overrideScalaVersion)
			overrideScalaVersion(module, check.scalaFullVersion)
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
	private def checkDependencies(module: ModuleDescriptor, scalaBinaryVersion: String, configurations: Iterable[Configuration], log: Logger)
	{
		val configSet = if(configurations.isEmpty) (c: String) => true else configurationSet(configurations)
		def binaryScalaWarning(dep: DependencyDescriptor): Option[String] =
		{
			val id = dep.getDependencyRevisionId
			val depBinaryVersion = CrossVersion.binaryScalaVersion(id.getRevision)
			val mismatched = id.getOrganisation == Organization && depBinaryVersion != scalaBinaryVersion && dep.getModuleConfigurations.exists(configSet)
			if(mismatched)
				Some("Binary version (" + depBinaryVersion + ") for dependency " + id +
					"\n\tin " + module.getModuleRevisionId +
					" differs from Scala binary version in project (" + scalaBinaryVersion + ").")
			else
				None
		}
		module.getDependencies.toList.flatMap(binaryScalaWarning).toSet foreach { (s: String) => log.warn(s) }
	}
	private def configurationSet(configurations: Iterable[Configuration]) = configurations.map(_.toString).toSet

	/** Adds exclusions for the scala library and compiler jars so that they are not downloaded.  This is
	* done because these jars are provided by the ScalaInstance of the project.  The version of Scala to use
	* is done by setting scalaVersion in the project definition. */
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
			module.addExcludeRule(excludeRule(Organization, name, configurationNames, "jar"))
		excludeScalaJar(LibraryID)
		excludeScalaJar(CompilerID)
	}

  private val matchers = HashMap[String, Matcher]()

  private object RegexPatternMatcher extends PatternMatcher
  {
    def getName() = "SbtRegexPatternMatcher"
    def getMatcher(expression: String): Matcher =
    {
      if (expression == null) throw new NullPointerException()
      matchers.getOrElseUpdate(expression, new Matcher {
        val regex = expression.r.pattern
        def matches(input: String): Boolean = regex.matcher(input).matches
        def isExact = false
      })
    }
  }

	/** Creates an ExcludeRule that excludes artifacts with the given module organization and name for
	* the given configurations. */
	private[sbt] def excludeRule(organization: String, name: String, configurationNames: Iterable[String], excludeTypePattern: String): ExcludeRule =
  {
		val artifact = new ArtifactId(ModuleId.newInstance(organization, name), ".*", excludeTypePattern, ".*")
		val rule = new DefaultExcludeRule(artifact, RegexPatternMatcher, emptyMap[AnyRef,AnyRef])
		configurationNames.foreach(rule.addConfiguration)
		rule
	}
}
