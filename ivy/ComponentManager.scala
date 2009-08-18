package xsbt

import java.io.File
import xsbti.Versions

/** A component manager provides access to the pieces of xsbt that are distributed as components.
* There are two types of components.  The first type is compiled subproject jars with their dependencies.
* The second type is a subproject distributed as a source jar so that it can be compiled against a specific
* version of Scala.
*
* The component manager provides services to install and retrieve components to the local repository.
* This is used for source jars so that the compilation need not be repeated for other projects on the same
* machine.
*/
class ComponentManager(baseDirectory: File, log: IvyLogger) extends NotNull
{
	def location(id: String): File = new File(baseDirectory, id)
	def directory(id: String): File =
	{
		val dir = location(id)
		if(!dir.exists)
			update(id)
		dir
	}
	private def contents(dir: File): Seq[File] =
	{
		val fs = dir.listFiles
		if(fs == null) Nil else fs
	}
	def files(id: String): Iterable[File] =
	{
		val fs = contents(directory(id))
		if(!fs.isEmpty) fs else error("Could not find required component '" + id + "'")
	}
	def file(id: String): File =
		files(id).toList match {
			case x :: Nil => x
			case xs => error("Expected single file for component '" + id + "', found: " + xs.mkString(", "))
		}
		
	def update(id: String): Unit =
		IvyActions.basicRetrieveLocal(sbtModuleID("manager"), Seq(sbtModuleID(id)), location(id), log)
	def sbtModuleID(id: String) = ModuleID("org.scala-tools.sbt", id, Versions.Sbt)
	def cache(id: String): Unit = IvyActions.basicPublishLocal(sbtModuleID(id), Nil, files(id), log)
}