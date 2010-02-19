/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

import java.io.File
import xsbt.Paths._
import ProcessorException.error

/** Files needed by ManagerImpl.
* `retrieveBaseDirectory` is the directory that processors are retrieved under.
* `retrieveLockFile` is used to synchronize access to that directory.
* `definitionsFile` is the file to save repository and processor definitions to.  It is usually per-user instead of per-project.*/
class ManagerFiles(val retrieveBaseDirectory: File, val retrieveLockFile: File, val definitionsFile: File)

class ManagerImpl(files: ManagerFiles, scalaVersion: String, persist: Persist, log: Logger) extends Manager
{
	import files._
	
	def processorDefinition(label: String): Option[ProcessorDefinition] = processors.get(label)
	def processor(pdef: ProcessorDefinition): Option[Processor] =
	{
		def tryProcessor: Either[Throwable, Processor] =
			(new Loader).getProcessor( retrieveDirectory(pdef) )

		 // try to load the processor.  It will succeed here if the processor has already been retrieved
		tryProcessor.left.flatMap { _ =>
			 // if it hasn't been retrieved, retrieve the processor and its dependencies
			retrieveProcessor(pdef)
			// try to load the processor now that it has been retrieved
			tryProcessor.left.map { // if that fails, log a warning
				case p: ProcessorException => log.warn(p.getMessage)
				case t => log.trace(t); log.warn(t.toString)
			}
		}.right.toOption
	}
	def defineProcessor(p: ProcessorDefinition)
	{
		checkExisting(p)
		retrieveProcessor(p)
		add(p)
	}
	def defineRepository(r: RepositoryDefinition)
	{
		checkExisting(r)
		add(r)
	}
	def removeDefinition(label: String): Definition =
		definitions.removeKey(label) match
		{
			case Some(removed) =>
				saveDefinitions()
				removed
			case None => error("Label '" + label + "' not defined.")
		}
	
	private def retrieveProcessor(p: ProcessorDefinition): Unit =
	{
		val resolvers = repositories.values.toList.map(toResolver)
		val module = p.toModuleID(scalaVersion)
		( new Retrieve(retrieveDirectory(p), module, persist.lock, retrieveLockFile, resolvers, log) ).retrieve()
	}
	private def add(d: Definition)
	{
		definitions(d.label) = d
		saveDefinitions()
	}

	private lazy val definitions = loadDefinitions(definitionsFile)
	def repositories = Map() ++ partialMap(definitions) { case (label, d: RepositoryDefinition) => (label, d) }
	def processors = Map() ++ partialMap(definitions) { case (label, p: ProcessorDefinition) => (label, p) }
	
	private def checkExisting(p: Definition): Unit  =  definitions.get(p.label) map { d => error ("Label '" + p.label + "' already in use: " + d) }
	private def partialMap[T,S](i: Iterable[T])(f: PartialFunction[T,S]) = i.filter(f.isDefinedAt).map(f)
	private def toResolver(repo: RepositoryDefinition): Resolver = new MavenRepository(repo.label, repo.url)

	def retrieveDirectory(p: ProcessorDefinition) = retrieveBaseDirectory / p.group / p.module / p.rev
	
	private def saveDefinitions(): Unit = saveDefinitions(definitionsFile)
	private def saveDefinitions(file: File): Unit = persist.save(file)(definitions.values.toList)
	private def loadDefinitions(file: File): scala.collection.mutable.Map[String, Definition] =
		scala.collection.mutable.HashMap(  (if(file.exists) rawLoad(file) else Nil) : _*)
	private def rawLoad(file: File): Seq[(String, Definition)] = persist.load(definitionsFile).map { d => (d.label, d) }
}