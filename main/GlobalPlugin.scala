package sbt

	import Load._
	import Project._
	import Scoped._
	import Keys._
	import Configurations.Runtime
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
			resolvers <<= resolvers { rs => (rs ++ gp.resolvers).distinct },
			internalDependencyClasspath in Runtime ~= { prev => (prev ++ gp.internalClasspath).distinct }
		)
	
	def build(base: File, s: State, config: LoadBuildConfiguration): (BuildStructure, State) =
	{
		val globalConfig = config.copy(injectSettings = config.injectSettings.copy(global = config.injectSettings.global ++ globalPluginSettings))
		val (eval, structure) = Load(base, s, globalConfig)
		val session = Load.initialSession(structure, eval)
		(structure, Project.setProject(session, structure, s))
	}
	def load(base: File, s: State, config: LoadBuildConfiguration): GlobalPlugin =
	{
		val (structure, state) = build(base, s, config)
		val (newS, data) = extract(state, structure)
		Project.runUnloadHooks(newS) // discard state
		GlobalPlugin(data, structure, inject(data), base)
	}
	def extract(state: State, structure: BuildStructure): (State, GlobalPluginData) =
	{
		import structure.{data, root, rootProject}
		val p: Scope = Scope.GlobalScope in ProjectRef(root, rootProject(root))
		val taskInit = (projectID, projectDependencies, projectDescriptors, resolvers, fullClasspath in Runtime, internalDependencyClasspath in Runtime, exportedProducts in Runtime, ivyModule) map {
			(pid, pdeps, pdescs, rs, cp, intcp, prods, mod) =>
				val depMap = pdescs + mod.dependencyMapping(state.log)
				GlobalPluginData(pid, pdeps, depMap, rs, cp, prods ++ intcp)
		}
		val task = taskInit mapReferenced Project.mapScope(Scope replaceThis p) evaluate data
		evaluate(state, structure, task)
	}
	def evaluate[T](state: State, structure: BuildStructure, t: Task[T]): (State, T) =
	{
			import EvaluateTask._
		withStreams(structure) { str =>
			val nv = nodeView(state, str)
			val (newS, result) = runTask(t, state, str, structure.index.triggers)(nv)
			(newS, processResult(result, newS.log))
		}
	}
	val globalPluginSettings = inScope(Scope.GlobalScope in LocalRootProject)(Seq(
		organization := "org.scala-tools.sbt",
		onLoadMessage <<= Keys.baseDirectory("Loading global plugins from " + _),
		name := "global-plugin",
		sbtPlugin := true,
		version := "0.0"
	))
}
final case class GlobalPluginData(projectID: ModuleID, dependencies: Seq[ModuleID], descriptors: Map[ModuleRevisionId, ModuleDescriptor], resolvers: Seq[Resolver], fullClasspath: Classpath, internalClasspath: Classpath)
final case class GlobalPlugin(data: GlobalPluginData, structure: BuildStructure, inject: Seq[Setting[_]], base: File)
