package sbt.script

	import scala.collection.Set
	import Scripts.{checkName, runScript}

trait Scripts extends Project
{
	abstract override def tasks = if(scriptedTasks.isEmpty) super.tasks else Map() ++ super.tasks ++ scriptedTasks
	
	def scriptSources: PathFinder = info.builderPath / "scripts" * "*.*"
	
	lazy val scriptedTasks: Map[String, Task] = makeScripted(scriptSources.get)
	
	def makeScripted(files: Set[Path]): Map[String, Task] =  Map() ++ (files map makeScripted)
	def makeScripted(file: Path): (String, Task) =
	{
		val t = scriptedTask(file) named(checkName(file.base))
		(t.name, t)
	}
	def scriptedTask(file: Path): Task = task { runScript(file, this) }
	def scriptedTask(script: String, language: String): Task = task { runScript(script, language, this) }
}
object Scripts
{
	def runScript(file: Path, project: Project): Option[String] = getError( Run(file, project) )
	def runScript(script: String, language: String, project: Project): Option[String] = getError( Run(script, language, project) )
	def getError(result: AnyRef): Option[String] =
		result match
		{
			case Some(v: String) => Some(v)
			case _ => None
		}
	def checkName(base: String) = base.find(c => !legalID(c)) match { case Some(c) => error("Illegal character in scripted task name '" + base + "': '" + c + "'"); case None => base }
	def legalID(c: Char) = java.lang.Character.isJavaIdentifierPart(c) || c == '-'
}
