/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import Resolver.PluginPattern

import java.io.File
import java.util.concurrent.Callable
import java.util.{Collection, Collections => CS}
import CS.singleton

import org.apache.ivy.{core, plugins, util, Ivy}
import core.{IvyPatternHelper, LogOptions}
import core.cache.{CacheMetadataOptions, DefaultRepositoryCacheManager}
import core.module.descriptor.{Artifact => IArtifact, DefaultArtifact, DefaultDependencyArtifactDescriptor, MDArtifact}
import core.module.descriptor.{DefaultDependencyDescriptor, DefaultModuleDescriptor, DependencyDescriptor, ModuleDescriptor, License}
import core.module.id.{ArtifactId,ModuleId, ModuleRevisionId}
import core.resolve.{IvyNode, ResolveData}
import core.settings.IvySettings
import plugins.conflict.{ConflictManager, LatestCompatibleConflictManager, LatestConflictManager}
import plugins.latest.LatestRevisionStrategy
import plugins.matcher.PatternMatcher
import plugins.parser.m2.PomModuleDescriptorParser
import plugins.resolver.{ChainResolver, DependencyResolver}
import util.{Message, MessageLogger}
import util.extendable.ExtendableItem

import scala.xml.{NodeSeq, Text}

final class IvySbt(val configuration: IvyConfiguration)
{
		import configuration.baseDirectory

	/** ========== Configuration/Setup ============
	* This part configures the Ivy instance by first creating the logger interface to ivy, then IvySettings, and then the Ivy instance.
	* These are lazy so that they are loaded within the right context.  This is important so that no Ivy XML configuration needs to be loaded,
	* saving some time.  This is necessary because Ivy has global state (IvyContext, Message, DocumentBuilder, ...).
	*/
	private def withDefaultLogger[T](logger: MessageLogger)(f: => T): T =
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
		//   For thread-safety in particular, Ivy uses a static DocumentBuilder, which is not thread-safe.
		configuration.lock match
		{
			case Some(lock) => lock(ivyLockFile, new Callable[T] { def call = action() })
			case None => action()
		}
	}
	private lazy val settings: IvySettings =
	{
		val is = new IvySettings
		is.setBaseDir(baseDirectory)
		CustomPomParser.registerDefault
		configuration match
		{
			case e: ExternalIvyConfiguration => is.load(e.file)
			case i: InlineIvyConfiguration =>
				is.setVariable("ivy.checksums", i.checksums mkString ",")
				i.paths.ivyHome foreach is.setDefaultIvyUserDir
				IvySbt.configureCache(is, i.localOnly)
				IvySbt.setResolvers(is, i.resolvers, i.otherResolvers, i.localOnly, configuration.log)
				IvySbt.setModuleConfigurations(is, i.moduleConfigurations, configuration.log)
		}
		is
	}
	private lazy val ivy: Ivy =
	{
		val i = new Ivy() { private val loggerEngine = new SbtMessageLoggerEngine; override def getLoggerEngine = loggerEngine }
		i.setSettings(settings)
		i.bind()
		i.getLoggerEngine.pushLogger(new IvyLoggerInterface(configuration.log))
		i
	}
	// Must be the same file as is used in Update in the launcher
	private lazy val ivyLockFile = new File(settings.getDefaultIvyUserDir, ".sbt.ivy.lock")
	/** ========== End Configuration/Setup ============*/

	/** Uses the configured Ivy instance within a safe context.*/
	def withIvy[T](log: Logger)(f: Ivy => T): T =
		withIvy(new IvyLoggerInterface(log))(f)

	def withIvy[T](log: MessageLogger)(f: Ivy => T): T =
		withDefaultLogger(log)
		{
			ivy.pushContext()
			ivy.getLoggerEngine.pushLogger(log)
			try { f(ivy) }
			finally {
				ivy.getLoggerEngine.popLogger()
				ivy.popContext()
			}
		}

	final class Module(rawModuleSettings: ModuleSettings)
	{
		val moduleSettings: ModuleSettings = IvySbt.substituteCross(rawModuleSettings)
		def owner = IvySbt.this
		def withModule[T](log: Logger)(f: (Ivy,DefaultModuleDescriptor,String) => T): T =
			withIvy[T](log) { ivy => f(ivy, moduleDescriptor0, defaultConfig0) }

		def moduleDescriptor(log: Logger): DefaultModuleDescriptor = withModule(log)((_,md,_) => md)
		def dependencyMapping(log: Logger): (ModuleRevisionId, ModuleDescriptor) =
		{
			val md = moduleDescriptor(log)
			(md.getModuleRevisionId, md)
		}
		def defaultConfig(log: Logger): String = withModule(log)( (_,_,dc) => dc)
		// these should only be referenced by withModule because lazy vals synchronize on this object
		// withIvy explicitly locks the IvySbt object, so they have to be done in the right order to avoid deadlock
		private[this] lazy val (moduleDescriptor0: DefaultModuleDescriptor, defaultConfig0: String) =
		{
			val (baseModule, baseConfiguration) =
				moduleSettings match
				{
					case ic: InlineConfiguration => configureInline(ic, configuration.log)
					case ec: EmptyConfiguration => configureEmpty(ec)
					case pc: PomConfiguration => configurePom(pc)
					case ifc: IvyFileConfiguration => configureIvyFile(ifc)
				}
			moduleSettings.ivyScala.foreach(IvyScala.checkModule(baseModule, baseConfiguration))
			baseModule.getExtraAttributesNamespaces.asInstanceOf[java.util.Map[String,String]].put("e", "http://ant.apache.org/ivy/extra")
			(baseModule, baseConfiguration)
		}
		private def configureInline(ic: InlineConfiguration, log: Logger) =
		{
			import ic._
			val moduleID = newConfiguredModuleID(module, moduleInfo, configurations)
			val defaultConf = defaultConfiguration getOrElse Configurations.config(ModuleDescriptor.DEFAULT_CONFIGURATION)
			log.debug("Using inline dependencies specified in Scala" + (if(ivyXML.isEmpty) "." else " and XML."))

			val parser = IvySbt.parseIvyXML(ivy.getSettings, IvySbt.wrapped(module, ivyXML), moduleID, defaultConf.name, validate)
			IvySbt.addDependencies(moduleID, dependencies, parser)
			IvySbt.addMainArtifact(moduleID)
			(moduleID, parser.getDefaultConf)
		}
		private def newConfiguredModuleID(module: ModuleID, moduleInfo: ModuleInfo, configurations: Iterable[Configuration]) =
		{
			val mod = new DefaultModuleDescriptor(IvySbt.toID(module), "release", null, false)
			mod.setLastModified(System.currentTimeMillis)
			mod.setDescription(moduleInfo.description)
			moduleInfo.homepage foreach { h => mod.setHomePage(h.toString) }
			moduleInfo.licenses foreach { l => mod.addLicense(new License(l._1, l._2.toString)) }
			IvySbt.addConfigurations(mod, configurations)
			IvySbt.addArtifacts(mod, module.explicitArtifacts)
			mod
		}

		/** Parses the Maven pom 'pomFile' from the given `PomConfiguration`.*/
		private def configurePom(pc: PomConfiguration) =
		{
			val md = PomModuleDescriptorParser.getInstance.parseDescriptor(settings, toURL(pc.file), pc.validate)
			val dmd = IvySbt.toDefaultModuleDescriptor(md)
			IvySbt.addConfigurations(dmd, Configurations.defaultInternal)
			(dmd, "compile")
		}
		/** Parses the Ivy file 'ivyFile' from the given `IvyFileConfiguration`.*/
		private def configureIvyFile(ifc: IvyFileConfiguration) =
		{
			val parser = new CustomXmlParser.CustomParser(settings, None)
			parser.setValidate(ifc.validate)
			parser.setSource(toURL(ifc.file))
			parser.parse()
			val md = parser.getModuleDescriptor()
			(IvySbt.toDefaultModuleDescriptor(md), parser.getDefaultConf)
		}
		private def toURL(file: File) = file.toURI.toURL
		private def configureEmpty(ec: EmptyConfiguration) =
		{
			val defaultConf = ModuleDescriptor.DEFAULT_CONFIGURATION
			val mod = newConfiguredModuleID(ec.module, ec.moduleInfo, Seq(Configurations.Default))
			IvySbt.addMainArtifact(mod)
			(mod, defaultConf)
		}
	}
}

private object IvySbt
{
	val DefaultIvyConfigFilename = "ivysettings.xml"
	val DefaultIvyFilename = "ivy.xml"
	val DefaultMavenFilename = "pom.xml"
	val DefaultChecksums = Seq("sha1", "md5")

	def defaultIvyFile(project: File) = new File(project, DefaultIvyFilename)
	def defaultIvyConfiguration(project: File) = new File(project, DefaultIvyConfigFilename)
	def defaultPOM(project: File) = new File(project, DefaultMavenFilename)

	/** Sets the resolvers for 'settings' to 'resolvers'.  This is done by creating a new chain and making it the default.
	* 'other' is for resolvers that should be in a different chain.  These are typically used for publishing or other actions. */
	private def setResolvers(settings: IvySettings, resolvers: Seq[Resolver], other: Seq[Resolver], localOnly: Boolean, log: Logger)
	{
		def makeChain(label: String, name: String, rs: Seq[Resolver]) = {
			log.debug(label + " repositories:")
			val chain = resolverChain(name, rs, localOnly, settings, log)
			settings.addResolver(chain)
			chain
		}
		val otherChain = makeChain("Other", "sbt-other", other)
		val mainChain = makeChain("Default", "sbt-chain", resolvers)
		settings.setDefaultResolver(mainChain.getName)
	}
	def resolverChain(name: String, resolvers: Seq[Resolver], localOnly: Boolean, settings: IvySettings, log: Logger): DependencyResolver =
	{
		val newDefault = new ChainResolver {
			// Technically, this should be applied to module configurations.
			// That would require custom subclasses of all resolver types in ConvertResolver (a delegation approach does not work).
			// It would be better to get proper support into Ivy.
			override def locate(artifact: IArtifact) =
				if(hasImplicitClassifier(artifact)) null else super.locate(artifact)
			override def getDependency(dd: DependencyDescriptor, data: ResolveData) =
			{
				if(data.getOptions.getLog != LogOptions.LOG_QUIET)
					log.info("Resolving " + dd.getDependencyRevisionId + " ...")
				super.getDependency(dd, data)
			}
		}
		newDefault.setName(name)
		newDefault.setReturnFirst(true)
		newDefault.setCheckmodified(false)
		for(sbtResolver <- resolvers) {
			log.debug("\t" + sbtResolver)
			newDefault.add(ConvertResolver(sbtResolver)(settings, log))
		}
		newDefault
	}
	/** A hack to detect if the given artifact is an automatically generated request for a classifier,
	* as opposed to a user-initiated declaration.  It relies on Ivy prefixing classifier with m:, while sbt uses e:.
	* Clearly, it would be better to have an explicit option in Ivy to control this.*/
	def hasImplicitClassifier(artifact: IArtifact): Boolean =
	{
		import collection.JavaConversions._
		artifact.getQualifiedExtraAttributes.keys.exists(_.asInstanceOf[String] startsWith "m:")
	}
	private def setModuleConfigurations(settings: IvySettings, moduleConfigurations: Seq[ModuleConfiguration], log: Logger)
	{
		val existing = settings.getResolverNames
		for(moduleConf <- moduleConfigurations)
		{
			import moduleConf._
			import IvyPatternHelper._
			import PatternMatcher._
			if(!existing.contains(resolver.name))
				settings.addResolver(ConvertResolver(resolver)(settings, log))
			val attributes = javaMap(Map(MODULE_KEY -> name, ORGANISATION_KEY -> organization, REVISION_KEY -> revision))
			settings.addModuleConfiguration(attributes, settings.getMatcher(EXACT_OR_REGEXP), resolver.name, null, null, null)
		}
	}
	private def configureCache(settings: IvySettings, localOnly: Boolean)
	{
		val cacheDir = settings.getDefaultRepositoryCacheBasedir()
		val manager = new DefaultRepositoryCacheManager("default-cache", settings, cacheDir) {
			override def findModuleInCache(dd: DependencyDescriptor, revId: ModuleRevisionId, options: CacheMetadataOptions, r: String) =
				super.findModuleInCache(dd,revId,options,null)
		}
		manager.setArtifactPattern(PluginPattern + manager.getArtifactPattern)
		manager.setDataFilePattern(PluginPattern + manager.getDataFilePattern)
		manager.setIvyPattern(PluginPattern + manager.getIvyPattern)
		manager.setUseOrigin(true)
		if(localOnly)
			manager.setDefaultTTL(java.lang.Long.MAX_VALUE);
		else
		{
			manager.setChangingMatcher(PatternMatcher.REGEXP);
			manager.setChangingPattern(".*-SNAPSHOT");
		}
		settings.addRepositoryCacheManager(manager)
		settings.setDefaultRepositoryCacheManager(manager)
	}
	def toIvyConfiguration(configuration: Configuration) =
	{
		import org.apache.ivy.core.module.descriptor.{Configuration => IvyConfig}
		import IvyConfig.Visibility._
		import configuration._
		new IvyConfig(name, if(isPublic) PUBLIC else PRIVATE, description, extendsConfigs.map(_.name).toArray, transitive, null)
	}
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

	private def substituteCross(m: ModuleSettings): ModuleSettings =
		m.ivyScala match { case None => m; case Some(is) => substituteCross(m, is.substituteCross) }
	private def substituteCross(m: ModuleSettings, sub: ModuleID => ModuleID): ModuleSettings =
		m match {
			case ec: EmptyConfiguration => ec.copy(module = sub(ec.module))
			case ic: InlineConfiguration => ic.copy(module = sub(ic.module), dependencies = ic.dependencies map sub)
			case _ => m
		}
	def crossName(name: String, cross: String): String =
		name + "_" + cross
	def substituteCross(a: Artifact, cross: String): Artifact =
		a.copy(name = crossName(a.name, cross))
	def substituteCrossA(as: Seq[Artifact], cross: String): Seq[Artifact] =
		as.map(art => substituteCross(art, cross))
	def substituteCross(m: ModuleID, cross: String): ModuleID =
		if(m.crossVersion)
			m.copy(name = crossName(m.name, m.crossVersionRemap(cross)), explicitArtifacts = substituteCrossA(m.explicitArtifacts, cross))
		else
			m
		
	private def toIvyArtifact(moduleID: ModuleDescriptor, a: Artifact, configurations: Iterable[String]): MDArtifact =
	{
		val artifact = new MDArtifact(moduleID, a.name, a.`type`, a.extension, null, extra(a, false))
		configurations.foreach(artifact.addConfiguration)
		artifact
	}
	def getExtraAttributes(revID: ExtendableItem): Map[String,String] =
	{
			import collection.JavaConverters._
		revID.getExtraAttributes.asInstanceOf[java.util.Map[String,String]].asScala.toMap
	}
	private[sbt] def extra(artifact: Artifact, unqualify: Boolean = false): java.util.Map[String, String] =
	{
		val ea = artifact.classifier match { case Some(c) => artifact.extra("e:classifier" -> c); case None => artifact }
		javaMap(ea.extraAttributes, unqualify)
	}
	private[sbt] def javaMap(m: Map[String,String], unqualify: Boolean = false) =
	{
		val map = if(unqualify) m map { case (k, v) => (k.stripPrefix("e:"), v) } else m
		if(map.isEmpty) null else scala.collection.JavaConversions.asJavaMap(map)
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
	private def wrapped(module: ModuleID, dependencies: NodeSeq) =
	{
		<ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
			{ if(hasInfo(module, dependencies))
				NodeSeq.Empty
			else
				addExtraAttributes(defaultInfo(module), module.extraAttributes)
			}
			{dependencies}
			{
				// this is because Ivy adds a default artifact if none are specified.
				if(dependencies \\ "publications" isEmpty) <publications/> else NodeSeq.Empty
			}
		</ivy-module>
	}
	private[this] def defaultInfo(module: ModuleID): scala.xml.Elem = {
		import module._
		<info organisation={organization} module={name} revision={revision}/>
	}
	private[this] def addExtraAttributes(elem: scala.xml.Elem, extra: Map[String, String]): scala.xml.Elem =
		(elem /: extra) { case (e, (key,value) ) => e % new scala.xml.UnprefixedAttribute(key, value, scala.xml.Null) }
	private def hasInfo(module: ModuleID, x: scala.xml.NodeSeq) =
	{
		val info = <g>{x}</g> \ "info"
		if(!info.isEmpty)
		{
			def check(found: NodeSeq, expected: String, label: String) =
				if(found.isEmpty)
					error("Missing " + label + " in inline Ivy XML.")
				else {
					val str = found.text
					if(str != expected) error("Inconsistent " + label + " in inline Ivy XML.  Expected '" + expected + "', got '" + str + "'")
				}
			check(info \ "@organisation", module.organization, "organisation")
			check(info \ "@module", module.name, "name")
			check(info \ "@revision", module.revision, "version")
		}
		!info.isEmpty
	}
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
			for(excls <- dependency.exclusions)
			{
				for(conf <- dependencyDescriptor.getModuleConfigurations)
				{
					dependencyDescriptor.addExcludeRule(conf, IvyScala.excludeRule(excls.organization, excls.name, excls.configurations))
				}
			}
			moduleID.addDependency(dependencyDescriptor)
		}
	}
	/** This method is used to add inline artifacts to the provided module. */
	def addArtifacts(moduleID: DefaultModuleDescriptor, artifacts: Iterable[Artifact]): Unit =
		for(art <- mapArtifacts(moduleID, artifacts.toSeq); c <- art.getConfigurations)
			moduleID.addArtifact(c, art)
	def addConfigurations(mod: DefaultModuleDescriptor, configurations: Iterable[Configuration]): Unit =
			configurations.foreach(config => mod.addConfiguration(toIvyConfiguration(config)))

	def mapArtifacts(moduleID: ModuleDescriptor, artifacts: Seq[Artifact]): Seq[IArtifact] =
	{
		lazy val allConfigurations = moduleID.getPublicConfigurationsNames
		for(artifact <- artifacts) yield
		{
			val configurationStrings: Iterable[String] =
			{
				val artifactConfigurations = artifact.configurations
				if(artifactConfigurations.isEmpty)
					allConfigurations
				else
					artifactConfigurations.map(_.name)
			}
			toIvyArtifact(moduleID, artifact, configurationStrings)
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
