package sbt

	import std._
	import TaskExtra._
	import ClasspathProject._
	import java.io.File
	import Path._
	import scala.xml.NodeSeq

trait ClasspathProject
{
	def configurations: Seq[Configuration]
	def products(configuration: Configuration, intermediate: Boolean): Task[Seq[File]]
	def unmanagedClasspath(configuration: Configuration): Task[Seq[File]]
	def managedClasspath(configuration: Configuration): Task[Seq[File]]

	def dependencyClasspath(configuration: Configuration): Task[Seq[File]] =
		(unmanagedClasspath(configuration), managedClasspath(configuration)) map concat[File]

	def fullClasspath(configuration: Configuration, intermediate: Boolean): Task[Seq[File]] =
		(dependencyClasspath(configuration), products(configuration, intermediate) ) map concat[File]
}

trait BasicClasspathProject extends ClasspathProject
{
	val ivyConfiguration: Task[IvyConfiguration]
	val moduleSettings: Task[ModuleSettings]
	val unmanagedBase: Task[File]

	lazy val updateConfig: Task[UpdateConfiguration] = task {
		new UpdateConfiguration(null, null, true, UpdateLogging.Full)
	}

	lazy val ivySbt: Task[IvySbt] =
		ivyConfiguration map { conf => new IvySbt(conf) }

	lazy val ivyModule: Task[IvySbt#Module] =
		(ivySbt, moduleSettings) map { (ivySbt: IvySbt, settings: ModuleSettings) =>
			new ivySbt.Module(settings)
		}

	def classpathFilter: FileFilter = GlobFilter("*.jar")
	def defaultExcludeFilter: FileFilter = MultiProject.defaultExcludes

	override def managedClasspath(configuration: Configuration) =
		update map { _.getOrElse(configuration, error("No such configuration '" + configuration.toString + "'")) }

	def unmanagedClasspath(configuration: Configuration): Task[Seq[File]] =
		unmanagedBase map { base =>
			(base * (classpathFilter -- defaultExcludeFilter) +++
			(base / configuration.toString).descendentsExcept(classpathFilter, defaultExcludeFilter)).getFiles.toSeq
		}

	import Types._
	lazy val update = (ivyModule, updateConfig) map { case module :+: config :+: HNil =>
		val confMap = configurations map { conf => (conf.name, conf) } toMap;
		IvyActions.update(module, config) map { case (key, value) => (confMap(key), value) } toMap;
	}
}

trait DefaultClasspathProject extends BasicClasspathProject with Project
{
	def projectID: ModuleID
	def baseResolvers: Seq[Resolver] = Resolver.withDefaultResolvers(ReflectUtilities.allVals[Resolver](this).toSeq.map(_._2) )
	lazy val resolvers: Task[Seq[Resolver]] = task { baseResolvers }

	def otherResolvers: Seq[Resolver] = Nil
	def moduleConfigurations: Seq[ModuleConfiguration] = Nil

	def offline: Boolean = false
	def paths: IvyPaths = new IvyPaths(info.projectDirectory, None)
	
	lazy val ivyConfiguration: Task[IvyConfiguration] = resolvers map { rs =>
		 // TODO: log should be passed directly to IvyActions and pulled from Streams
		new InlineIvyConfiguration(paths, rs, otherResolvers, moduleConfigurations, offline, Some(info.globalLock), ConsoleLogger())
	}

	def libraryDependencies: Iterable[ModuleID] = ReflectUtilities.allVals[ModuleID](this).map(_._2)

	def dependencyPath: Path = info.projectDirectory / "lib"

	def ivyXML: NodeSeq = NodeSeq.Empty
	def defaultConfiguration: Option[Configuration] = None
	def ivyScala: Option[IvyScala] = None
	def ivyValidate: Boolean = false

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
		ClasspathProject.resolvedDependencies(this) collect { case (p: DefaultClasspathProject, conf) => p.projectID.copy(configurations = conf) }

	lazy val projectResolver =
		ClasspathProject.depMap(this) map { m =>
			new RawRepository(new ProjectResolver("inter-project", m))
		}

	override def projectID = ModuleID(organization, name, version)
	override def libraryDependencies: Iterable[ModuleID] = super.libraryDependencies ++ projectDependencies
	
	override lazy val resolvers: Task[Seq[Resolver]] = projectResolver map { _ +: baseResolvers }
}

	import org.apache.ivy.core.module
	import module.id.ModuleRevisionId
	import module.descriptor.ModuleDescriptor

object ClasspathProject
{
	def concat[A]: (Seq[A], Seq[A]) => Seq[A] = _ ++ _

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
	{
		import ProjectDependency.Classpath
		p.dependencies map {
			case Classpath(Left(extPath), conf) => (p.info.externals(extPath), conf)
			case Classpath(Right(proj), conf) => (proj, conf)
		}
	}

	def parseSimpleConfigurations(confString: String): Map[String, String] =
		confString.split(";").map( conf =>
			conf.split("->",2).toList.map(_.trim) match {
				case x :: Nil => (x,x)
				case x :: y :: Nil => (x,y)
				case _ => error("Invalid configuration '" + conf + "'") // shouldn't get here
			}
		).toMap
}