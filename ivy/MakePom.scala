// based on Ivy's PomModuleDescriptorWriter, which is Apache Licensed, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package sbt;

import java.io.{BufferedWriter, File, OutputStreamWriter, FileOutputStream}
import scala.xml.{Node, NodeSeq, XML}

import org.apache.ivy.{core, Ivy}
import core.module.{descriptor, id}
import descriptor.{DependencyDescriptor, License, ModuleDescriptor}
import id.ModuleRevisionId

class PomWriter
{
	def encoding = "UTF-8"
	def write(module: ModuleDescriptor, extra: NodeSeq, output: File): Unit = write(toPom(module, extra), output)
	def write(node: Node, output: File)
	{
		output.getParentFile.mkdirs()
		val out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), encoding))
		try { XML.write(out, node, encoding, true, null) }
		finally { out.close() }
	}

	def toPom(module: ModuleDescriptor, extra: NodeSeq): Node =
		(<project xmlns="http://maven.apache.org/POM/4.0.0"  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
			<modelVersion>4.0.0</modelVersion>
			{ makeModuleID(module) }
			{ extra }
			{ makeDependencies(module) }
			<!--{ makeRepositories(module.ivy) }-->
		</project>)

	def makeModuleID(module: ModuleDescriptor): NodeSeq =
	{
		val mrid = moduleDescriptor(module)
		val a: NodeSeq = 
			(<groupId>{ mrid.getOrganisation }</groupId>
			<artifactId>{ mrid.getName }</artifactId>
			<packaging> { packaging(mrid) }</packaging>)
		val b: NodeSeq =
			( (description(module.getDescription) ++
			homePage(module.getHomePage) ++
			revision(mrid.getRevision) ++
			licenses(module.getLicenses)) : NodeSeq )
		a ++ b
	}

	def description(d: String) = if((d eq null) || d.isEmpty) NodeSeq.Empty else <description>{d}</description>
	def licenses(ls: Array[License]) = if(ls == null || ls.isEmpty) NodeSeq.Empty else <licenses>{ls.map(license)}</licenses>
	def license(l: License) =
		<license>
			<name>{l.getName}</name>
			<url>{l.getUrl}</url>
			<distribution>jar</distribution>
		</license>
	def homePage(homePage: String) = if(homePage eq null) NodeSeq.Empty else <url>{homePage}</url>
	def revision(version: String) = if(version ne null) <version>{version}</version> else NodeSeq.Empty
	def packaging(module: ModuleRevisionId) = "jar"//module.getDefaultArtifact.getExt

	def makeDependencies(module: ModuleDescriptor): NodeSeq =
	{
		val dependencies = module.getDependencies
		if(dependencies.isEmpty) NodeSeq.Empty
		else
			<dependencies>
				{ dependencies.map(makeDependency) }
			</dependencies>
	}

	def makeDependency(dependency: DependencyDescriptor): NodeSeq =
	{
		val mrid = dependency.getDependencyRevisionId
		<dependency>
			<groupId>{mrid.getOrganisation}</groupId>
			<artifactId>{mrid.getName}</artifactId>
			<version>{mrid.getRevision}</version>
			{ scope(dependency)}
			{ optional(dependency) }
		</dependency>
	}

	def scope(dependency: DependencyDescriptor): NodeSeq =
		scope(getScope(dependency.getModuleConfigurations))
	def scope(scope: String): NodeSeq = if(scope ne null) <scope>{scope}</scope> else NodeSeq.Empty
	def optional(dependency: DependencyDescriptor) =
		if(isOptional(dependency.getModuleConfigurations)) <optional>true</optional> else NodeSeq.Empty
	def moduleDescriptor(module: ModuleDescriptor) = module.getModuleRevisionId

	def getScope(confs: Array[String]) =
	{
		Configurations.defaultMavenConfigurations.find(conf => confs.contains(conf.name)) match
		{
			case Some(conf) => conf.name
			case None =>
				if(confs.isEmpty || confs(0) == Configurations.Default.name)
					null
				else
					confs(0)
		}
	}
	def isOptional(confs: Array[String]) = confs.isEmpty || (confs.length == 1 && confs(0) == Configurations.Optional.name)
}
