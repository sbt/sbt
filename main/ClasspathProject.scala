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
	import scala.xml.NodeSeq
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

	val updateConfig: Task[UpdateConfiguration]

	lazy val ivySbt: Task[IvySbt] =
		ivyConfiguration map { conf => new IvySbt(conf) }

	lazy val ivyModule: Task[IvySbt#Module] =
		(ivySbt, moduleSettings) map { (ivySbt: IvySbt, settings: ModuleSettings) =>
			new ivySbt.Module(settings)
		}

	def classpathFilter: FileFilter = GlobFilter("*.jar")
	def defaultExcludes: FileFilter

	override val managedClasspath: Classpath =
		TaskMap { configuration =>
			update map { x => attributed(x.getOrElse(configuration, error("No such configuration '" + configuration.toString + "'")) ) }
		}

	val unmanagedClasspath: Classpath =
		TaskMap { configuration =>
			unmanagedBase map { base =>
				attributed( (base * (classpathFilter -- defaultExcludes) +++
				(base / configuration.toString).descendentsExcept(classpathFilter, defaultExcludes)).getFiles.toSeq )
			}
		}
		
	import Types._
	lazy val update = (ivyModule, updateConfig) map { case module :+: config :+: HNil =>
		val confMap = configurationMap
		IvyActions.update(module, config) map { case (key, value) => (confMap(key), value) } toMap;
	}
}

trait DefaultClasspathProject extends BasicClasspathProject with Project
{
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

	def libraryDependencies: Iterable[ModuleID] = ReflectUtilities.allVals[ModuleID](this).map(_._2)

	def managedDependencyPath: Path = info.projectDirectory / "lib_managed"
	def dependencyPath: Path = info.projectDirectory / "lib"

	def ivyXML: NodeSeq = NodeSeq.Empty
	def defaultConfiguration: Option[Configuration] = None
	def ivyScala: Option[IvyScala] = None
	def ivyValidate: Boolean = false

	lazy val internalDependencyClasspath: Classpath = internalDependencies(this)

	lazy val unmanagedBase = task { dependencyPath.asFile }

	lazy val moduleSettings: Task[ModuleSettings] = task {
		new InlineConfiguration(projectID, libraryDependencies, ivyXML, configurations, defaultConfiguration, ivyScala, ivyValidate)
	}
}
trait MultiClasspathProject extends DefaultClasspathProject
{
	def dependencies: Iterable[ProjectDependency.Classpath]
	def name: String
	def organization: String
	def version: String

	def projectDependencies: Iterable[ModuleID] =
		resolvedDependencies(this) collect { case (p: DefaultClasspathProject, conf) => p.projectID.copy(configurations = conf) }

	lazy val projectResolver =
		depMap(this) map { m =>
			new RawRepository(new ProjectResolver("inter-project", m))
		}

	override def projectID = ModuleID(organization, name, version)
	override def libraryDependencies: Iterable[ModuleID] = super.libraryDependencies ++ projectDependencies
	
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
/*	def consoleTask(compileTask: Task[Analysis], inputTask: Task[Compile.Inputs]): Task[Unit] =
	{
		(compileTask, inputTask) map { (analysis, in) =>
			val console = new Console(in.compilers.scalac)
			console()
		}
	}
*/
	type Classpath = Configuration => Task[Seq[Attributed[File]]]
	
	val Analyzed = AttributeKey[Analysis]("analysis")
	
	def attributed[T](in: Seq[T]): Seq[Attributed[T]] = in map Attributed.blank
	
	def analyzed[T](data: T, analysis: Analysis) = Attributed.blank(data).put(Analyzed, analysis) 
	
	def analyzed(compile: Task[Analysis], inputs: Task[Compile.Inputs]): Task[Attributed[File]] =
		(compile, inputs) map { case analysis :+: i :+: HNil =>
			analyzed(i.config.classesDirectory, analysis)
		}

	def concat[A]: (Seq[A], Seq[A]) => Seq[A] = _ ++ _

	def extractAnalysis[T](a: Attributed[T]): (T, Analysis) = 
		(a.data, a.metadata get Analyzed getOrElse Analysis.Empty)

	def analysisMap[T](cp: Seq[Attributed[T]]): Map[T, Analysis] =
		(cp map extractAnalysis).toMap

	def data[T](in: Seq[Attributed[T]]): Seq[T] = in.map(_.data)

	def depMap(root: Project): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
		depMap(MultiProject.topologicalSort(root).dropRight(1) collect { case cp: DefaultClasspathProject => cp })

	def depMap(projects: Seq[DefaultClasspathProject]): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
		projects.map( _.ivyModule ).join.map { mods =>
			(mods.map{ mod =>
				val md = mod.moduleDescriptor
				(md.getModuleRevisionId, md)
			}).toMap
		}

	def resolvedDependencies(p: Project): Iterable[(Project, Option[String])] =
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
				for( (dep, confMapping) <- ClasspathProject.resolvedDependencies(p))
				{
					val depConf = mapped(c, confMapping, defaultConfiguration(dep).toString) { missingMapping(p.name, dep.name, c) }
					val unvisited = visited.add( (dep, depConf) )
					if(unvisited) visit(dep, depConf)
				}
			}
			visit(project, conf.toString)

			val productsTasks = new LinkedHashSet[Task[Seq[Attributed[File]]]]
			for( (dep: ClasspathProject, conf) <- visited )
				if(dep ne project) productsTasks += products(dep, conf)
			(productsTasks.toSeq.join) named(project.name + "/join") map(_.flatten) 
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
		 error("Configuration '" + conf + "' not defined in '" + in)
	def products(dep: ClasspathProject, conf: String) =
		dep.products(dep.configurationMap.getOrElse(conf,missingConfiguration(dep.name, conf)))
	def defaultConfiguration(p: Project): Configuration =
		p match
		{
			case cp: ClasspathProject => cp.defaultConfiguration getOrElse Configurations.Default
			case _ => Configurations.Default
		}
}