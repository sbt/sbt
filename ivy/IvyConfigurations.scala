/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt

import java.io.File
import scala.xml.NodeSeq

final class IvyPaths(val baseDirectory: File, val cacheDirectory: Option[File]) extends NotNull
final class IvyConfiguration(val paths: IvyPaths, val resolvers: Seq[Resolver], 
	val moduleConfigurations: Seq[ModuleConfiguration], val log: IvyLogger) extends NotNull

sealed trait ModuleSettings extends NotNull
{
	def validate: Boolean
	def ivyScala: Option[IvyScala]
}
final class IvyFileConfiguration(val file: File, val ivyScala: Option[IvyScala], val validate: Boolean) extends ModuleSettings
final class PomConfiguration(val file: File, val ivyScala: Option[IvyScala], val validate: Boolean) extends ModuleSettings
final class InlineConfiguration(val module: ModuleID, val dependencies: Iterable[ModuleID], val ivyXML: NodeSeq,
	val configurations: Iterable[Configuration], val defaultConfiguration: Option[Configuration], val ivyScala: Option[IvyScala],
	val artifacts: Iterable[Artifact], val validate: Boolean) extends ModuleSettings
final class EmptyConfiguration(val module: ModuleID, val ivyScala: Option[IvyScala], val validate: Boolean) extends ModuleSettings
object InlineConfiguration
{
	def apply(module: ModuleID, dependencies: Iterable[ModuleID], artifacts: Iterable[Artifact]) =
		new InlineConfiguration(module, dependencies, NodeSeq.Empty, Nil, None, None, artifacts, false)
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