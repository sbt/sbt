/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */

// based on Ivy's PomModuleDescriptorWriter, which is Apache Licensed, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package sbt;

import java.io.File
import scala.xml.{Node => XNode, NodeSeq, PrettyPrinter, XML}
import Configurations.Optional

import org.apache.ivy.{core, plugins, Ivy}
import core.settings.IvySettings
import core.module.{descriptor, id}
import descriptor.{DependencyDescriptor, License, ModuleDescriptor, ExcludeRule}
import id.ModuleRevisionId
import plugins.resolver.{ChainResolver, DependencyResolver, IBiblioResolver}

class MakePom(val log: Logger)
{
	def write(ivy: Ivy, module: ModuleDescriptor, moduleInfo: ModuleInfo, configurations: Option[Iterable[Configuration]], extra: NodeSeq, process: XNode => XNode, filterRepositories: MavenRepository => Boolean, allRepositories: Boolean, output: File): Unit =
		write(process(toPom(ivy, module, moduleInfo, configurations, extra, filterRepositories, allRepositories)), output)
	// use \n as newline because toString uses PrettyPrinter, which hard codes line endings to be \n
	def write(node: XNode, output: File): Unit = write(toString(node), output, "\n")
	def write(xmlString: String, output: File, newline: String)
	{
		IO.write(output, "<?xml version='1.0' encoding='" + IO.utf8.name + "'?>" + newline + xmlString)
	}

	def toString(node: XNode): String = new PrettyPrinter(1000, 4).format(node)
	def toPom(ivy: Ivy, module: ModuleDescriptor, moduleInfo: ModuleInfo, configurations: Option[Iterable[Configuration]], extra: NodeSeq, filterRepositories: MavenRepository => Boolean, allRepositories: Boolean): XNode =
		(<project xmlns="http://maven.apache.org/POM/4.0.0"  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
			<modelVersion>4.0.0</modelVersion>
			{ makeModuleID(module) }
			<name>{moduleInfo.nameFormal}</name>
			{ makeStartYear(moduleInfo) }
			{ makeOrganization(moduleInfo) }
			{ extra }
			{
				val deps = depsInConfs(module, configurations)
				makeProperties(module, deps) ++
				makeDependencies(deps)
			}
			{ makeRepositories(ivy.getSettings, allRepositories, filterRepositories) }
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

	def makeStartYear(moduleInfo: ModuleInfo): NodeSeq = moduleInfo.startYear map { y => <inceptionYear>{y}</inceptionYear> } getOrElse NodeSeq.Empty
	def makeOrganization(moduleInfo: ModuleInfo): NodeSeq =
	{
		<organization>
			<name>{moduleInfo.organizationName}</name>
			{ moduleInfo.organizationHomepage map { h => <url>{h}</url> } getOrElse NodeSeq.Empty }
		</organization>
	}
	def makeProperties(module: ModuleDescriptor, dependencies: Seq[DependencyDescriptor]): NodeSeq =
	{
		val extra = IvySbt.getExtraAttributes(module)
		val depExtra = CustomPomParser.writeDependencyExtra(dependencies).mkString("\n")
		val allExtra = if(depExtra.isEmpty) extra else extra.updated(CustomPomParser.ExtraAttributesKey, depExtra)
		if(allExtra.isEmpty) NodeSeq.Empty else makeProperties(allExtra)
	}
	def makeProperties(extra: Map[String,String]): NodeSeq =
		<properties> {
			for( (key,value) <- extra ) yield
				(<x>{value}</x>).copy(label = key)
		} </properties>

	def description(d: String) = if((d eq null) || d.isEmpty) NodeSeq.Empty else <description>{d}</description>
	def licenses(ls: Array[License]) = if(ls == null || ls.isEmpty) NodeSeq.Empty else <licenses>{ls.map(license)}</licenses>
	def license(l: License) =
		<license>
			<name>{l.getName}</name>
			<url>{l.getUrl}</url>
			<distribution>repo</distribution>
		</license>
	def homePage(homePage: String) = if(homePage eq null) NodeSeq.Empty else <url>{homePage}</url>
	def revision(version: String) = if(version ne null) <version>{version}</version> else NodeSeq.Empty
	def packaging(module: ModuleDescriptor) =
		module.getAllArtifacts match
		{
			case Array() => "pom"
			case Array(x) => x.getType
			case xs =>
				val types = xs.map(_.getType).toList.filterNot(IgnoreTypes)
				types match {
					case Nil => Artifact.PomType
					case xs if xs.contains(Artifact.DefaultType) => Artifact.DefaultType
					case x :: xs => x
				}
		}
	val IgnoreTypes: Set[String] = Set(Artifact.SourceType, Artifact.DocType, Artifact.PomType)

	def makeDependencies(dependencies: Seq[DependencyDescriptor]): NodeSeq =
		if(dependencies.isEmpty)
			NodeSeq.Empty
		else
			<dependencies>
				{ dependencies.map(makeDependency) }
			</dependencies>

	def makeDependency(dependency: DependencyDescriptor): NodeSeq =
	{
		val mrid = dependency.getDependencyRevisionId
		val excl = dependency.getExcludeRules(dependency.getModuleConfigurations)
		<dependency>
			<groupId>{mrid.getOrganisation}</groupId>
			<artifactId>{mrid.getName}</artifactId>
			<version>{mrid.getRevision}</version>
			{ scopeAndOptional(dependency) }
			{ classifier(dependency) }
			{
				val (warns, excls) = List.separate(excl.map(makeExclusion))
				if(!warns.isEmpty) log.warn(warns.mkString(IO.Newline))
				if(excls.isEmpty) NodeSeq.Empty
				else
					<exclusions>
						{ excls }
					</exclusions>
			}
		</dependency>
	}

	def classifier(dependency: DependencyDescriptor): NodeSeq =
	{
		val jarDep = dependency.getAllDependencyArtifacts.filter(_.getType == Artifact.DefaultType).headOption
		jarDep match {
			case Some(a) => {
				val cl = a.getExtraAttribute("classifier")
				if (cl != null) <classifier>{cl}</classifier> else NodeSeq.Empty
			}
			case _ => NodeSeq.Empty
		}
	}

	def scopeAndOptional(dependency: DependencyDescriptor): NodeSeq  =
	{
		val (scope, opt) = getScopeAndOptional(dependency.getModuleConfigurations)
		scopeElem(scope) ++ optionalElem(opt)
	}
	def scopeElem(scope: Option[String]): NodeSeq = scope match {
		case Some(s) => <scope>{s}</scope>
		case None => NodeSeq.Empty
	}
	def optionalElem(opt: Boolean)  =  if(opt) <optional>true</optional> else NodeSeq.Empty
	def moduleDescriptor(module: ModuleDescriptor) = module.getModuleRevisionId

	def getScopeAndOptional(confs: Array[String]): (Option[String], Boolean) =
	{
		val (opt, notOptional) = confs.partition(_ == Optional.name)
		val defaultNotOptional = Configurations.defaultMavenConfigurations.find(notOptional contains _.name)
		val scope = defaultNotOptional match
		{
			case Some(conf) => Some(conf.name)
			case None =>
				if(notOptional.isEmpty || notOptional(0) == Configurations.Default.name)
					None
				else
					Option(notOptional(0))
		}
		(scope, !opt.isEmpty)
	}

	def makeExclusion(exclRule: ExcludeRule): Either[String, NodeSeq] =
	{
		val m = exclRule.getId.getModuleId
		val (g, a) = (m.getOrganisation, m.getName)
		if(g == null || g.isEmpty || g == "*" || a.isEmpty || a == "*")
			Left("Skipped generating '<exclusion/>' for %s. Dependency exclusion should have both 'org' and 'module' to comply with Maven POM's schema.".format(m))
		else
			Right(
				<exclusion>
					<groupId>{g}</groupId>
					<artifactId>{a}</artifactId>
				</exclusion>
			)
	}

	def makeRepositories(settings: IvySettings, includeAll: Boolean, filterRepositories: MavenRepository => Boolean) =
	{
		class MavenRepo(name: String, snapshots: Boolean, releases: Boolean)
		val repositories = if(includeAll) allResolvers(settings) else resolvers(settings.getDefaultResolver)
		val mavenRepositories =
			repositories.flatMap {
				case m: IBiblioResolver if m.isM2compatible && m.getRoot != IBiblioResolver.DEFAULT_M2_ROOT =>
					MavenRepository(m.getName, m.getRoot) :: Nil
				case _ => Nil
			}
		val repositoryElements =  mavenRepositories.filter(filterRepositories).map(mavenRepository)
		if(repositoryElements.isEmpty) repositoryElements else <repositories>{repositoryElements}</repositories>
	}
	def allResolvers(settings: IvySettings): Seq[DependencyResolver] = flatten(castResolvers(settings.getResolvers)).distinct
	def flatten(rs: Seq[DependencyResolver]): Seq[DependencyResolver] = if(rs eq null) Nil else rs.flatMap(resolvers)
	def resolvers(r: DependencyResolver): Seq[DependencyResolver] =
		r match { case c: ChainResolver => flatten(castResolvers(c.getResolvers)); case _ => r :: Nil }

	// cast the contents of a pre-generics collection
	private def castResolvers(s: java.util.Collection[_]): Seq[DependencyResolver] =
		s.toArray.map(_.asInstanceOf[DependencyResolver])

	def toID(name: String) = checkID(name.filter(isValidIDCharacter).mkString, name)
	def isValidIDCharacter(c: Char) = c.isLetterOrDigit
	private def checkID(id: String, name: String) = if(id.isEmpty) error("Could not convert '" + name + "' to an ID") else id
	def mavenRepository(repo: MavenRepository): XNode =
		mavenRepository(toID(repo.name), repo.name, repo.root)
	def mavenRepository(id: String, name: String, root: String): XNode =
		<repository>
			<id>{id}</id>
			<name>{name}</name>
			<url>{root}</url>
			<layout>{ if(name == JavaNet1Repository.name) "legacy" else "default" }</layout>
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
