/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt.processor

import java.io.File

class Retrieve(retrieveDirectory: File, module: ModuleID, lock: xsbti.GlobalLock, lockFile: File, repositories: Seq[Resolver], log: IvyLogger) extends NotNull
{
	def retrieve(localOnly: Boolean)
	{
		val paths = new IvyPaths(retrieveDirectory, None)
		val ivyScala = new IvyScala("", Nil, false, true)
		val fullRepositories = Resolver.withDefaultResolvers(repositories) // TODO: move this somewhere under user control
		val configuration = new InlineIvyConfiguration(paths, fullRepositories, Nil, Nil, localOnly, Some(lock), log)
		val moduleConfiguration = new InlineConfiguration(thisID, module :: Nil, scala.xml.NodeSeq.Empty, Nil, None, Some(ivyScala), false)
		val update = new UpdateConfiguration(retrieveDirectory, retrievePattern, true, logging)
		val ivySbt = new IvySbt(configuration)
		val ivyModule = new ivySbt.Module(moduleConfiguration)
		
		lock(lockFile, Callable { IvyActions.update(ivyModule, update) } )
	}
	def thisID = ModuleID("org.scala-tools.sbt", "retrieve-processor", "1.0")
	def retrievePattern = "[artifact](-[revision])(-[classifier]).[ext]"

	def logging = UpdateLogging.DownloadOnly
}

object Callable
{
	def apply[T](f: => T): java.util.concurrent.Callable[T] = new java.util.concurrent.Callable[T] { def call = f }
}