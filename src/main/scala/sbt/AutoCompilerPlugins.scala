/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

trait AutoCompilerPlugins extends BasicScalaProject
{
	import Configurations.CompilerPlugin
	abstract override def ivyConfigurations =
	{
		val superConfigurations = super.ivyConfigurations.toList
		val newConfigurations =
			if(superConfigurations.isEmpty)
			{
				if(useDefaultConfigurations)
					CompilerPlugin :: Configurations.defaultMavenConfigurations
				else
				 	Configurations.Default :: CompilerPlugin :: Nil
			}
			else
				CompilerPlugin :: superConfigurations
		log.debug("Auto configurations: " + newConfigurations.toList.mkString(", "))
		Configurations.removeDuplicates(newConfigurations)
	}
	abstract override def compileOptions = compilerPlugins ++ super.compileOptions

	/** A PathFinder that provides the classpath to search for compiler plugins. */
	def pluginClasspath = fullClasspath(CompilerPlugin)
	protected def compilerPlugins: List[CompileOption] =
		ClasspathUtilities.compilerPlugins(pluginClasspath.get).map(plugin => new CompileOption("-Xplugin:" + plugin.getAbsolutePath)).toList

	def compilerPlugin(dependency: ModuleID) = dependency % "plugin->default(compile)"
}