/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */

// based on Ivy's PomModuleDescriptorWriter, which is Apache Licensed, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package sbt;

import java.io.{BufferedWriter, File, OutputStreamWriter, FileOutputStream}
import scala.xml.{Node, NodeSeq, PrettyPrinter, XML}

import org.apache.ivy.{core, plugins, Ivy}
import core.settings.IvySettings
import core.module.{descriptor, id}
import descriptor.{DependencyDescriptor, License, ModuleDescriptor}
import id.ModuleRevisionId
import plugins.resolver.{ChainResolver, DependencyResolver, IBiblioResolver}

class MakePom
{
	def encoding = "UTF-8"
	def write(ivy: Ivy, module: ModuleDescriptor, configurations: Option[Iterable[Configuration]], extra: NodeSeq, output: File): Unit =
		write(toPom(ivy, module, configurations, extra), output)
	def write(node: Node, output: File): Unit = write(toString(node), output)
	def write(xmlString: String, output: File)
	{
		output.getParentFile.mkdirs()
		val out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), encoding))
		try
		{
			out.write("<?xml version='1.0' encoding='" + encoding + "'?>")
			out.newLine()
			out.write(xmlString)
		}
		finally { out.close() }
	}

	def toString(node: Node): String = new PrettyPrinter(1000, 4).format(node)
	def toPom(ivy: Ivy, module: ModuleDescriptor, configurations: Option[Iterable[Configuration]], extra: NodeSeq): Node =
		(<project xmlns="http://maven.apache.org/POM/4.0.0"  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
			<modelVersion>4.0.0</modelVersion>
			{ makeModuleID(module) }
			{ extra }
			{ makeDependencies(module, configurations) }
			{ makeRepositories(ivy.getSettings) }
		</project>)

	def makeModuleID(module: ModuleDescriptor): NodeSeq =
	{
		val mrid = moduleDescriptor(module)
		val a: NodeSeq = 
			(<groupId>{ mrid.getOrganisation }</groupId>
			<artifactId>{ mrid.getName }</artifactId>
			<packaging>{ packaging(module) }</packaging>)
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
	def packaging(module: ModuleDescriptor) =
		module.getAllArtifacts match
		{
			case Array() => "pom"
			case Array(x) => x.getType
			case xs =>
				val notpom = xs.filter(_.getType != "pom")
				if(notpom.isEmpty) "pom" else notpom(0).getType
		}

	def makeDependencies(module: ModuleDescriptor, configurations: Option[Iterable[Configuration]]): NodeSeq =
	{
		val dependencies = depsInConfs(module, configurations)
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


	def makeRepositories(settings: IvySettings) =
	{
		class MavenRepo(name: String, snapshots: Boolean, releases: Boolean)
		val repositories = resolvers(settings.getDefaultResolver)
		val mavenRepositories =
			repositories.flatMap {
				case m: IBiblioResolver if m.isM2compatible && m.getRoot != IBiblioResolver.DEFAULT_M2_ROOT => m :: Nil
				case _ => Nil
			}
		val repositoryElements = mavenRepositories.map { repo => mavenRepository(repo.getName, repo.getRoot) }
		if(repositoryElements.isEmpty) repositoryElements else <repositories>{repositoryElements}</repositories>
	}
	def flatten(rs: Seq[DependencyResolver]): Seq[DependencyResolver] = if(rs eq null) Nil else rs.flatMap(resolvers)
	def resolvers(r: DependencyResolver): Seq[DependencyResolver] =
		r match { case c: ChainResolver => flatten(castResolvers(c.getResolvers)); case _ => r :: Nil }

	// cast the contents of a pre-generics collection
	private def castResolvers(s: java.util.Collection[_]): Seq[DependencyResolver] =
		s.toArray.map(_.asInstanceOf[DependencyResolver])

	def toID(name: String) = checkID(name.filter(isValidIDCharacter).mkString, name)
	def isValidIDCharacter(c: Char) = c.isLetterOrDigit
	private def checkID(id: String, name: String) = if(id.isEmpty) error("Could not convert '" + name + "' to an ID") else id
	def mavenRepository(name: String, root: String): Node =
		mavenRepository(toID(name), name, root)
	def mavenRepository(id: String, name: String, root: String): Node =
		<repository>
			<id>{id}</id>
			<name>{name}</name>
			<url>{root}</url>
		</repository>

	/** Retain dependencies only with the configurations given, or all public configurations of `module` if `configurations` is None.
	* This currently only preserves the information required by makePom*/
	private def depsInConfs(module: ModuleDescriptor, configurations: Option[Iterable[Configuration]]): Seq[DependencyDescriptor] =
	{
		val keepConfigurations = IvySbt.getConfigurations(module, configurations)
		val keepSet = Set(keepConfigurations.toSeq : _*)
		def translate(dependency: DependencyDescriptor) =
		{
			val keep = dependency.getModuleConfigurations.filter(keepSet.contains)
			if(keep.isEmpty)
				None
			else // TODO: translate the dependency to contain only configurations to keep
				Some(dependency)
		}
		module.getDependencies flatMap translate
	}
}