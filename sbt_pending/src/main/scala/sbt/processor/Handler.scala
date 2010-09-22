/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

class Handler(baseProject: Project) extends NotNull
{
	def unapply(line: String): Option[ParsedProcessor] =
		line.split("""\s+""", 2) match
		{
			case Array(label @ GetProcessor(processor), args @ _*) => Some( new ParsedProcessor(label, processor, args.mkString) )
			case _ => None
		}
	private object GetProcessor
	{
		def unapply(name: String): Option[Processor] =
			manager.processorDefinition(name).flatMap(manager.processor)
	}
	
	def lock = baseProject.info.launcher.globalLock
	
	lazy val scalaVersion = baseProject.defScalaVersion.value
	lazy val base = baseProject.info.bootPath / ("scala-" + scalaVersion) / "sbt-processors"
	lazy val persistBase = Path.userHome / ".ivy2" / "sbt"
	
	def retrieveLockFile = base / lockName
	def persistLockFile = persistBase / lockName
	def lockName = "processors.lock"
	def definitionsFile = persistBase / "processors.properties"
	def files = new ManagerFiles(base.asFile, retrieveLockFile.asFile, definitionsFile.asFile)
	
	lazy val defParser = new DefinitionParser
	lazy val manager = new ManagerImpl(files, scalaVersion, new Persist(lock, persistLockFile.asFile, defParser), baseProject.offline.value, baseProject.log)
}
class ParsedProcessor(val label: String, val processor: Processor, val arguments: String) extends NotNull