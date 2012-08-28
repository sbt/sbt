/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import java.net.{URI,URL}
import scala.xml.NodeSeq

final class IvyPaths(val baseDirectory: File, val ivyHome: Option[File])
{
	def withBase(newBaseDirectory: File) = new IvyPaths(newBaseDirectory, ivyHome)
}
sealed trait IvyConfiguration
{
	type This <: IvyConfiguration
	def lock: Option[xsbti.GlobalLock]
	def baseDirectory: File
	def log: Logger
	def withBase(newBaseDirectory: File): This
}
final class InlineIvyConfiguration(val paths: IvyPaths, val resolvers: Seq[Resolver], val otherResolvers: Seq[Resolver],
	val moduleConfigurations: Seq[ModuleConfiguration], val localOnly: Boolean, val lock: Option[xsbti.GlobalLock],
	val checksums: Seq[String], val resolutionCacheDir: Option[File], val log: Logger) extends IvyConfiguration
{
	@deprecated("Use the variant that accepts the resolution cache location.", "0.13.0")
	def this(paths: IvyPaths, resolvers: Seq[Resolver], otherResolvers: Seq[Resolver],
		moduleConfigurations: Seq[ModuleConfiguration], localOnly: Boolean, lock: Option[xsbti.GlobalLock],
		checksums: Seq[String], log: Logger) =
			this(paths, resolvers, otherResolvers, moduleConfigurations, localOnly, lock, checksums, None, log)

	type This = InlineIvyConfiguration
	def baseDirectory = paths.baseDirectory
	def withBase(newBase: File) = new InlineIvyConfiguration(paths.withBase(newBase), resolvers, otherResolvers, moduleConfigurations, localOnly, lock, checksums, resolutionCacheDir, log)
	def changeResolvers(newResolvers: Seq[Resolver]) = new InlineIvyConfiguration(paths, newResolvers, otherResolvers, moduleConfigurations, localOnly, lock, checksums, resolutionCacheDir, log)
}
final class ExternalIvyConfiguration(val baseDirectory: File, val uri: URI, val lock: Option[xsbti.GlobalLock], val extraResolvers: Seq[Resolver], val log: Logger) extends IvyConfiguration
{
	type This = ExternalIvyConfiguration
	def withBase(newBase: File) = new ExternalIvyConfiguration(newBase, uri, lock, extraResolvers, log)
}
object ExternalIvyConfiguration
{
	def apply(baseDirectory: File, file: File, lock: Option[xsbti.GlobalLock], log: Logger) = new ExternalIvyConfiguration(baseDirectory, file.toURI, lock, Nil, log)
}

object IvyConfiguration
{
	/** Called to configure Ivy when inline resolvers are not specified.
	* This will configure Ivy with an 'ivy-settings.xml' file if there is one or else use default resolvers.*/
	@deprecated("Explicitly use either external or inline configuration.", "0.12.0")
	def apply(paths: IvyPaths, lock: Option[xsbti.GlobalLock], localOnly: Boolean, checksums: Seq[String], log: Logger): IvyConfiguration =
	{
		log.debug("Autodetecting configuration.")
		val defaultIvyConfigFile = IvySbt.defaultIvyConfiguration(paths.baseDirectory)
		if(defaultIvyConfigFile.canRead)
			ExternalIvyConfiguration(paths.baseDirectory, defaultIvyConfigFile, lock, log)
		else
			new InlineIvyConfiguration(paths, Resolver.withDefaultResolvers(Nil), Nil, Nil, localOnly, lock, checksums, None, log)
	}
}

sealed trait ModuleSettings
{
	def validate: Boolean
	def ivyScala: Option[IvyScala]
	def noScala: ModuleSettings
}
final case class IvyFileConfiguration(file: File, ivyScala: Option[IvyScala], validate: Boolean) extends ModuleSettings
{
	def noScala = copy(ivyScala = None)
}
final case class PomConfiguration(file: File, ivyScala: Option[IvyScala], validate: Boolean) extends ModuleSettings
{
	def noScala = copy(ivyScala = None)
}
final case class InlineConfiguration(module: ModuleID, moduleInfo: ModuleInfo, dependencies: Seq[ModuleID], overrides: Set[ModuleID] = Set.empty, ivyXML: NodeSeq = NodeSeq.Empty, configurations: Seq[Configuration] = Nil, defaultConfiguration: Option[Configuration] = None, ivyScala: Option[IvyScala] = None, validate: Boolean = false) extends ModuleSettings
{
	def withConfigurations(configurations: Seq[Configuration]) =  copy(configurations = configurations)
	def noScala = copy(ivyScala = None)
}
final case class EmptyConfiguration(module: ModuleID, moduleInfo: ModuleInfo, ivyScala: Option[IvyScala], validate: Boolean) extends ModuleSettings
{
	def noScala = copy(ivyScala = None)
}
object InlineConfiguration
{
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
	def apply(ivyScala: Option[IvyScala], validate: Boolean, module: => ModuleID, moduleInfo: => ModuleInfo)(baseDirectory: File, log: Logger): ModuleSettings =
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
				new EmptyConfiguration(module, moduleInfo, ivyScala, validate)
			}
		}
	}
}
