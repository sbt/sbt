/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt

import java.io.File
import scala.xml.NodeSeq

final class IvyPaths(val baseDirectory: File, val cacheDirectory: Option[File]) extends NotNull
final class IvyConfiguration(val paths: IvyPaths, val resolvers: Seq[Resolver], val log: IvyLogger) extends NotNull

final class ModuleConfiguration(val module: ModuleID, val dependencies: Iterable[ModuleID], val ivyXML: NodeSeq,
	val configurations: Iterable[Configuration], val defaultConfiguration: Option[Configuration], val ivyScala: Option[IvyScala],
	val artifacts: Iterable[Artifact], val validate: Boolean) extends NotNull
{
	def isUnconfigured = dependencies.isEmpty && ivyXML.isEmpty && configurations.isEmpty &&
		defaultConfiguration.isEmpty && artifacts.isEmpty
}
object ModuleConfiguration
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