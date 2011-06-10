/* sbt -- Simple Build Tool
 * Copyright 2010, 2011 Mark Harrah
 */
package object sbt extends sbt.std.TaskExtra with sbt.Types with sbt.ProcessExtra with sbt.impl.DependencyBuilders
	with sbt.PathExtra with sbt.ProjectExtra with sbt.DependencyFilterExtra with sbt.BuildExtra
{
	type Setting[T] = Project.Setting[T]
	type ScopedKey[T] = Project.ScopedKey[T]
	type SettingsDefinition = Project.SettingsDefinition
	type File = java.io.File
	type URI = java.net.URI
	type URL = java.net.URL

	implicit def maybeToOption[S](m: xsbti.Maybe[S]): Option[S] =
		if(m.isDefined) Some(m.get) else None
	def uri(s: String): URI = new URI(s)
	def file(s: String): File = new File(s)
	def url(s: String): URL = new URL(s)
	
	def ThisScope = Scope.ThisScope
	def GlobalScope = Scope.GlobalScope

		import sbt.{Configurations => C}
	def Compile = C.Compile
	def Test = C.Test
	def Runtime = C.Runtime
	def IntegrationTest = C.IntegrationTest
	def Default = C.Default
	def Javadoc = C.Javadoc
	def Sources = C.Sources
	def Provided = C.Provided
// java.lang.System is more important, so don't alias this one
//	def System = C.System
	def Optional = C.Optional
	def config(s: String): Configuration = Configurations.config(s)
}
