/* sbt -- Simple Build Tool
 * Copyright 2010, 2011 Mark Harrah
 */
package object sbt extends sbt.std.TaskExtra with sbt.Types with sbt.ProcessExtra with sbt.impl.DependencyBuilders
	with sbt.PathExtra with sbt.ProjectExtra with sbt.DependencyFilterExtra with sbt.BuildExtra with sbt.TaskMacroExtra
	with sbt.ScopeFilter.Make
{
	@deprecated("Renamed to CommandStrings.", "0.12.0")
	val CommandSupport = CommandStrings

	@deprecated("Use SettingKey, which is a drop-in replacement.", "0.11.1")
	type ScopedSetting[T] = SettingKey[T]
	@deprecated("Use TaskKey, which is a drop-in replacement.", "0.11.1")
	type ScopedTask[T] = TaskKey[T]
	@deprecated("Use InputKey, which is a drop-in replacement.", "0.11.1")
	type ScopedInput[T] = InputKey[T]

	type Setting[T] = Def.Setting[T]
	type ScopedKey[T] = Def.ScopedKey[T]
	type SettingsDefinition = Def.SettingsDefinition
	type File = java.io.File
	type URI = java.net.URI
	type URL = java.net.URL
	
	object CompileOrder {
		val JavaThenScala = xsbti.compile.CompileOrder.JavaThenScala
		val ScalaThenJava = xsbti.compile.CompileOrder.ScalaThenJava
		val Mixed = xsbti.compile.CompileOrder.Mixed
	}
	type CompileOrder = xsbti.compile.CompileOrder

	implicit def maybeToOption[S](m: xsbti.Maybe[S]): Option[S] =
		if(m.isDefined) Some(m.get) else None
	def uri(s: String): URI = new URI(s)
	def file(s: String): File = new File(s)
	def url(s: String): URL = new URL(s)
	
	final val ThisScope = Scope.ThisScope
	final val GlobalScope = Scope.GlobalScope

		import sbt.{Configurations => C}
	final val Compile = C.Compile
	final val Test = C.Test
	final val Runtime = C.Runtime
	final val IntegrationTest = C.IntegrationTest
	final val Default = C.Default
	final val Docs = C.Docs
	final val Sources = C.Sources
	final val Provided = C.Provided
// java.lang.System is more important, so don't alias this one
//	final val System = C.System
	final val Optional = C.Optional
	def config(s: String): Configuration = Configurations.config(s)

		import language.experimental.macros
	def settingKey[T](description: String): SettingKey[T] = macro std.KeyMacro.settingKeyImpl[T]
	def taskKey[T](description: String): TaskKey[T] = macro std.KeyMacro.taskKeyImpl[T]
	def inputKey[T](description: String): InputKey[T] = macro std.KeyMacro.inputKeyImpl[T]
}
