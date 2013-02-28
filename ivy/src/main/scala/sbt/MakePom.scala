/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */

// based on Ivy's PomModuleDescriptorWriter, which is Apache Licensed, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package sbt

import java.io.File
// Node needs to be renamed to XNode because the task subproject contains a Node type that will shadow
// scala.xml.Node when generating aggregated API documentation
import scala.xml.{Elem, Node => XNode, NodeSeq, PrettyPrinter, PrefixedAttribute}
import Configurations.Optional

import org.apache.ivy.{core, plugins, Ivy}
import core.settings.IvySettings
import core.module.descriptor.{DependencyArtifactDescriptor, DependencyDescriptor, License, ModuleDescriptor, ExcludeRule}
import plugins.resolver.{ChainResolver, DependencyResolver, IBiblioResolver}

class MakePom(val log: Logger)
{
	@deprecated("Use `write(Ivy, ModuleDescriptor, ModuleInfo, Option[Iterable[Configuration]], Set[String], NodeSeq, XNode => XNode, MavenRepository => Boolean, Boolean, File)` instead", "0.11.2")
	def write(ivy: Ivy, module: ModuleDescriptor, moduleInfo: ModuleInfo, configurations: Option[Iterable[Configuration]], extra: NodeSeq, process: XNode => XNode, filterRepositories: MavenRepository => Boolean, allRepositories: Boolean, output: File): Unit =
		write(ivy, module, moduleInfo: ModuleInfo, configurations: Option[Iterable[Configuration]], Set(Artifact.DefaultType), extra, process, filterRepositories, allRepositories, output)
	def write(ivy: Ivy, module: ModuleDescriptor, moduleInfo: ModuleInfo, configurations: Option[Iterable[Configuration]], includeTypes: Set[String], extra: NodeSeq, process: XNode => XNode, filterRepositories: MavenRepository => Boolean, allRepositories: Boolean, output: File): Unit =
		write(process(toPom(ivy, module, moduleInfo, configurations, includeTypes, extra, filterRepositories, allRepositories)), output)
	// use \n as newline because toString uses PrettyPrinter, which hard codes line endings to be \n
	def write(node: XNode, output: File): Unit = write(toString(node), output, "\n")
	def write(xmlString: String, output: File, newline: String)
	{
		IO.write(output, "<?xml version='1.0' encoding='" + IO.utf8.name + "'?>" + newline + xmlString)
	}

	def toString(node: XNode): String = new PrettyPrinter(1000, 4).format(node)
	@deprecated("Use `toPom(Ivy, ModuleDescriptor, ModuleInfo, Option[Iterable[Configuration]], Set[String], NodeSeq, MavenRepository => Boolean, Boolean)` instead", "0.11.2")
	def toPom(ivy: Ivy, module: ModuleDescriptor, moduleInfo: ModuleInfo, configurations: Option[Iterable[Configuration]], extra: NodeSeq, filterRepositories: MavenRepository => Boolean, allRepositories: Boolean): XNode =
		toPom(ivy, module, moduleInfo, configurations, Set(Artifact.DefaultType), extra, filterRepositories, allRepositories)
	def toPom(ivy: Ivy, module: ModuleDescriptor, moduleInfo: ModuleInfo, configurations: Option[Iterable[Configuration]], includeTypes: Set[String], extra: NodeSeq, filterRepositories: MavenRepository => Boolean, allRepositories: Boolean): XNode =
		(<project xmlns="http://maven.apache.org/POM/4.0.0"  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
			<modelVersion>4.0.0</modelVersion>
			{ makeModuleID(module) }
			<name>{moduleInfo.nameFormal}</name>
			{ makeStartYear(moduleInfo) }
			{ makeOrganization(moduleInfo) }
			{ makeScmInfo(moduleInfo) }
			{ extra }
			{
				val deps = depsInConfs(module, configurations)
				makeProperties(module, deps) ++
				makeDependencies(deps, includeTypes)
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

	def makeStartYear(moduleInfo: ModuleInfo): NodeSeq =
		moduleInfo.startYear match {
			case Some(y) => <inceptionYear>{y}</inceptionYear>
			case _ => NodeSeq.Empty
		}
	def makeOrganization(moduleInfo: ModuleInfo): NodeSeq =
	{
		<organization>
			<name>{moduleInfo.organizationName}</name>
			{ moduleInfo.organizationHomepage match {
				case Some(h) => <url>{h}</url>
				case _ => NodeSeq.Empty
			}}
		</organization>
	}
	def makeScmInfo(moduleInfo: ModuleInfo): NodeSeq =
	{
		moduleInfo.scmInfo match {
			case Some(s) =>
				<scm>
					<url>{s.browseUrl}</url>
					<connection>{s.connection}</connection>
					{s.devConnection match {
						case Some(d) => <developerConnection>{d}</developerConnection>
						case _ => NodeSeq.Empty
					}}
				</scm>
			case _ => NodeSeq.Empty
		}
	}
	def makeProperties(module: ModuleDescriptor, dependencies: Seq[DependencyDescriptor]): NodeSeq =
	{
		val extra = IvySbt.getExtraAttributes(module)
		val depExtra = CustomPomParser.writeDependencyExtra(dependencies).mkString("\n")
		val allExtra = if(depExtra.isEmpty) extra else extra.updated(CustomPomParser.ExtraAttributesKey, depExtra)
		if(allExtra.isEmpty) NodeSeq.Empty else makeProperties(allExtra)
	}
	def makeProperties(extra: Map[String,String]): NodeSeq = {
		def _extraAttributes(k: String) = if (k == CustomPomParser.ExtraAttributesKey) xmlSpacePreserve else scala.xml.Null
		<properties> {
			for( (key,value) <- extra ) yield
				(<x>{value}</x>).copy(label = key, attributes = _extraAttributes(key))
		} </properties>
	}

	/**
	 * Attribute tag that PrettyPrinter won't ignore, saying "don't mess with my spaces"
	 * Without this, PrettyPrinter will flatten multiple entries for ExtraDependencyAttributes and make them
	 * unparseable. (e.g. a plugin that depends on multiple plugins will fail)
	 */
	def xmlSpacePreserve = new PrefixedAttribute("xml", "space", "preserve", scala.xml.Null)

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

	def makeDependencies(dependencies: Seq[DependencyDescriptor], includeTypes: Set[String]): NodeSeq =
		if(dependencies.isEmpty)
			NodeSeq.Empty
		else
			<dependencies>
				{ dependencies.map(makeDependency(_, includeTypes)) }
			</dependencies>

	def makeDependency(dependency: DependencyDescriptor, includeTypes: Set[String]): NodeSeq =
	{
		val artifacts = dependency.getAllDependencyArtifacts
		val includeArtifacts = artifacts.filter(d => includeTypes(d.getType))
		if(artifacts.isEmpty) {
			val (scope, optional) = getScopeAndOptional(dependency.getModuleConfigurations)
			makeDependencyElem(dependency, scope, optional, None, None)
		}
		else if(includeArtifacts.isEmpty)
			NodeSeq.Empty
		else
			NodeSeq.fromSeq(artifacts.map( a => makeDependencyElem(dependency, a) ))
	}

	def makeDependencyElem(dependency: DependencyDescriptor, artifact: DependencyArtifactDescriptor): Elem =
	{
		val configs = artifact.getConfigurations.toList match {
			case Nil | "*" :: Nil => dependency.getModuleConfigurations
			case x => x.toArray
		}
		val (scope, optional) = getScopeAndOptional(configs)
		makeDependencyElem(dependency, scope, optional, artifactClassifier(artifact), artifactType(artifact))
	}
	def makeDependencyElem(dependency: DependencyDescriptor, scope: Option[String], optional: Boolean, classifier: Option[String], tpe: Option[String]): Elem =
	{
		val mrid = dependency.getDependencyRevisionId
		<dependency>
			<groupId>{mrid.getOrganisation}</groupId>
			<artifactId>{mrid.getName}</artifactId>
			<version>{mrid.getRevision}</version>
			{ scopeElem(scope) }
			{ optionalElem(optional) }
			{ classifierElem(classifier) }
			{ typeElem(tpe) }
			{ exclusions(dependency) }
		</dependency>
	}

	@deprecated("No longer used and will be removed.", "0.12.1")
	def classifier(dependency: DependencyDescriptor, includeTypes: Set[String]): NodeSeq =
	{
		val jarDep = dependency.getAllDependencyArtifacts.filter(d => includeTypes(d.getType)).headOption
		jarDep match {
			case Some(a) => classifierElem(artifactClassifier(a))
			case None => NodeSeq.Empty
		}
	}
	def artifactType(artifact: DependencyArtifactDescriptor): Option[String] =
		Option(artifact.getType).flatMap { tpe => if(tpe == "jar") None else Some(tpe) }
	def typeElem(tpe: Option[String]): NodeSeq =
		tpe match {
			case Some(t) => <type>{t}</type>
			case None => NodeSeq.Empty
		}
				
	def artifactClassifier(artifact: DependencyArtifactDescriptor): Option[String] =
		Option(artifact.getExtraAttribute("classifier"))
	def classifierElem(classifier: Option[String]): NodeSeq =
		classifier match {
			case Some(c) => <classifier>{c}</classifier>
			case None => NodeSeq.Empty
		}

	@deprecated("No longer used and will be removed.", "0.12.1")
	def scopeAndOptional(dependency: DependencyDescriptor): NodeSeq  =
	{
		val (scope, opt) = getScopeAndOptional(dependency.getModuleConfigurations)
		scopeElem(scope) ++ optionalElem(opt)
	}
	def scopeElem(scope: Option[String]): NodeSeq = scope match {
		case None | Some(Configurations.Compile.name) => NodeSeq.Empty
		case Some(s) => <scope>{s}</scope>
	}
	def optionalElem(opt: Boolean)  =  if(opt) <optional>true</optional> else NodeSeq.Empty
	def moduleDescriptor(module: ModuleDescriptor) = module.getModuleRevisionId

	def getScopeAndOptional(confs: Array[String]): (Option[String], Boolean) =
	{
		val (opt, notOptional) = confs.partition(_ == Optional.name)
		val defaultNotOptional = Configurations.defaultMavenConfigurations.find(notOptional contains _.name)
		val scope = defaultNotOptional.map(_.name)
		(scope, !opt.isEmpty)
	}

	def exclusions(dependency: DependencyDescriptor): NodeSeq =
	{
		val excl = dependency.getExcludeRules(dependency.getModuleConfigurations)
		val (warns, excls) = IvyUtil.separate(excl.map(makeExclusion))
		if(!warns.isEmpty) log.warn(warns.mkString(IO.Newline))
		if(!excls.isEmpty) <exclusions>{excls}</exclusions>
		else NodeSeq.Empty
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
	private def checkID(id: String, name: String) = if(id.isEmpty) sys.error("Could not convert '" + name + "' to an ID") else id
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
