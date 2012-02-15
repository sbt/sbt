package sbt

	import Keys.Classpath
	import Project.Setting

final case class PluginManagement(overrides: Set[ModuleID], applyOverrides: Set[ModuleID], loader: ClassLoader, initialLoader: ClassLoader)
{
	def shift: PluginManagement =
		PluginManagement(Set.empty, overrides, initialLoader, initialLoader)

	def addOverrides(os: Set[ModuleID]): PluginManagement =
		copy(overrides = overrides ++ os)

	def addOverrides(cp: Classpath): PluginManagement =
		addOverrides(PluginManagement extractOverrides cp)

	def inject: Seq[Setting[_]] = Seq(
		Keys.dependencyOverrides ++= overrides
	)
}
object PluginManagement
{
	def apply(initialLoader: ClassLoader): PluginManagement = PluginManagement(Set.empty, Set.empty, initialLoader, initialLoader)

	def extractOverrides(classpath: Classpath): Set[ModuleID] =
		classpath flatMap { _.metadata get Keys.moduleID.key map keepOverrideInfo } toSet;

	def keepOverrideInfo(m: ModuleID): ModuleID =
		ModuleID(m.organization, m.name, m.revision, crossVersion = m.crossVersion)
}
