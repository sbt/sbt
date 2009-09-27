package xsbt

import java.io.File

/** A component manager provides access to the pieces of xsbt that are distributed as components.
* There are two types of components.  The first type is compiled subproject jars with their dependencies.
* The second type is a subproject distributed as a source jar so that it can be compiled against a specific
* version of Scala.
*
* The component manager provides services to install and retrieve components to the local repository.
* This is used for compiled source jars so that the compilation need not be repeated for other projects on the same
* machine.
*/
class ComponentManager(provider: xsbti.ComponentProvider, log: IvyLogger) extends NotNull
{
	/** Get all of the files for component 'id', throwing an exception if no files exist for the component. */
	def files(id: String): Iterable[File] =
	{
		val existing = provider.component(id)
		val fs = if(existing.isEmpty) { update(id); provider.component(id) } else existing
		if(!fs.isEmpty) fs else invalid("Could not find required component '" + id + "'")
	}
	/** Get the file for component 'id', throwing an exception if no files or multiple files exist for the component. */
	def file(id: String): File =
		files(id).toList match {
			case x :: Nil => x
			case xs => invalid("Expected single file for component '" + id + "', found: " + xs.mkString(", "))
		}
	private def invalid(msg: String) = throw new InvalidComponent(msg)
	private def invalid(e: NotInCache) = throw new InvalidComponent(e.getMessage, e)

	def define(id: String, files: Iterable[File]) = provider.defineComponent(id, files.toSeq.toArray)
	/** Retrieve the file for component 'id' from the local repository. */
	def update(id: String): Unit =
		try { IvyCache.withCachedJar(sbtModuleID(id), log)(jar => define(id, Seq(jar)) ) }
		catch { case e: NotInCache => invalid(e) }

	def sbtModuleID(id: String) = ModuleID("org.scala-tools.sbt", id, xsbti.Versions.Sbt)
	/** Install the files for component 'id' to the local repository.  This is usually used after writing files to the directory returned by 'location'. */
	def cache(id: String): Unit = IvyCache.cacheJar(sbtModuleID(id), file(id), log)
	def clearCache(id: String): Unit = IvyCache.clearCachedJar(sbtModuleID(id), log)
}
class InvalidComponent(msg: String, cause: Throwable) extends RuntimeException(msg, cause)
{
	def this(msg: String) = this(msg, null)
}