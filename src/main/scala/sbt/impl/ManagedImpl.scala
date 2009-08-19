/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import org.apache.ivy.{plugins, util}
import plugins.parser.m2.PomModuleDescriptorWriter
import util.{Message, MessageLogger}

private object DefaultConfigurationMapping extends PomModuleDescriptorWriter.ConfigurationScopeMapping(new java.util.HashMap)
{
	override def getScope(confs: Array[String]) =
	{
		Configurations.defaultMavenConfigurations.find(conf => confs.contains(conf.name)) match
		{
			case Some(conf) => conf.name
			case None =>
				if(confs.isEmpty || confs(0) == Configurations.Default.name)
					null
				else
					confs(0)
		}
	}
	override def isOptional(confs: Array[String]) = confs.isEmpty || (confs.length == 1 && confs(0) == Configurations.Optional.name)
}

/** Interface between Ivy logging and sbt logging. */
private final class IvyLogger(log: Logger) extends MessageLogger
{
	private var progressEnabled = false
	
	def log(msg: String, level: Int)
	{
		import Message.{MSG_DEBUG, MSG_VERBOSE, MSG_INFO, MSG_WARN, MSG_ERR}
		level match
		{
			case MSG_DEBUG | MSG_VERBOSE => debug(msg)
			case MSG_INFO => info(msg)
			case MSG_WARN => warn(msg)
			case MSG_ERR => error(msg)
		}
	}
	def rawlog(msg: String, level: Int)
	{
		log(msg, level)
	}
	import Level.{Debug, Info, Warn, Error}
	def debug(msg: String) = logImpl(msg, Debug)
	def verbose(msg: String) = debug(msg)
	def deprecated(msg: String) = warn(msg)
	def info(msg: String) = logImpl(msg, Info)
	def rawinfo(msg: String) = info(msg)
	def warn(msg: String) = logImpl(msg, Warn)
	def error(msg: String) = logImpl(msg, Error)
	
	private def logImpl(msg: String, level: Level.Value) = log.log(level, msg)
	
	private def emptyList = java.util.Collections.emptyList[T forSome { type T}]
	def getProblems = emptyList
	def getWarns = emptyList
	def getErrors = emptyList

	def clearProblems = ()
	def sumupProblems = ()
	def progress = ()
	def endProgress = ()

	def endProgress(msg: String) = info(msg)
	def isShowProgress = false
	def setShowProgress(progress: Boolean) {}
}
