/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import scala.xml.NodeSeq

final class IvyPaths(val baseDirectory: File, val cacheDirectory: Option[File]) extends NotNull
{
	def withBase(newBaseDirectory: File) = new IvyPaths(newBaseDirectory, cacheDirectory)
}
sealed trait IvyConfiguration extends NotNull
{
	type This <: IvyConfiguration
	def lock: Option[xsbti.GlobalLock]
	def baseDirectory: File
	def log: IvyLogger
	def withBase(newBaseDirectory: File): This
}
final class InlineIvyConfiguration(val paths: IvyPaths, val resolvers: Seq[Resolver], val otherResolvers: Seq[Resolver],
	val moduleConfigurations: Seq[ModuleConfiguration], val lock: Option[xsbti.GlobalLock], val log: IvyLogger) extends IvyConfiguration
{
	type This = InlineIvyConfiguration
	def baseDirectory = paths.baseDirectory
	def withBase(newBase: File) = new InlineIvyConfiguration(paths.withBase(newBase), resolvers, otherResolvers, moduleConfigurations, lock, log)
}
final class ExternalIvyConfiguration(val baseDirectory: File, val file: File, val lock: Option[xsbti.GlobalLock], val log: IvyLogger) extends IvyConfiguration
{
	type This = ExternalIvyConfiguration
	def withBase(newBase: File) = new ExternalIvyConfiguration(newBase, file, lock, log)
}

object IvyConfiguration
{
	/** Called to configure Ivy when inline resolvers are not specified.
	* This will configure Ivy with an 'ivy-settings.xml' file if there is one or else use default resolvers.*/
	def apply(paths: IvyPaths, lock: Option[xsbti.GlobalLock], log: IvyLogger): IvyConfiguration =
	{
		log.debug("Autodetecting configuration.")
		val defaultIvyConfigFile = IvySbt.defaultIvyConfiguration(paths.baseDirectory)
		if(defaultIvyConfigFile.canRead)
			new ExternalIvyConfiguration(paths.baseDirectory, defaultIvyConfigFile, lock, log)
		else
			new InlineIvyConfiguration(paths, Resolver.withDefaultResolvers(Nil), Nil, Nil, lock, log)
	}
}

sealed trait ModuleSettings extends NotNull
{
	def validate: Boolean
	def ivyScala: Option[IvyScala]
	def noScala: ModuleSettings
}
final class IvyFileConfiguration(val file: File, val ivyScala: Option[IvyScala], val validate: Boolean) extends ModuleSettings
{
	def noScala = new IvyFileConfiguration(file, None, validate)
}
final class PomConfiguration(val file: File, val ivyScala: Option[IvyScala], val validate: Boolean) extends ModuleSettings
{
	def noScala = new PomConfiguration(file, None, validate)
}
final class InlineConfiguration(val module: ModuleID, val dependencies: Iterable[ModuleID], val ivyXML: NodeSeq,
	val configurations: Iterable[Configuration], val defaultConfiguration: Option[Configuration], val ivyScala: Option[IvyScala],
	val validate: Boolean) extends ModuleSettings
{
	def withConfigurations(configurations: Iterable[Configuration]) = 
		new InlineConfiguration(module, dependencies, ivyXML, configurations, defaultConfiguration, ivyScala, validate)
	def noScala = new InlineConfiguration(module, dependencies, ivyXML, configurations, defaultConfiguration, None, validate)
}
final class EmptyConfiguration(val module: ModuleID, val ivyScala: Option[IvyScala], val validate: Boolean) extends ModuleSettings
{
	def noScala = new EmptyConfiguration(module, None, validate)
}
object InlineConfiguration
{
	def apply(module: ModuleID, dependencies: Iterable[ModuleID]) =
		new InlineConfiguration(module, dependencies, NodeSeq.Empty, Nil, None, None, false)
	def configurations(explicitConfigurations: Iterable[Configuration], defaultConfiguration: Option[Configuration]) =
		if(explicitConfigurations.isEmpty)
		{
			defaultConfiguration match
			{
				case Some(Configurations.DefaultIvyConfiguration) => Configurations.Default :: Nil
				case Some(Configurations.DefaultMavenConfiguration) => Configurations.defaultMavenConfigurations
				case _ => Nil
			}
		}
		else
			explicitConfigurations
}
object ModuleSettings
{
	def apply(ivyScala: Option[IvyScala], validate: Boolean, module: => ModuleID)(baseDirectory: File, log: IvyLogger) =
	{
		log.debug("Autodetecting dependencies.")
		val defaultPOMFile = IvySbt.defaultPOM(baseDirectory)
		if(defaultPOMFile.canRead)
			new PomConfiguration(defaultPOMFile, ivyScala, validate)
		else
		{
			val defaultIvy = IvySbt.defaultIvyFile(baseDirectory)
			if(defaultIvy.canRead)
				new IvyFileConfiguration(defaultIvy, ivyScala, validate)
			else
			{
				log.warn("No dependency configuration found, using defaults.")
				new EmptyConfiguration(module, ivyScala, validate)
			}
		}
	}
}