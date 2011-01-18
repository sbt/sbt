/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import std._
	import inc.Analysis
	import TaskExtra._
	import ClasspathProject._
	import java.io.File
	import Path._	
	import Types._
	import scala.xml.{Node => XNode,NodeSeq}
	import scala.collection.mutable.{LinkedHashMap, LinkedHashSet}

trait ClasspathProject
{
	def name: String
	def configurations: Seq[Configuration]
	def defaultConfiguration: Option[Configuration]

	val products: Classpath
	val unmanagedClasspath: Classpath
	val managedClasspath: Classpath
	val internalDependencyClasspath: Classpath

	lazy val externalDependencyClasspath: Classpath =
		TaskMap { (configuration: Configuration) =>
			val un = unmanagedClasspath(configuration)
			val m = managedClasspath(configuration)
			(un, m) map concat[Attributed[File]]
		}

	lazy val dependencyClasspath: Classpath =
		TaskMap { (configuration: Configuration) =>
			val external = externalDependencyClasspath(configuration)
			val internal = internalDependencyClasspath(configuration)
			(external, internal) map concat[Attributed[File]]
		}
	lazy val fullClasspath: Classpath =
		TaskMap { case (configuration: Configuration) =>
			val dep = dependencyClasspath(configuration)
			val prod = products(configuration)
			(dep, prod) map concat[Attributed[File]]
		}

	lazy val configurationMap: Map[String, Configuration] = 
		configurations map { conf => (conf.name, conf) } toMap;
}

trait BasicClasspathProject extends ClasspathProject
{
	val ivyConfiguration: Task[IvyConfiguration]
	val moduleSettings: Task[ModuleSettings]
	val unmanagedBase: Task[File]
	def cacheDirectory: File

	val updateConfig: Task[UpdateConfiguration]

	lazy val ivySbt: Task[IvySbt] =
		ivyConfiguration map { conf => new IvySbt(conf) }

	lazy val ivyModule: Task[IvySbt#Module] =
		(ivySbt, moduleSettings) map { (ivySbt: IvySbt, settings: ModuleSettings) =>
			new ivySbt.Module(settings)
		}

	def classpathFilter: FileFilter = GlobFilter("*.jar")
	def defaultExcludes: FileFilter

	override lazy val managedClasspath: Classpath =
		TaskMap { configuration =>
			update map { x => attributed(x.getOrElse(configuration, error("No such configuration '" + configuration.toString + "'")) ) }
		}

	lazy val unmanagedClasspath: Classpath =
		TaskMap { configuration =>
			unmanagedBase map { base =>
				attributed( (base * (classpathFilter -- defaultExcludes) +++
				(base / configuration.toString).descendentsExcept(classpathFilter, defaultExcludes)).getFiles.toSeq )
			}
		}
		
	lazy val update = (ivyModule, updateConfig) map cachedUpdate(cacheDirectory / "update", configurationMap)
}
trait PublishProject extends BasicClasspathProject
{
	val publishConfig: Task[PublishConfiguration]
	val publishLocalConfig: Task[PublishConfiguration]
	val makePomConfig: Task[MakePomConfiguration]
	def packageToPublish: Seq[Task[_]]

	def publishMavenStyle = true
	def deliverDepends = if(publishMavenStyle) makePom :: Nil else packageToPublish

	lazy val makePom = (ivyModule, makePomConfig) map(IvyActions.makePom) dependsOn( packageToPublish : _*)
	lazy val deliver = (ivyModule, publishConfig) map cachedPublish(cacheDirectory / "deliver")(IvyActions.deliver) dependsOn(deliverDepends : _*)
	lazy val deliverLocal = (ivyModule, publishLocalConfig) map cachedPublish(cacheDirectory / "deliver-local")(IvyActions.deliver) dependsOn(deliverDepends : _*)
	lazy val publish = (ivyModule, publishConfig) map cachedPublish(cacheDirectory / "publish")(IvyActions.publish) dependsOn(deliver)
	lazy val publishLocal = (ivyModule, publishLocalConfig) map cachedPublish(cacheDirectory / "publish-local")(IvyActions.publish) dependsOn(deliverLocal)
}

trait DefaultClasspathProject extends BasicClasspathProject with PublishProject with Project
{
	def outputDirectory: Path
	def projectID: ModuleID
	def baseResolvers: Seq[Resolver]
	lazy val resolvers: Task[Seq[Resolver]] = task { baseResolvers }

	def otherResolvers: Seq[Resolver] = Nil
	def moduleConfigurations: Seq[ModuleConfiguration] = Nil

	def retrievePattern = "[type]/[organisation]/[module]/[artifact](-[revision])(-[classifier]).[ext]"
	override lazy val updateConfig: Task[UpdateConfiguration] = retrieveConfig map { rConf =>
		new UpdateConfiguration(rConf, UpdateLogging.Quiet)
	}
	lazy val retrieveConfig: Task[Option[RetrieveConfiguration]] = task {
		None//Some(new RetrieveConfiguration(managedDependencyPath asFile, retrievePattern, true))
	}

	def offline: Boolean = false
	def paths: IvyPaths = new IvyPaths(info.projectDirectory, None)
	
	lazy val ivyConfiguration: Task[IvyConfiguration] = resolvers map { rs =>
		 // TODO: log should be passed directly to IvyActions and pulled from Streams
		new InlineIvyConfiguration(paths, rs, otherResolvers, moduleConfigurations, offline, Some(info.globalLock), ConsoleLogger())
	}

	def libraryDependencies: Seq[ModuleID] = ReflectUtilities.allVals[ModuleID](this).toSeq.map(_._2)

	def managedDependencyPath: Path = info.projectDirectory / "lib_managed"
	def dependencyPath: Path = info.projectDirectory / "lib"

	def ivyXML: NodeSeq = NodeSeq.Empty
	def defaultConfiguration: Option[Configuration] = None
	def ivyScala: Option[IvyScala] = None
	def ivyValidate: Boolean = false
	def moduleID = normalizedName

	def pomFile: File
	def publishTo: Resolver = error("Repository for publishing is not specified.")

	lazy val internalDependencyClasspath: Classpath = internalDependencies(this)

	lazy val unmanagedBase = task { dependencyPath.asFile }

	lazy val moduleSettings: Task[ModuleSettings] = task {
		new InlineConfiguration(projectID, libraryDependencies, ivyXML, configurations, defaultConfiguration, ivyScala, ivyValidate)
	}

	lazy val publishConfig = task { publishConfiguration( publishPatterns(outputDirectory), resolverName = publishTo.name ) }
	lazy val publishLocalConfig = task { publishConfiguration( publishPatterns(outputDirectory, true) ) }
	lazy val makePomConfig = task { makePomConfiguration(pomFile) }
}
trait MultiClasspathProject extends DefaultClasspathProject
{
	def dependencies: Seq[ProjectDependency.Classpath]
	def name: String
	def organization: String
	def version: String
	def pomFile: File = outputDirectory / (moduleID + "-" + version + ".pom")
	def artifacts: Seq[Artifact] = Nil

	def projectDependencies: Seq[ModuleID] =
		resolvedDependencies(this) collect { case (p: DefaultClasspathProject, conf) => p.projectID.copy(configurations = conf) }

	lazy val projectResolver =
		depMap(this) map { m =>
			new RawRepository(new ProjectResolver("inter-project", m))
		}

	override def projectID = ModuleID(organization, moduleID, version).cross(true).artifacts(artifacts.toSeq : _*)
	override def libraryDependencies: Seq[ModuleID] = super.libraryDependencies ++ projectDependencies
	
	override lazy val resolvers: Task[Seq[Resolver]] = projectResolver map { _ +: baseResolvers }
}

trait ReflectiveClasspathProject extends DefaultClasspathProject
{
	private[this] def vals[T: Manifest] = ReflectUtilities.allVals[T](this).toSeq.map(_._2)
	def configurations: Seq[Configuration] = vals[Configuration] ++ Configurations.defaultMavenConfigurations
	def baseResolvers: Seq[Resolver] = Resolver.withDefaultResolvers(vals[Resolver] )
}

	import org.apache.ivy.core.module
	import module.id.ModuleRevisionId
	import module.descriptor.ModuleDescriptor

object ClasspathProject
{
	type Classpath = Configuration => Task[Seq[Attributed[File]]]
	
	val Analyzed = AttributeKey[Analysis]("analysis")
	
	def attributed[T](in: Seq[T]): Seq[Attributed[T]] = in map Attributed.blank
	
	def analyzed[T](data: T, analysis: Analysis) = Attributed.blank(data).put(Analyzed, analysis) 
	
	def analyzed(compile: Task[Analysis], inputs: Task[Compile.Inputs]): Task[Attributed[File]] =
		(compile, inputs) map { case analysis :+: i :+: HNil =>
			analyzed(i.config.classesDirectory, analysis)
		}

	def makeProducts(compile: Task[Analysis], inputs: Task[Compile.Inputs], name: String, prefix: String): Task[Seq[Attributed[File]]] =
	{
		def mkName(postfix: String) = name + "/" + prefix + postfix
		analyzed(compile, inputs) named(mkName("analyzed")) map { _ :: Nil } named(mkName("products"))
	}

	def concat[A]: (Seq[A], Seq[A]) => Seq[A] = _ ++ _

	def extractAnalysis[T](a: Attributed[T]): (T, Analysis) = 
		(a.data, a.metadata get Analyzed getOrElse Analysis.Empty)

	def analysisMap[T](cp: Seq[Attributed[T]]): Map[T, Analysis] =
		(cp map extractAnalysis).toMap

	def data[T](in: Seq[Attributed[T]]): Seq[T] = in.map(_.data)
	def taskData[T](in: Task[Seq[Attributed[T]]]): Task[Seq[T]] = in map data

	def depMap(root: Project): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
		depMap(MultiProject.topologicalSort(root).dropRight(1) collect { case cp: DefaultClasspathProject => cp })

	def depMap(projects: Seq[DefaultClasspathProject]): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
		projects.map( _.ivyModule ).join.map { mods =>
			(mods.map{ mod =>
				val md = mod.moduleDescriptor
				(md.getModuleRevisionId, md)
			}).toMap
		}

	def resolvedDependencies(p: Project): Seq[(Project, Option[String])] =
		p.dependencies map { cp =>
			(resolveProject(cp.project, p), cp.configuration)
		}
		
	def resolveProject(e: Either[File, Project], context: Project): Project =
		e match {
			case Left(extPath) => context.info.externals(extPath)
			case Right(proj) => proj
		}

	def mapped(c: String, mapping: Option[String], default: String)(errMsg: => String): String =
		parseSimpleConfigurations(mapping getOrElse default).getOrElse(c, errMsg)
	def internalDependencies(project: Project): Classpath =
		TaskMap { (conf: Configuration) =>
			val visited = new LinkedHashSet[(Project,String)]
			def visit(p: Project, c: String)
			{
				val applicableConfigs = allConfigs(p, c)
				for(ac <- applicableConfigs)
					visited add (p, ac)

				for( (dep, confMapping) <- resolvedDependencies(p))
				{
					val depConf = mapped(c, confMapping, defaultConfiguration(dep).toString) { missingMapping(p.name, dep.name, c) }
					if( ! visited( (dep, depConf) ) )
						visit(dep, depConf)
				}
			}
			visit(project, conf.name)

			val productsTasks = new LinkedHashSet[Task[Seq[Attributed[File]]]]
			for( (dep: ClasspathProject, c) <- visited )
				if( (dep ne project) || conf.name != c )
					productsTasks += products(dep, c)

			def name(action: String) = project.name + "/" + conf + "/" + action + "-products"
			(productsTasks.toSeq.join) named(name("join")) map(_.flatten) named(name("flatten"))
		}

	def parseSimpleConfigurations(confString: String): Map[String, String] =
		confString.split(";").flatMap( conf =>
			trim(conf.split("->",2)) match {
				case x :: Nil => for(a <- parseList(x)) yield (a,a)
				case x :: y :: Nil => for(a <- parseList(x); b <- parseList(x)) yield (a,b)
				case _ => error("Invalid configuration '" + conf + "'") // shouldn't get here
			}
		).toMap

	def parseList(s: String): Seq[String] = trim(s split ",")
	private def trim(a: Array[String]): List[String] = a.toList.map(_.trim)
	
	def missingMapping(from: String, to: String, conf: String) = 
		error("No configuration mapping defined from '" + from + "' to '" + to + "' for '" + conf + "'")
	def missingConfiguration(in: String, conf: String) =
		 error("Configuration '" + conf + "' not defined in '" + in + "'")
	def allConfigs(dep: Project, conf: String): Seq[String] =
		dep match {
			case cp: ClasspathProject => Dag.topologicalSort(configuration(cp, conf))(_.extendsConfigs).map(_.name)
			case _ => Nil
		}
	def configuration(dep: ClasspathProject, conf: String): Configuration =
		dep.configurationMap.getOrElse(conf,missingConfiguration(dep.name, conf))
	def products(dep: ClasspathProject, conf: String) =
		dep.products(configuration(dep, conf))
	def defaultConfiguration(p: Project): Configuration =
		p match
		{
			case cp: ClasspathProject => cp.defaultConfiguration getOrElse Configurations.Default
			case _ => Configurations.Default
		}

	def makePomConfiguration(file: File, configurations: Option[Iterable[Configuration]] = None, extra: NodeSeq = NodeSeq.Empty, process: XNode => XNode = n => n, filterRepositories: MavenRepository => Boolean = _ => true) = 
		new MakePomConfiguration(file, configurations, extra, process, filterRepositories)

	def publishConfiguration(patterns: PublishPatterns, resolverName: String = "local", status: String = "release", logging: UpdateLogging.Value = UpdateLogging.DownloadOnly) =
	    new PublishConfiguration(patterns, status, resolverName, None, logging)

	def publishPatterns(outputPath: Path, publishIvy: Boolean = false): PublishPatterns =
	{
		val deliverPattern = (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath
		val srcArtifactPatterns: Seq[String] =
		{
			val pathPatterns =
				(outputPath / "[artifact]-[revision]-[type](-[classifier]).[ext]") ::
				(outputPath / "[artifact]-[revision](-[classifier]).[ext]") ::
				Nil
			pathPatterns.map(_.absolutePath)
		}
		new PublishPatterns( if(publishIvy) Some(deliverPattern) else None, srcArtifactPatterns)
	}

		import Cache._
		import Types._
		import CacheIvy.{classpathFormat, publishIC, updateIC}

	def cachedUpdate(cacheFile: File, configMap: Map[String, Configuration]): (IvySbt#Module :+: UpdateConfiguration :+: HNil) => Map[Configuration, Seq[File]] =
		{ case module :+: config :+: HNil =>
			implicit val updateCache = updateIC
			val f = cached(cacheFile) { (conf: IvyConfiguration, settings: ModuleSettings, config: UpdateConfiguration) =>
				println("Updating...")
				val r = IvyActions.update(module, config)
				println("Done updating.")
				r
			}
			val classpaths = f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)
			val confMap = configMap
			classpaths map { case (key, value) => (confMap(key), value) } toMap;
		}

	// can't cache deliver/publish easily since files involved are hidden behind patterns.  publish will be difficult to verify target-side anyway
	def cachedPublish(cacheFile: File)(g: (IvySbt#Module, PublishConfiguration) => Unit): (IvySbt#Module :+: PublishConfiguration :+: HNil) => Unit =
	{ case module :+: config :+: HNil =>
	/*	implicit val publishCache = publishIC
		val f = cached(cacheFile) { (conf: IvyConfiguration, settings: ModuleSettings, config: PublishConfiguration) =>*/
		    g(module, config)
		/*}
		f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)*/
	}
}