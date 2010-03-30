/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import Artifact.{defaultExtension, defaultType}

import java.io.File
import java.util.concurrent.Callable

import org.apache.ivy.{core, plugins, util, Ivy}
import core.IvyPatternHelper
import core.cache.DefaultRepositoryCacheManager
import core.module.descriptor.{DefaultArtifact, DefaultDependencyArtifactDescriptor, MDArtifact}
import core.module.descriptor.{DefaultDependencyDescriptor, DefaultModuleDescriptor,  ModuleDescriptor}
import core.module.id.{ArtifactId,ModuleId, ModuleRevisionId}
import core.settings.IvySettings
import plugins.matcher.PatternMatcher
import plugins.parser.m2.PomModuleDescriptorParser
import plugins.resolver.ChainResolver
import util.Message

final class IvySbt(configuration: IvyConfiguration)
{
	import configuration.{log, baseDirectory}
	/** ========== Configuration/Setup ============
	* This part configures the Ivy instance by first creating the logger interface to ivy, then IvySettings, and then the Ivy instance.
	* These are lazy so that they are loaded within the right context.  This is important so that no Ivy XML configuration needs to be loaded,
	* saving some time.  This is necessary because Ivy has global state (IvyContext, Message, DocumentBuilder, ...).
	*/
	private lazy val logger = new IvyLoggerInterface(log)
	private def withDefaultLogger[T](f: => T): T =
	{
		def action() =
			IvySbt.synchronized 
			{
				val originalLogger = Message.getDefaultLogger
				Message.setDefaultLogger(logger)
				try { f }
				finally { Message.setDefaultLogger(originalLogger) }
			}
		// Ivy is not thread-safe nor can the cache be used concurrently.
		// If provided a GlobalLock, we can use that to ensure safe access to the cache.
		// Otherwise, we can at least synchronize within the JVM.
		//   For thread-safety In particular, Ivy uses a static DocumentBuilder, which is not thread-safe.
		configuration.lock match
		{
			case Some(lock) => lock(ivyLockFile, new Callable[T] { def call = action() })
			case None => action()
		}
	}
	private lazy val settings =
	{
		val is = new IvySettings
		is.setBaseDir(baseDirectory)
		configuration match
		{
			case e: ExternalIvyConfiguration => is.load(e.file)
			case i: InlineIvyConfiguration => 
				IvySbt.configureCache(is, i.paths.cacheDirectory)
				IvySbt.setResolvers(is, i.resolvers, i.otherResolvers, log)
				IvySbt.setModuleConfigurations(is, i.moduleConfigurations)
		}
		is
	}
	private lazy val ivy =
	{
		val i = new Ivy() { private val loggerEngine = new SbtMessageLoggerEngine; override def getLoggerEngine = loggerEngine }
		i.setSettings(settings)
		i.bind()
		i.getLoggerEngine.pushLogger(logger)
		i
	}
	// Must be the same file as is used in Update in the launcher
	private lazy val ivyLockFile = new File(settings.getDefaultIvyUserDir, ".sbt.ivy.lock")
	/** ========== End Configuration/Setup ============*/

	/** Uses the configured Ivy instance within a safe context.*/
	def withIvy[T](f: Ivy => T): T =
		withDefaultLogger
		{
			ivy.pushContext()
			try { f(ivy) }
			finally { ivy.popContext() }
		}

	final class Module(val moduleSettings: ModuleSettings) extends NotNull
	{
		def logger = configuration.log
		def withModule[T](f: (Ivy,DefaultModuleDescriptor,String) => T): T =
			withIvy[T] { ivy => f(ivy, moduleDescriptor, defaultConfig) }

		private lazy val (moduleDescriptor: DefaultModuleDescriptor, defaultConfig: String) =
		{
			val (baseModule, baseConfiguration) =
				moduleSettings match
				{
					case ic: InlineConfiguration => configureInline(ic)
					case ec: EmptyConfiguration => configureEmpty(ec.module)
					case pc: PomConfiguration => readPom(pc.file, pc.validate)
					case ifc: IvyFileConfiguration => readIvyFile(ifc.file, ifc.validate)
				}
			moduleSettings.ivyScala.foreach(IvyScala.checkModule(baseModule, baseConfiguration))
			baseModule.getExtraAttributesNamespaces.asInstanceOf[java.util.Map[String,String]].put("e", "http://ant.apache.org/ivy/extra")
			(baseModule, baseConfiguration)
		}
		private def configureInline(ic: InlineConfiguration) =
		{
			import ic._
			val moduleID = newConfiguredModuleID(module, configurations)
			val defaultConf = defaultConfiguration getOrElse Configurations.config(ModuleDescriptor.DEFAULT_CONFIGURATION)
			log.debug("Using inline dependencies specified in Scala" + (if(ivyXML.isEmpty) "." else " and XML."))

			val parser = IvySbt.parseIvyXML(ivy.getSettings, IvySbt.wrapped(module, ivyXML), moduleID, defaultConf.name, validate)

			IvySbt.addDependencies(moduleID, dependencies, parser)
			IvySbt.addMainArtifact(moduleID)
			(moduleID, parser.getDefaultConf)
		}
		private def newConfiguredModuleID(module: ModuleID, configurations: Iterable[Configuration]) =
		{
			val mod = new DefaultModuleDescriptor(IvySbt.toID(module), "release", null, false)
			mod.setLastModified(System.currentTimeMillis)
			configurations.foreach(config => mod.addConfiguration(IvySbt.toIvyConfiguration(config)))
			IvySbt.addArtifacts(mod, module.explicitArtifacts)
			mod
		}

		/** Parses the given Maven pom 'pomFile'.*/
		private def readPom(pomFile: File, validate: Boolean) =
		{
			val md = PomModuleDescriptorParser.getInstance.parseDescriptor(settings, toURL(pomFile), validate)
			(IvySbt.toDefaultModuleDescriptor(md), "compile")
		}
		/** Parses the given Ivy file 'ivyFile'.*/
		private def readIvyFile(ivyFile: File, validate: Boolean) =
		{
			val url = toURL(ivyFile)
			val parser = new CustomXmlParser.CustomParser(settings, None)
			parser.setValidate(validate)
			parser.setSource(url)
			parser.parse()
			val md = parser.getModuleDescriptor()
			(IvySbt.toDefaultModuleDescriptor(md), parser.getDefaultConf)
		}
		private def toURL(file: File) = file.toURI.toURL
		private def configureEmpty(module: ModuleID) =
		{
			val defaultConf = ModuleDescriptor.DEFAULT_CONFIGURATION
			val moduleID = new DefaultModuleDescriptor(IvySbt.toID(module), "release", null, false)
			moduleID.setLastModified(System.currentTimeMillis)
			moduleID.addConfiguration(IvySbt.toIvyConfiguration(Configurations.Default))
			IvySbt.addArtifacts(moduleID, module.explicitArtifacts)
			IvySbt.addMainArtifact(moduleID)
			(moduleID, defaultConf)
		}
	}
}

private object IvySbt
{
	val DefaultIvyConfigFilename = "ivysettings.xml"
	val DefaultIvyFilename = "ivy.xml"
	val DefaultMavenFilename = "pom.xml"

	def defaultIvyFile(project: File) = new File(project, DefaultIvyFilename)
	def defaultIvyConfiguration(project: File) = new File(project, DefaultIvyConfigFilename)
	def defaultPOM(project: File) = new File(project, DefaultMavenFilename)

	/** Sets the resolvers for 'settings' to 'resolvers'.  This is done by creating a new chain and making it the default.
	* 'other' is for resolvers that should be in a different chain.  These are typically used for publishing or other actions. */
	private def setResolvers(settings: IvySettings, resolvers: Seq[Resolver], other: Seq[Resolver], log: IvyLogger)
	{
		val otherChain = resolverChain("sbt-other", other)
		settings.addResolver(otherChain)
		val newDefault = resolverChain("sbt-chain", resolvers)
		settings.addResolver(newDefault)
		settings.setDefaultResolver(newDefault.getName)
		log.debug("Using repositories:\n" + resolvers.mkString("\n\t"))
		log.debug("Using other repositories:\n" + other.mkString("\n\t"))
	}
	private def resolverChain(name: String, resolvers: Seq[Resolver]): ChainResolver =
	{
		val newDefault = new ChainResolver
		newDefault.setName(name)
		newDefault.setReturnFirst(true)
		newDefault.setCheckmodified(true)
		resolvers.foreach(r => newDefault.add(ConvertResolver(r)))
		newDefault
	}
	private def setModuleConfigurations(settings: IvySettings, moduleConfigurations: Seq[ModuleConfiguration])
	{
		val existing = settings.getResolverNames
		for(moduleConf <- moduleConfigurations)
		{
			import moduleConf._
			import IvyPatternHelper._
			import PatternMatcher._
			if(!existing.contains(resolver.name))
				settings.addResolver(ConvertResolver(resolver))
			val attributes = javaMap(Map(MODULE_KEY -> name, ORGANISATION_KEY -> organization, REVISION_KEY -> revision))
			settings.addModuleConfiguration(attributes, settings.getMatcher(EXACT_OR_REGEXP), resolver.name, null, null, null)
		}
	}
	private def configureCache(settings: IvySettings, dir: Option[File])
	{
		val cacheDir = dir.getOrElse(settings.getDefaultRepositoryCacheBasedir())
		val manager = new DefaultRepositoryCacheManager("default-cache", settings, cacheDir)
		manager.setUseOrigin(true)
		manager.setChangingMatcher(PatternMatcher.REGEXP);
		manager.setChangingPattern(".*-SNAPSHOT");
		settings.addRepositoryCacheManager(manager)
		settings.setDefaultRepositoryCacheManager(manager)
		dir.foreach(dir => settings.setDefaultResolutionCacheBasedir(dir.getAbsolutePath))
	}
	def toIvyConfiguration(configuration: Configuration) =
	{
		import org.apache.ivy.core.module.descriptor.{Configuration => IvyConfig}
		import IvyConfig.Visibility._
		import configuration._
		new IvyConfig(name, if(isPublic) PUBLIC else PRIVATE, description, extendsConfigs.map(_.name).toArray, transitive, null)
	}
	private def addDefaultArtifact(defaultConf: String, moduleID: DefaultModuleDescriptor) =
		moduleID.addArtifact(defaultConf, new MDArtifact(moduleID, moduleID.getModuleRevisionId.getName, defaultType, defaultExtension))
	/** Adds the ivy.xml main artifact. */
	private def addMainArtifact(moduleID: DefaultModuleDescriptor)
	{
		val artifact = DefaultArtifact.newIvyArtifact(moduleID.getResolvedModuleRevisionId, moduleID.getPublicationDate)
		moduleID.setModuleArtifact(artifact)
		moduleID.check()
	}
	/** Converts the given sbt module id into an Ivy ModuleRevisionId.*/
	def toID(m: ModuleID) =
	{
		import m._
		ModuleRevisionId.newInstance(organization, name, revision, javaMap(extraAttributes))
	}
	private def toIvyArtifact(moduleID: ModuleDescriptor, a: Artifact, configurations: Iterable[String]): MDArtifact =
	{
		val artifact = new MDArtifact(moduleID, a.name, a.`type`, a.extension, null, extra(a))
		configurations.foreach(artifact.addConfiguration)
		artifact
	}
	private def extra(artifact: Artifact) =
	{
		val ea = artifact.classifier match { case Some(c) => artifact.extra("e:classifier" -> c); case None => artifact }
		javaMap(ea.extraAttributes)
	}
	private def javaMap(map: Map[String,String]) =
		if(map.isEmpty) null
		else
		{
			val wrap = scala.collection.jcl.Map(new java.util.HashMap[String,String])
			wrap ++= map
			wrap.underlying
		}

	private object javaMap
	{
		import java.util.{HashMap, Map}
		def apply[K,V](pairs: (K,V)*): Map[K,V] =
		{
			val map = new HashMap[K,V]
			pairs.foreach { case (key, value) => map.put(key, value) }
			map
		}
	}
	/** Creates a full ivy file for 'module' using the 'dependencies' XML as the part after the &lt;info&gt;...&lt;/info&gt; section. */
	private def wrapped(module: ModuleID, dependencies: scala.xml.NodeSeq) =
	{
		import module._
		<ivy-module version="2.0">
			{ if(hasInfo(dependencies))
				scala.xml.NodeSeq.Empty
			else
				<info organisation={organization} module={name} revision={revision}/>
			}
			{dependencies}
		</ivy-module>
	}
	private def hasInfo(x: scala.xml.NodeSeq) = !(<g>{x}</g> \ "info").isEmpty
	/** Parses the given in-memory Ivy file 'xml', using the existing 'moduleID' and specifying the given 'defaultConfiguration'. */
	private def parseIvyXML(settings: IvySettings, xml: scala.xml.NodeSeq, moduleID: DefaultModuleDescriptor, defaultConfiguration: String, validate: Boolean): CustomXmlParser.CustomParser =
		parseIvyXML(settings,  xml.toString, moduleID, defaultConfiguration, validate)
	/** Parses the given in-memory Ivy file 'xml', using the existing 'moduleID' and specifying the given 'defaultConfiguration'. */
	private def parseIvyXML(settings: IvySettings, xml: String, moduleID: DefaultModuleDescriptor, defaultConfiguration: String, validate: Boolean): CustomXmlParser.CustomParser =
	{
		val parser = new CustomXmlParser.CustomParser(settings, Some(defaultConfiguration))
		parser.setMd(moduleID)
		parser.setValidate(validate)
		parser.setInput(xml.getBytes)
		parser.parse()
		parser
	}

	/** This method is used to add inline dependencies to the provided module. */
	def addDependencies(moduleID: DefaultModuleDescriptor, dependencies: Iterable[ModuleID], parser: CustomXmlParser.CustomParser)
	{
		for(dependency <- dependencies)
		{
			val dependencyDescriptor = new DefaultDependencyDescriptor(moduleID, toID(dependency), false, dependency.isChanging, dependency.isTransitive)
			dependency.configurations match
			{
				case None => // The configuration for this dependency was not explicitly specified, so use the default
					parser.parseDepsConfs(parser.getDefaultConf, dependencyDescriptor)
				case Some(confs) => // The configuration mapping (looks like: test->default) was specified for this dependency
					parser.parseDepsConfs(confs, dependencyDescriptor)
			}
			for(artifact <- dependency.explicitArtifacts)
			{
				import artifact.{name, classifier, `type`, extension, url}
				val extraMap = extra(artifact)
				val ivyArtifact = new DefaultDependencyArtifactDescriptor(dependencyDescriptor, name, `type`, extension, url.getOrElse(null), extraMap)
				for(conf <- dependencyDescriptor.getModuleConfigurations)
					dependencyDescriptor.addDependencyArtifact(conf, ivyArtifact)
			}
			moduleID.addDependency(dependencyDescriptor)
		}
	}
	/** This method is used to add inline artifacts to the provided module. */
	def addArtifacts(moduleID: DefaultModuleDescriptor, artifacts: Iterable[Artifact])
	{
		lazy val allConfigurations = moduleID.getPublicConfigurationsNames
		for(artifact <- artifacts)
		{
			val configurationStrings =
			{
				val artifactConfigurations = artifact.configurations
				if(artifactConfigurations.isEmpty)
					allConfigurations
				else
					artifactConfigurations.map(_.name)
			}
			val ivyArtifact = toIvyArtifact(moduleID, artifact, configurationStrings)
			configurationStrings.foreach(configuration => moduleID.addArtifact(configuration, ivyArtifact))
		}
	}
	/** This code converts the given ModuleDescriptor to a DefaultModuleDescriptor by casting or generating an error.
	* Ivy 2.0.0 always produces a DefaultModuleDescriptor. */
	private def toDefaultModuleDescriptor(md: ModuleDescriptor) =
		md match
		{
			case dmd: DefaultModuleDescriptor => dmd
			case _ => error("Unknown ModuleDescriptor type.")
		}
	def getConfigurations(module: ModuleDescriptor, configurations: Option[Iterable[Configuration]]) =
		configurations match
		{
			case Some(confs) => confs.map(_.name).toList.toArray
			case None => module.getPublicConfigurationsNames
		}
}
