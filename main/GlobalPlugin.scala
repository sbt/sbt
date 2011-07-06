package sbt

	import Load._
	import Project._
	import Scoped._
	import Keys._
	import Configurations.Compile
	import java.io.File
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId

object GlobalPlugin
{
		// constructs a sequence of settings that may be appended to a project's settings to
		//  statically add the global plugin as a classpath dependency.
		//  static here meaning that the relevant tasks for the global plugin have already been evaluated
	def inject(gp: GlobalPluginData): Seq[Setting[_]] =
		Seq[Setting[_]](
			projectDescriptors ~= { _ ++ gp.descriptors },
			projectDependencies ++= gp.projectID +: gp.dependencies,
			internalDependencyClasspath in Compile ~= { prev => (prev ++ gp.internalClasspath).distinct }
		)
	
	def build(base: File, s: State, config: LoadBuildConfiguration): BuildStructure =  Load(base, s, config)._2
	def load(base: File, s: State, config: LoadBuildConfiguration): GlobalPlugin =
	{
		val structure = build(base, s, config)
		val data = extract(s, structure)
		GlobalPlugin(data, structure, inject(data))
	}
	def extract(state: State, structure: BuildStructure): GlobalPluginData =
	{
		import structure.{data, root}
		val p = RootProject(root)
		val task = (projectID in p, projectDependencies in p, projectDescriptors in p, fullClasspath in (p, Compile), internalDependencyClasspath in (p, Compile) ) map GlobalPluginData.apply get data
		evaluate(state, structure, task)
	}
	def evaluate[T](state: State, structure: BuildStructure, t: Task[T]): T =
	{
			import EvaluateTask._
		val log = CommandSupport.logger(state)
		withStreams(structure) { str =>
			val nv = nodeView(state, str)
			processResult(runTask(t, str, structure.index.triggers)(nv), log)
		}
	}
}
final case class GlobalPluginData(projectID: ModuleID, dependencies: Seq[ModuleID], descriptors: Map[ModuleRevisionId, ModuleDescriptor], fullClasspath: Seq[Attributed[File]], internalClasspath: Seq[Attributed[File]])
final case class GlobalPlugin(data: GlobalPluginData, structure: BuildStructure, inject: Seq[Setting[_]])
