package sbt.script

	import java.io.PrintWriter
	import javax.script.{ScriptContext, ScriptEngine, ScriptEngineManager, SimpleScriptContext}
	import xsbt.OpenResource.fileReader
	import xsbt.FileUtilities.defaultCharset
	
object Run
{
	def apply(file: Path, project: Project): AnyRef =
		apply(file, defaultContext(project)_, project)
	def apply(file: Path, ctx: ScriptContext => Unit, project: Project): AnyRef =
		apply(file, ctx, defaultLoaders(project))
	def apply(script: String, language: String, project: Project): AnyRef =
		apply(script, language, defaultContext(project)_, project)
	def apply(script: String, language: String, ctx: ScriptContext => Unit, project: Project): AnyRef =
		apply(script, language, ctx, defaultLoaders(project))
		
	def apply(file: Path, ctx: ScriptContext => Unit, loaders: Stream[ClassLoader]): AnyRef =
	{
		val engine = getEngineByExt(loaders, file.ext)
		ctx(engine.getContext)
		apply(file, engine)
	}
	def apply(script: String, language: String, ctx: ScriptContext => Unit, loaders: Stream[ClassLoader]): AnyRef =
	{
		val engine = getEngineByName(loaders, language)
		ctx(engine.getContext)
		engine.eval(script)
	}
	
	def defaultContext(project: Project)(ctx: ScriptContext)
	{
		ctx.setAttribute("project", project, ScriptContext.ENGINE_SCOPE)
		// JavaScript implementation in 1.6 depends on this being a PrintWriter
		ctx.setErrorWriter(new PrintWriter(new LoggerWriter(project.log, Level.Error)))
		ctx.setWriter(new PrintWriter(new LoggerWriter(project.log, Level.Info)))
	}
	
	def apply(file: Path, engine: ScriptEngine): AnyRef = 
		xsbt.OpenResource.fileReader(defaultCharset)(file asFile)( engine.eval )

	def bind(engine: ScriptEngine, bindings: Map[String, AnyRef]): Unit =
		for( (k, v) <- bindings ) engine.put(k, v)

	def defaultLoaders(project: Project) = Stream(project.getClass.getClassLoader, launcherLoader(project))
	def launcherLoader(project: Project) = project.info.launcher.topLoader.getParent
	def defaultBindings(project: Project) = Map("project" -> project)
	
	def getEngine(loaders: Stream[ClassLoader], get: ScriptEngineManager => ScriptEngine, label: String): ScriptEngine =
		firstEngine( engines(managers(loaders), get), label)
		
	def getEngineByName(loaders: Stream[ClassLoader], lang: String): ScriptEngine =
		getEngine(loaders, _.getEngineByName(lang), "name '" + lang + "'")
	def getEngineByExt(loaders: Stream[ClassLoader], ext: String): ScriptEngine =
		getEngine(loaders, _.getEngineByExtension(ext), "extension '" + ext + "'")
		
	def managers(loaders: Stream[ClassLoader]): Stream[ScriptEngineManager] =
		loaders.map(new ScriptEngineManager(_))
	def engines(managers: Stream[ScriptEngineManager], get: ScriptEngineManager => ScriptEngine) =
		managers.flatMap(getEngine(get))
	def firstEngine(engines: Stream[ScriptEngine], label: String) =
		engines.headOption.getOrElse(error("Could not find script engine for " + label))
	def getEngine(get: ScriptEngineManager => ScriptEngine)(manager: ScriptEngineManager) = 
	{
		val engine = get(manager)
		if(engine == null) Nil else engine :: Nil
	}
}