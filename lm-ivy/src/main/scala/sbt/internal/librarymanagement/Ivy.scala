/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.File
import java.net.URI
import java.util.concurrent.Callable

import org.apache.ivy.Ivy
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.cache.{ CacheMetadataOptions, DefaultRepositoryCacheManager }
import org.apache.ivy.core.event.EventManager
import org.apache.ivy.core.module.descriptor.{
  DefaultArtifact,
  DefaultDependencyArtifactDescriptor,
  MDArtifact,
  Artifact => IArtifact
}
import org.apache.ivy.core.module.descriptor.{
  DefaultDependencyDescriptor,
  DefaultModuleDescriptor,
  DependencyDescriptor,
  License,
  ModuleDescriptor
}
import org.apache.ivy.core.module.descriptor.OverrideDependencyDescriptorMediator
import org.apache.ivy.core.module.id.{ ModuleId, ModuleRevisionId }
import org.apache.ivy.core.resolve._
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.sort.SortEngine
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.util.{ Message, MessageLogger }
import org.apache.ivy.util.extendable.ExtendableItem
import org.apache.ivy.util.url._
import scala.xml.NodeSeq
import scala.collection.mutable
import scala.collection.immutable.ArraySeq
import scala.util.{ Success, Failure }
import sbt.util._
import sbt.librarymanagement.{ ModuleDescriptorConfiguration => InlineConfiguration, _ }
import sbt.librarymanagement.Platform
import sbt.librarymanagement.ivy._
import sbt.librarymanagement.syntax._

import IvyInternalDefaults._
import Resolver.PluginPattern
import ivyint.{
  CachedResolutionResolveCache,
  CachedResolutionResolveEngine,
  ParallelResolveEngine,
  SbtDefaultDependencyDescriptor,
}
import sjsonnew.JsonFormat
import sjsonnew.support.murmurhash.Hasher
import sbt.librarymanagement.ModuleSettings

final class IvySbt(
    val configuration: IvyConfiguration,
) { self =>
  /*
   * ========== Configuration/Setup ============
   * This part configures the Ivy instance by first creating the logger interface to ivy, then IvySettings, and then the Ivy instance.
   * These are lazy so that they are loaded within the right context.  This is important so that no Ivy XML configuration needs to be loaded,
   * saving some time.  This is necessary because Ivy has global state (IvyContext, Message, DocumentBuilder, ...).
   */

  private def withDefaultLogger[T](logger: MessageLogger)(f: => T): T = {
    def action() =
      IvySbt.synchronized {
        val originalLogger = Message.getDefaultLogger
        Message.setDefaultLogger(logger)
        try {
          f
        } finally {
          Message.setDefaultLogger(originalLogger)
        }
      }
    // Ivy is not thread-safe nor can the cache be used concurrently.
    // If provided a GlobalLock, we can use that to ensure safe access to the cache.
    // Otherwise, we can at least synchronize within the JVM.
    //   For thread-safety in particular, Ivy uses a static DocumentBuilder, which is not thread-safe.
    configuration.lock match {
      case Some(lock) => lock(ivyLockFile, new Callable[T] { def call = action() })
      case None       => action()
    }
  }

  private lazy val basicUrlHandler: URLHandler = new BasicURLHandler

  private lazy val settings: IvySettings = {
    val dispatcher: URLHandlerDispatcher = URLHandlerRegistry.getDefault match {
      // If the default is already a URLHandlerDispatcher then just use that
      case disp: URLHandlerDispatcher => disp

      // Otherwise wrap the existing URLHandler in a URLHandlerDispatcher
      // while retaining the existing URLHandler as the default.
      case default =>
        val disp: URLHandlerDispatcher = new URLHandlerDispatcher()
        disp.setDefault(default)
        URLHandlerRegistry.setDefault(disp)
        disp
    }

    // Ignore configuration.updateOptions.gigahorse due to sbt/sbt#6912
    val urlHandler: URLHandler = basicUrlHandler

    // Only set the urlHandler for the http/https protocols so we do not conflict with any other plugins
    // that might register other protocol handlers.
    // For example https://github.com/frugalmechanic/fm-sbt-s3-resolver registers "s3"
    dispatcher.setDownloader("http", urlHandler)
    dispatcher.setDownloader("https", urlHandler)

    val is = new IvySettings
    is.setCircularDependencyStrategy(
      configuration.updateOptions.circularDependencyLevel.ivyStrategy
    )
    CustomPomParser.registerDefault
    val log = getLog(configuration.log)

    configuration match {
      case e: ExternalIvyConfiguration =>
        val baseDirectory = getBaseDirectory(e.baseDirectory)
        is.setBaseDir(baseDirectory)
        IvySbt.addResolvers(e.extraResolvers, is, log)
        IvySbt.loadURI(is, e.uri.getOrElse(sys.error("uri must be specified!")))
      case i: InlineIvyConfiguration =>
        val paths = getIvyPaths(i.paths)
        is.setBaseDir(new File(paths.baseDirectory))
        is.setVariable("ivy.checksums", i.checksums mkString ",")
        is.setVariable(ConvertResolver.ManagedChecksums, i.managedChecksums.toString)
        paths.ivyHome.foreach { (h) => is.setDefaultIvyUserDir(new File(h)) }
        IvySbt.configureCache(is, i.resolutionCacheDir)
        IvySbt.setResolvers(is, i.resolvers, i.otherResolvers, configuration.updateOptions, log)
        IvySbt.setModuleConfigurations(is, i.moduleConfigurations, log)
    }
    is
  }

  /**
   * Defines a parallel [[CachedResolutionResolveEngine]].
   *
   * This is defined here because it needs access to [[mkIvy]].
   */
  private class ParallelCachedResolutionResolveEngine(
      settings: IvySettings,
      eventManager: EventManager,
      sortEngine: SortEngine
  ) extends ParallelResolveEngine(settings, eventManager, sortEngine)
      with CachedResolutionResolveEngine {
    def makeInstance: Ivy = mkIvy
    val cachedResolutionResolveCache: CachedResolutionResolveCache =
      IvySbt.cachedResolutionResolveCache
    val projectResolver: Option[ProjectResolver] = {
      val res = settings.getResolver(ProjectResolver.InterProject)
      Option(res.asInstanceOf[ProjectResolver])
    }
  }

  /**
   * Provides a default ivy implementation that decides which resolution
   * engine to use depending on the passed ivy configuration options.
   */
  private class IvyImplementation extends Ivy {
    private val loggerEngine = new SbtMessageLoggerEngine
    override def getLoggerEngine: SbtMessageLoggerEngine = loggerEngine
    override def bind(): Unit = {
      val settings = getSettings
      val eventManager = new EventManager()
      val sortEngine = new SortEngine(settings)

      // We inject the deps we need before we can hook our resolve engine.
      setSortEngine(sortEngine)
      setEventManager(eventManager)

      val resolveEngine = {
        // Decide to use cached resolution if user enabled it
        if (configuration.updateOptions.cachedResolution)
          new ParallelCachedResolutionResolveEngine(settings, eventManager, sortEngine)
        else new ParallelResolveEngine(settings, eventManager, sortEngine)
      }

      setResolveEngine(resolveEngine)
      super.bind()
    }
  }

  private[sbt] def mkIvy: Ivy = {
    val ivy = new IvyImplementation()
    ivy.setSettings(settings)
    ivy.bind()
    val logger = new IvyLoggerInterface(getLog(configuration.log))
    ivy.getLoggerEngine.pushLogger(logger)
    ivy
  }

  private lazy val ivy: Ivy = mkIvy
  // Must be the same file as is used in Update in the launcher
  private lazy val ivyLockFile = new File(settings.getDefaultIvyUserDir, ".sbt.ivy.lock")

  // ========== End Configuration/Setup ============

  /** Uses the configured Ivy instance within a safe context. */
  def withIvy[T](log: Logger)(f: Ivy => T): T =
    withIvy(new IvyLoggerInterface(log))(f)

  def withIvy[T](log: MessageLogger)(f: Ivy => T): T =
    withDefaultLogger(log) {
      // See #429 - We always insert a helper authenticator here which lets us get more useful authentication errors.
      ivyint.ErrorMessageAuthenticator.install()
      ivy.pushContext()
      ivy.getLoggerEngine.pushLogger(log)
      try {
        f(ivy)
      } finally {
        ivy.getLoggerEngine.popLogger()
        ivy.popContext()
      }
    }

  /** Cleans cached resolution cache. */
  private[sbt] def cleanCachedResolutionCache(): Unit = {
    if (!configuration.updateOptions.cachedResolution) ()
    else IvySbt.cachedResolutionResolveCache.clean()
  }

  /**
   * In the new POM format of sbt plugins, we append the sbt-cross version _2.12_1.0 to
   * the module artifactId, and the artifactIds of its dependencies that are sbt plugins.
   *
   * The goal is to produce a valid Maven POM, a POM that Maven can resolve:
   * Maven will try and succeed to resolve the POM of pattern:
   * <org>/<artifact-name>_2.12_1.0/<version>/<artifact-name>_2.12_1.0-<version>.pom
   */
  final class Module(rawModuleSettings: ModuleSettings, appendSbtCrossVersion: Boolean)
      extends sbt.librarymanagement.ModuleDescriptor { self =>

    def this(rawModuleSettings: ModuleSettings) =
      this(rawModuleSettings, appendSbtCrossVersion = false)

    val moduleSettings: ModuleSettings =
      rawModuleSettings match {
        case ic: InlineConfiguration =>
          val icWithCross: ModuleSettings = IvySbt.substituteCross(ic)
          if appendSbtCrossVersion then IvySbt.appendSbtCrossVersion(icWithCross)
          else icWithCross
        case m => m
      }

    def directDependencies: Vector[ModuleID] =
      moduleSettings match {
        case x: InlineConfiguration => x.dependencies
        case _                      => Vector()
      }

    def configurations =
      moduleSettings match {
        case ic: InlineConfiguration => ic.configurations
        case _: PomConfiguration     => Configurations.default ++ Configurations.defaultInternal
        case _: IvyFileConfiguration =>
          Configurations.default ++ Configurations.defaultInternal
      }

    def scalaModuleInfo: Option[ScalaModuleInfo] = moduleSettings.scalaModuleInfo

    def owner = IvySbt.this
    def withModule[T](log: Logger)(f: (Ivy, DefaultModuleDescriptor, String) => T): T =
      withIvy[T](log) { ivy =>
        f(ivy, moduleDescriptor0, defaultConfig0)
      }

    def moduleDescriptor(log: Logger): DefaultModuleDescriptor = withModule(log)((_, md, _) => md)
    def dependencyMapping(log: Logger): (ModuleRevisionId, ModuleDescriptor) = {
      val md = moduleDescriptor(log)
      (md.getModuleRevisionId, md)
    }
    def defaultConfig(log: Logger): String = withModule(log)((_, _, dc) => dc)
    // these should only be referenced by withModule because lazy vals synchronize on this object
    // withIvy explicitly locks the IvySbt object, so they have to be done in the right order to avoid deadlock
    private[this] lazy val (moduleDescriptor0: DefaultModuleDescriptor, defaultConfig0: String) = {
      val (baseModule, baseConfiguration) =
        moduleSettings match {
          case ic: InlineConfiguration   => configureInline(ic, getLog(configuration.log))
          case pc: PomConfiguration      => configurePom(pc)
          case ifc: IvyFileConfiguration => configureIvyFile(ifc)
        }

      val configs = configurations
      moduleSettings.scalaModuleInfo foreach { is =>
        val svc = configs filter Configurations.underScalaVersion map { _.name }
        IvyScalaUtil.checkModule(baseModule, svc, getLog(configuration.log))(is)
      }
      IvySbt.addExtraNamespace(baseModule)
      (baseModule, baseConfiguration)
    }
    private def configureInline(ic: InlineConfiguration, log: Logger) = {
      import ic._
      val moduleID = newConfiguredModuleID(module, moduleInfo, ic.configurations)
      IvySbt.setConflictManager(moduleID, conflictManager, ivy.getSettings)
      val defaultConf = defaultConfiguration getOrElse Configuration.of(
        "Default",
        ModuleDescriptor.DEFAULT_CONFIGURATION
      )
      log.debug(
        s"Using inline dependencies specified in Scala${(if (ivyXML.isEmpty) "" else " and XML")}."
      )

      val parser = IvySbt.parseIvyXML(
        ivy.getSettings,
        IvySbt.wrapped(module, ivyXML),
        moduleID,
        defaultConf.name,
        ic.validate
      )
      IvySbt.addMainArtifact(moduleID)
      IvySbt.addOverrides(moduleID, overrides, ivy.getSettings.getMatcher(PatternMatcher.EXACT))
      IvySbt.addExcludes(moduleID, excludes, ic.scalaModuleInfo)
      val transformedDeps = IvySbt.overrideDirect(dependencies, overrides)
      IvySbt.addDependencies(moduleID, transformedDeps, parser)
      (moduleID, parser.getDefaultConf)
    }
    private def newConfiguredModuleID(
        module: ModuleID,
        moduleInfo: ModuleInfo,
        configurations: Iterable[Configuration]
    ) = {
      val mod = new DefaultModuleDescriptor(IvySbt.toID(module), "release", null, false)
      mod.setLastModified(System.currentTimeMillis)
      mod.setDescription(moduleInfo.description)
      moduleInfo.homepage foreach { h =>
        mod.setHomePage(h.toString)
      }
      moduleInfo.licenses foreach { l =>
        mod.addLicense(new License(l._1, l._2.toString))
      }
      IvySbt.addConfigurations(mod, configurations)
      IvySbt.addArtifacts(mod, module.explicitArtifacts)
      mod
    }

    /** Parses the Maven pom 'pomFile' from the given `PomConfiguration`. */
    private def configurePom(pc: PomConfiguration) = {
      val md = CustomPomParser.default.parseDescriptor(settings, toURL(pc.file), pc.validate)
      val dmd = IvySbt.toDefaultModuleDescriptor(md)
      IvySbt.addConfigurations(dmd, Configurations.defaultInternal)
      val defaultConf = Configurations.DefaultMavenConfiguration.name
      for (is <- pc.scalaModuleInfo) if (pc.autoScalaTools) {
        val confParser = new CustomXmlParser.CustomParser(settings, Some(defaultConf))
        confParser.setMd(dmd)
        addScalaToolDependencies(dmd, confParser, is)
      }
      (dmd, defaultConf)
    }

    /** Parses the Ivy file 'ivyFile' from the given `IvyFileConfiguration`. */
    private def configureIvyFile(ifc: IvyFileConfiguration) = {
      val parser = new CustomXmlParser.CustomParser(settings, None)
      parser.setValidate(ifc.validate)
      parser.setSource(toURL(ifc.file))
      parser.parse()
      val dmd = IvySbt.toDefaultModuleDescriptor(parser.getModuleDescriptor())
      for (is <- ifc.scalaModuleInfo)
        if (ifc.autoScalaTools)
          addScalaToolDependencies(dmd, parser, is)
      (dmd, parser.getDefaultConf)
    }
    private def addScalaToolDependencies(
        dmd: DefaultModuleDescriptor,
        parser: CustomXmlParser.CustomParser,
        is: ScalaModuleInfo
    ): Unit = {
      IvySbt.addConfigurations(dmd, Configurations.ScalaTool :: Nil)
      IvySbt.addDependencies(
        dmd,
        ScalaArtifacts.toolDependencies(is.scalaOrganization, is.scalaFullVersion),
        parser
      )
    }
    private def toURL(file: File) = file.toURI.toURL

    // Todo: We just need writing side of this codec. We can clean up the reads.
    private[sbt] object AltLibraryManagementCodec extends IvyLibraryManagementCodec {
      import sbt.io.Hash
      type InlineIvyHL = (
          Option[IvyPaths],
          Vector[Resolver],
          Vector[Resolver],
          Vector[ModuleConfiguration],
          Vector[String],
          Boolean
      )
      def inlineIvyToHL(i: InlineIvyConfiguration): InlineIvyHL =
        (
          i.paths,
          i.resolvers,
          i.otherResolvers,
          i.moduleConfigurations,
          i.checksums,
          i.managedChecksums
        )

      type ExternalIvyHL = (Option[PlainFileInfo], Array[Byte])
      def externalIvyToHL(e: ExternalIvyConfiguration): ExternalIvyHL =
        (
          e.baseDirectory.map(FileInfo.exists.apply),
          e.uri.map(Hash.contentsIfLocal).getOrElse(Array.empty)
        )

      // Redefine to use a subset of properties, that are serialisable
      override implicit lazy val InlineIvyConfigurationFormat
          : JsonFormat[InlineIvyConfiguration] = {
        def hlToInlineIvy(i: InlineIvyHL): InlineIvyConfiguration = {
          val (
            paths,
            resolvers,
            otherResolvers,
            moduleConfigurations,
            checksums,
            managedChecksums
          ) = i
          InlineIvyConfiguration()
            .withPaths(paths)
            .withResolvers(resolvers)
            .withOtherResolvers(otherResolvers)
            .withModuleConfigurations(moduleConfigurations)
            .withManagedChecksums(managedChecksums)
            .withChecksums(checksums)
        }
        projectFormat[InlineIvyConfiguration, InlineIvyHL](inlineIvyToHL, hlToInlineIvy)
      }

      // Redefine to use a subset of properties, that are serialisable
      override implicit lazy val ExternalIvyConfigurationFormat
          : JsonFormat[ExternalIvyConfiguration] = {
        def hlToExternalIvy(e: ExternalIvyHL): ExternalIvyConfiguration = {
          val (baseDirectory, _) = e
          ExternalIvyConfiguration(
            None,
            Some(NullLogger),
            UpdateOptions(),
            baseDirectory.map(_.file),
            None /* the original uri is destroyed.. */,
            Vector.empty
          )
        }
        projectFormat[ExternalIvyConfiguration, ExternalIvyHL](externalIvyToHL, hlToExternalIvy)
      }

      // Redefine to switch to unionFormat
      override implicit lazy val IvyConfigurationFormat: JsonFormat[IvyConfiguration] =
        unionFormat2[IvyConfiguration, InlineIvyConfiguration, ExternalIvyConfiguration]

      object NullLogger extends sbt.internal.util.BasicLogger {
        override def control(event: sbt.util.ControlEvent.Value, message: => String): Unit = ()
        override def log(level: Level.Value, message: => String): Unit = ()
        override def logAll(events: Seq[sbt.util.LogEvent]): Unit = ()
        override def success(message: => String): Unit = ()
        override def trace(t: => Throwable): Unit = ()
      }
    }

    def extraInputHash: Long = {
      import AltLibraryManagementCodec._
      Hasher.hash(owner.configuration) match {
        case Success(keyHash) => keyHash.toLong
        case Failure(_)       => 0L
      }
    }
  }
}

private[sbt] object IvySbt {
  val DefaultIvyConfigFilename = "ivysettings.xml"
  val DefaultIvyFilename = "ivy.xml"
  val DefaultMavenFilename = "pom.xml"
  val DefaultChecksums = IvyDefaults.defaultChecksums
  private[sbt] def cachedResolutionResolveCache: CachedResolutionResolveCache =
    new CachedResolutionResolveCache

  def defaultIvyFile(project: File) = new File(project, DefaultIvyFilename)
  def defaultIvyConfiguration(project: File) = new File(project, DefaultIvyConfigFilename)
  def defaultPOM(project: File) = new File(project, DefaultMavenFilename)

  def loadURI(is: IvySettings, uri: URI): Unit = {
    if (uri.getScheme == "file")
      is.load(new File(uri)) // IVY-1114
    else
      is.load(uri.toURL)
  }

  /**
   * Sets the resolvers for 'settings' to 'resolvers'.  This is done by creating a new chain and making it the default.
   * 'other' is for resolvers that should be in a different chain.  These are typically used for publishing or other actions.
   */
  private def setResolvers(
      settings: IvySettings,
      resolvers: Seq[Resolver],
      other: Seq[Resolver],
      updateOptions: UpdateOptions,
      log: Logger
  ): Unit = {
    def makeChain(label: String, name: String, rs: Seq[Resolver]) = {
      log.debug(label + " repositories:")
      val chain = resolverChain(name, rs, settings, updateOptions, log)
      settings.addResolver(chain)
      chain
    }
    makeChain("Other", "sbt-other", other)
    val mainChain = makeChain("Default", "sbt-chain", resolvers)
    settings.setDefaultResolver(mainChain.getName)
  }

  // TODO: Expose the changing semantics to the caller so that users can specify a regex
  private[sbt] def isChanging(dd: DependencyDescriptor): Boolean =
    dd.isChanging || isChanging(dd.getDependencyRevisionId)
  private[sbt] def isChanging(module: ModuleID): Boolean =
    module.revision endsWith "-SNAPSHOT"
  private[sbt] def isChanging(mrid: ModuleRevisionId): Boolean =
    mrid.getRevision endsWith "-SNAPSHOT"

  def resolverChain(
      name: String,
      resolvers: Seq[Resolver],
      settings: IvySettings,
      log: Logger
  ): DependencyResolver = resolverChain(name, resolvers, settings, UpdateOptions(), log)

  def resolverChain(
      name: String,
      resolvers: Seq[Resolver],
      settings: IvySettings,
      updateOptions: UpdateOptions,
      log: Logger
  ): DependencyResolver = {
    val ivyResolvers = resolvers.map(r => ConvertResolver(r, settings, updateOptions, log))
    val (projectResolvers, rest) =
      ivyResolvers.partition(_.getName == ProjectResolver.InterProject)
    if (projectResolvers.isEmpty) ivyint.SbtChainResolver(name, rest, settings, updateOptions, log)
    else {
      // Force that we always look at the project resolver first by wrapping the chain resolver
      val delegatedName = s"$name-delegate"
      val delegate = ivyint.SbtChainResolver(delegatedName, rest, settings, updateOptions, log)
      val initialResolvers = projectResolvers :+ delegate
      val freshOptions = UpdateOptions()
        .withLatestSnapshots(false)
        .withModuleResolvers(updateOptions.moduleResolvers)
      ivyint.SbtChainResolver(name, initialResolvers, settings, freshOptions, log)
    }
  }

  def addResolvers(resolvers: Seq[Resolver], settings: IvySettings, log: Logger): Unit = {
    for (r <- resolvers) {
      log.debug("\t" + r)
      settings.addResolver(ConvertResolver(r, settings, UpdateOptions(), log))
    }
  }

  /**
   * A hack to detect if the given artifact is an automatically generated request for a classifier,
   * as opposed to a user-initiated declaration.  It relies on Ivy prefixing classifier with m:, while sbt uses e:.
   * Clearly, it would be better to have an explicit option in Ivy to control this.
   */
  def hasImplicitClassifier(artifact: IArtifact): Boolean = {
    import scala.jdk.CollectionConverters._
    artifact.getQualifiedExtraAttributes.asScala.keys
      .exists(_.asInstanceOf[String] startsWith "m:")
  }
  private def setModuleConfigurations(
      settings: IvySettings,
      moduleConfigurations: Seq[ModuleConfiguration],
      log: Logger
  ): Unit = {
    val existing = settings.getResolverNames
    for (moduleConf <- moduleConfigurations) {
      import moduleConf._
      import IvyPatternHelper._
      import PatternMatcher._
      if (!existing.contains(resolver.name))
        settings.addResolver(ConvertResolver(resolver, settings, UpdateOptions(), log))
      val attributes = javaMap(
        Map(MODULE_KEY -> name, ORGANISATION_KEY -> organization, REVISION_KEY -> revision)
      )
      settings.addModuleConfiguration(
        attributes,
        settings.getMatcher(EXACT_OR_REGEXP),
        resolver.name,
        null,
        null,
        null
      )
    }
  }

  private def configureCache(settings: IvySettings, resCacheDir: Option[File]): Unit = {
    configureResolutionCache(settings, resCacheDir)
    configureRepositoryCache(settings)
  }
  private[this] def configureResolutionCache(settings: IvySettings, resCacheDir: Option[File]) = {
    val base = resCacheDir getOrElse settings.getDefaultResolutionCacheBasedir
    settings.setResolutionCacheManager(new ResolutionCache(base, settings))
  }
  // set the artifact resolver to be the main resolver.
  // this is because sometimes the artifact resolver saved in the cache is not correct
  // the common case is for resolved.getArtifactResolver to be inter-project from a different project's publish-local
  // if there are problems with this, a less aggressive fix might be to only reset the artifact resolver when it is a ProjectResolver
  // a possible problem is that fetching artifacts is slower, due to the full chain being the artifact resolver instead of the specific resolver
  // This also fixes #760, which occurs when metadata exists in a repository, but the artifact doesn't.
  private[sbt] def resetArtifactResolver(
      resolved: ResolvedModuleRevision
  ): ResolvedModuleRevision =
    if (resolved eq null) null
    else {
      val desc = resolved.getDescriptor
      val updatedDescriptor = CustomPomParser.defaultTransform(desc.getParser, desc)
      new ResolvedModuleRevision(
        resolved.getResolver,
        resolved.getResolver,
        updatedDescriptor,
        resolved.getReport,
        resolved.isForce
      )
    }

  private[this] def configureRepositoryCache(settings: IvySettings): Unit = {
    val cacheDir = settings.getDefaultRepositoryCacheBasedir()
    val manager = new DefaultRepositoryCacheManager("default-cache", settings, cacheDir) {
      override def findModuleInCache(
          dd: DependencyDescriptor,
          revId: ModuleRevisionId,
          options: CacheMetadataOptions,
          r: String
      ) = {
        // ignore and reset the resolver- not ideal, but avoids thrashing.
        val resolved = resetArtifactResolver(super.findModuleInCache(dd, revId, options, null))
        // invalidate the cache if the artifact was removed from the local repository
        if (resolved == null) null
        else if (isProjectResolver(resolved.getResolver)) {
          resolved.getReport.getLocalFile.delete()
          null
        } else {
          val origin = resolved.getReport.getArtifactOrigin
          if (!origin.isLocal) resolved
          else {
            val file = new File(origin.getLocation)
            if (file == null || file.exists) resolved
            else {
              resolved.getReport.getLocalFile.delete()
              null
            }
          }
        }
      }
      private[this] def isProjectResolver(r: DependencyResolver): Boolean = r match {
        case _: ProjectResolver => true
        case _                  => false
      }
      // ignore the original resolver wherever possible to avoid issues like #704
      override def saveResolvers(
          descriptor: ModuleDescriptor,
          metadataResolverName: String,
          artifactResolverName: String
      ): Unit = ()
    }
    manager.setArtifactPattern(PluginPattern + manager.getArtifactPattern)
    manager.setDataFilePattern(PluginPattern + manager.getDataFilePattern)
    manager.setIvyPattern(PluginPattern + manager.getIvyPattern)
    manager.setUseOrigin(true)
    manager.setChangingMatcher(PatternMatcher.REGEXP)
    manager.setChangingPattern(".*-SNAPSHOT")
    settings.addRepositoryCacheManager(manager)
    settings.setDefaultRepositoryCacheManager(manager)
  }
  def toIvyConfiguration(configuration: Configuration) = {
    import org.apache.ivy.core.module.descriptor.{ Configuration => IvyConfig }
    import IvyConfig.Visibility._
    import configuration._
    new IvyConfig(
      name,
      if (isPublic) PUBLIC else PRIVATE,
      description,
      extendsConfigs.map(_.name).toArray,
      transitive,
      null
    )
  }
  def addExtraNamespace(dmd: DefaultModuleDescriptor): Unit =
    dmd.addExtraAttributeNamespace("e", "http://ant.apache.org/ivy/extra")

  /** Adds the ivy.xml main artifact. */
  private def addMainArtifact(moduleID: DefaultModuleDescriptor): Unit = {
    val artifact = DefaultArtifact.newIvyArtifact(
      moduleID.getResolvedModuleRevisionId,
      moduleID.getPublicationDate
    )
    moduleID.setModuleArtifact(artifact)
    moduleID.check()
  }
  private def setConflictManager(
      moduleID: DefaultModuleDescriptor,
      conflict: ConflictManager,
      is: IvySettings
  ): Unit = {
    val mid = ModuleId.newInstance(conflict.organization, conflict.module)
    val matcher = is.getMatcher(PatternMatcher.EXACT_OR_REGEXP)
    val manager = is.getConflictManager(conflict.name)
    moduleID.addConflictManager(mid, matcher, manager)
  }

  /** Converts the given sbt module id into an Ivy ModuleRevisionId. */
  def toID(m: ModuleID) = {
    import m._
    ModuleRevisionId.newInstance(
      organization,
      name,
      branchName.orNull,
      revision,
      javaMap(extraAttributes)
    )
  }

  private def substituteCross(m: ModuleSettings): ModuleSettings =
    m.scalaModuleInfo match {
      case None     => m
      case Some(is) => substituteCross(m, is.scalaFullVersion, is.scalaBinaryVersion, is.platform)
    }

  private def substituteCross(
      m: ModuleSettings,
      scalaFullVersion: String,
      scalaBinaryVersion: String,
      platform: Option[String]
  ): ModuleSettings = {
    m match
      case ic: InlineConfiguration =>
        val applyPlatform: ModuleID => ModuleID = substitutePlatform(platform)
        val applyCross = CrossVersion(scalaFullVersion, scalaBinaryVersion)
        val transform: ModuleID => ModuleID = (m: ModuleID) => applyCross(applyPlatform(m))
        def propagateCrossVersion(moduleID: ModuleID): ModuleID = {
          val crossExclusions: Vector[ExclusionRule] =
            moduleID.exclusions.map(CrossVersion.substituteCross(_, ic.scalaModuleInfo))
          transform(moduleID)
            .withExclusions(crossExclusions)
        }
        ic.withModule(transform(ic.module))
          .withDependencies(ic.dependencies.map(propagateCrossVersion))
          .withOverrides(ic.overrides map transform)
      case m => m
  }

  private def substitutePlatform(platform: Option[String]): ModuleID => ModuleID = {
    def addSuffix(m: ModuleID, platformName: String): ModuleID =
      platformName match
        case "" | Platform.jvm => m
        case _                 => m.withName(s"${m.name}_$platformName")
    (m: ModuleID) =>
      m.crossVersion match
        case _: Disabled => m
        case _ =>
          (platform, m.platformOpt) match
            case (Some(p), None) => addSuffix(m, p)
            case (_, Some(p))    => addSuffix(m, p)
            case _               => m
  }

  private def appendSbtCrossVersion(m: ModuleSettings): ModuleSettings =
    m match
      case ic: InlineConfiguration =>
        ic.withModule(appendSbtCrossVersion(ic.module))
          .withDependencies(ic.dependencies.map(appendSbtCrossVersion))
          .withOverrides(ic.overrides.map(appendSbtCrossVersion))
      case m => m

  private def appendSbtCrossVersion(mid: ModuleID): ModuleID = {
    val crossVersion = for {
      scalaVersion <- mid.extraAttributes.get("e:scalaVersion")
      sbtVersion <- mid.extraAttributes.get("e:sbtVersion")
    } yield s"_${scalaVersion}_$sbtVersion"
    crossVersion
      .filter(!mid.name.endsWith(_))
      .map(cv => mid.withName(mid.name + cv))
      .getOrElse(mid)
  }

  private def toIvyArtifact(
      moduleID: ModuleDescriptor,
      a: Artifact,
      allConfigurations: Vector[ConfigRef]
  ): MDArtifact = {
    val artifact = new MDArtifact(moduleID, a.name, a.`type`, a.extension, null, extra(a, false))
    copyConfigurations(
      a,
      (ref: ConfigRef) => { artifact.addConfiguration(ref.name) },
      allConfigurations
    )
    artifact
  }
  def getExtraAttributes(revID: ExtendableItem): Map[String, String] = {
    import scala.jdk.CollectionConverters._
    revID.getExtraAttributes.asInstanceOf[java.util.Map[String, String]].asScala.toMap
  }
  private[sbt] def extra(
      artifact: Artifact,
      unqualify: Boolean = false
  ): java.util.Map[String, String] = {
    val ea = artifact.classifier match {
      case Some(c) => artifact.extra("e:classifier" -> c); case None => artifact
    }
    javaMap(ea.extraAttributes, unqualify)
  }
  private[sbt] def javaMap(m: Map[String, String], unqualify: Boolean = false) = {
    import scala.jdk.CollectionConverters._
    val map = if (unqualify) m map { case (k, v) => (k.stripPrefix("e:"), v) }
    else m
    if (map.isEmpty) null else map.asJava
  }

  /** Creates a full ivy file for 'module' using the 'dependencies' XML as the part after the &lt;info&gt;...&lt;/info&gt; section. */
  private def wrapped(module: ModuleID, dependencies: NodeSeq) = {
    <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
      {
      if (hasInfo(module, dependencies))
        NodeSeq.Empty
      else
        addExtraAttributes(defaultInfo(module), module.extraAttributes)
    }
      {dependencies}
      {
      // this is because Ivy adds a default artifact if none are specified.
      if ((dependencies \\ "publications").isEmpty) <publications/> else NodeSeq.Empty
    }
    </ivy-module>
  }
  private[this] def defaultInfo(module: ModuleID): scala.xml.Elem = {
    import module._
    val base = <info organisation={organization} module={name} revision={revision}/>
    branchName.fold(base) { br =>
      base % new scala.xml.UnprefixedAttribute("branch", br, scala.xml.Null)
    }
  }
  private[this] def addExtraAttributes(
      elem: scala.xml.Elem,
      extra: Map[String, String]
  ): scala.xml.Elem =
    extra.foldLeft(elem) { case (e, (key, value)) =>
      e % new scala.xml.UnprefixedAttribute(key, value, scala.xml.Null)
    }
  private def hasInfo(module: ModuleID, x: scala.xml.NodeSeq) = {
    val info = <g>{x}</g> \ "info"
    if (info.nonEmpty) {
      def check(found: NodeSeq, expected: String, label: String) =
        if (found.isEmpty) sys.error("Missing " + label + " in inline Ivy XML.")
        else {
          val str = found.text
          if (str != expected)
            sys.error(
              "Inconsistent " + label + " in inline Ivy XML.  Expected '" + expected + "', got '" + str + "'"
            )
        }
      check(info \ "@organisation", module.organization, "organisation")
      check(info \ "@module", module.name, "name")
      check(info \ "@revision", module.revision, "version")
    }
    info.nonEmpty
  }

  /** Parses the given in-memory Ivy file 'xml', using the existing 'moduleID' and specifying the given 'defaultConfiguration'. */
  private def parseIvyXML(
      settings: IvySettings,
      xml: scala.xml.NodeSeq,
      moduleID: DefaultModuleDescriptor,
      defaultConfiguration: String,
      validate: Boolean
  ): CustomXmlParser.CustomParser =
    parseIvyXML(settings, xml.toString, moduleID, defaultConfiguration, validate)

  /** Parses the given in-memory Ivy file 'xml', using the existing 'moduleID' and specifying the given 'defaultConfiguration'. */
  private def parseIvyXML(
      settings: IvySettings,
      xml: String,
      moduleID: DefaultModuleDescriptor,
      defaultConfiguration: String,
      validate: Boolean
  ): CustomXmlParser.CustomParser = {
    val parser = new CustomXmlParser.CustomParser(settings, Some(defaultConfiguration))
    parser.setMd(moduleID)
    parser.setValidate(validate)
    parser.setInput(xml.getBytes)
    parser.parse()
    parser
  }

  def inconsistentDuplicateWarning(moduleID: DefaultModuleDescriptor): List[String] = {
    import IvyRetrieve.toModuleID
    val dds = ArraySeq.unsafeWrapArray(moduleID.getDependencies)
    val deps = dds flatMap { dd =>
      val module = toModuleID(dd.getDependencyRevisionId)
      dd.getModuleConfigurations map (c => module.withConfigurations(Some(c)))
    }
    inconsistentDuplicateWarning(deps)
  }

  def inconsistentDuplicateWarning(dependencies: Seq[ModuleID]): List[String] = {
    val warningHeader =
      "Multiple dependencies with the same organization/name but different versions. To avoid conflict, pick one version:"
    val out: mutable.ListBuffer[String] = mutable.ListBuffer()
    (dependencies groupBy { dep =>
      (dep.organization, dep.name, dep.configurations)
    }) foreach {
      case (_, vs) if vs.size > 1 =>
        val v0 = vs.head
        (vs find { _.revision != v0.revision }) foreach { _ =>
          out += s" * ${v0.organization}:${v0.name}:(" + (vs map { _.revision })
            .mkString(", ") + ")"
        }
      case _ => ()
    }
    if (out.isEmpty) Nil
    else warningHeader :: out.toList
  }

  /** This method is used to add inline dependencies to the provided module. */
  def addDependencies(
      moduleID: DefaultModuleDescriptor,
      dependencies: Seq[ModuleID],
      parser: CustomXmlParser.CustomParser
  ): Unit = {
    val converted = dependencies map { dependency =>
      convertDependency(moduleID, dependency, parser)
    }
    val unique =
      if (hasDuplicateDependencies(converted)) mergeDuplicateDefinitions(converted) else converted
    unique foreach moduleID.addDependency
  }

  /** Determines if there are multiple dependency definitions for the same dependency ID. */
  def hasDuplicateDependencies(dependencies: Seq[DependencyDescriptor]): Boolean = {
    val ids = dependencies.map(_.getDependencyRevisionId)
    ids.toSet.size != ids.size
  }

  /**
   * Combines the artifacts, includes, and excludes of duplicate dependency definitions.
   * This is somewhat fragile and is only intended to workaround Ivy (or sbt's use of Ivy) not handling this case properly.
   * In particular, Ivy will create multiple dependency entries when converting a pom with a dependency on a classified artifact and a non-classified artifact:
   *   https://github.com/sbt/sbt/issues/468
   * It will also allow users to declare dependencies on classified modules in different configurations:
   *   https://groups.google.com/d/topic/simple-build-tool/H2MdAARz6e0/discussion
   * as well as basic multi-classifier handling: #285, #419, #480.
   * Multiple dependency definitions should otherwise be avoided as much as possible.
   */
  def mergeDuplicateDefinitions(
      dependencies: Seq[DependencyDescriptor]
  ): Seq[DependencyDescriptor] = {
    // need to preserve basic order of dependencies: can't use dependencies.groupBy
    val deps = new java.util.LinkedHashMap[ModuleRevisionId, List[DependencyDescriptor]]
    for (dd <- dependencies) {
      val id = dd.getDependencyRevisionId
      val updated = deps get id match {
        case null => dd :: Nil
        case v    => dd :: v
      }
      deps.put(id, updated)
    }

    import scala.jdk.CollectionConverters._
    deps.values.asScala.toSeq.flatMap { dds =>
      val mergeable = dds.lazyZip(dds.tail).forall(ivyint.MergeDescriptors.mergeable _)
      if (mergeable) dds.reverse.reduceLeft(ivyint.MergeDescriptors.apply _) :: Nil else dds
    }
  }

  /** Transforms an sbt ModuleID into an Ivy DefaultDependencyDescriptor. */
  def convertDependency(
      moduleID: DefaultModuleDescriptor,
      dependency: ModuleID,
      parser: CustomXmlParser.CustomParser
  ): DefaultDependencyDescriptor = {
    val dependencyDescriptor = new DefaultDependencyDescriptor(
      moduleID,
      toID(dependency),
      dependency.isForce,
      dependency.isChanging,
      dependency.isTransitive
    ) with SbtDefaultDependencyDescriptor {
      def dependencyModuleId = dependency
    }
    dependency.configurations match {
      case None => // The configuration for this dependency was not explicitly specified, so use the default
        parser.parseDepsConfs(parser.getDefaultConf, dependencyDescriptor)
      case Some(
            confs
          ) => // The configuration mapping (looks like: test->default) was specified for this dependency
        parser.parseDepsConfs(confs, dependencyDescriptor)
    }
    for (artifact <- dependency.explicitArtifacts) {
      import artifact.{ name, `type`, extension, url }
      val extraMap = extra(artifact)
      val ivyArtifact = new DefaultDependencyArtifactDescriptor(
        dependencyDescriptor,
        name,
        `type`,
        extension,
        url.map(_.toURL).orNull,
        extraMap
      )
      copyConfigurations(artifact, (ref: ConfigRef) => { ivyArtifact.addConfiguration(ref.name) })
      for (conf <- dependencyDescriptor.getModuleConfigurations)
        dependencyDescriptor.addDependencyArtifact(conf, ivyArtifact)
    }
    for (excls <- dependency.exclusions) {
      for (conf <- dependencyDescriptor.getModuleConfigurations) {
        dependencyDescriptor.addExcludeRule(
          conf,
          IvyScalaUtil.excludeRule(
            excls.organization,
            excls.name,
            excls.configurations map { _.name },
            excls.artifact
          )
        )
      }
    }
    for (incls <- dependency.inclusions) {
      for (conf <- dependencyDescriptor.getModuleConfigurations) {
        dependencyDescriptor.addIncludeRule(
          conf,
          IvyScalaUtil.includeRule(
            incls.organization,
            incls.name,
            incls.configurations map { _.name },
            incls.artifact
          )
        )
      }
    }

    dependencyDescriptor
  }
  def copyConfigurations(artifact: Artifact, addConfiguration: ConfigRef => Unit): Unit =
    copyConfigurations(artifact, addConfiguration, Vector(ConfigRef("*")))

  private[this] def copyConfigurations(
      artifact: Artifact,
      addConfiguration: ConfigRef => Unit,
      allConfigurations: Vector[ConfigRef]
  ): Unit = {
    val confs =
      if (artifact.configurations.isEmpty) allConfigurations
      else artifact.configurations
    confs foreach addConfiguration
  }

  def addExcludes(
      moduleID: DefaultModuleDescriptor,
      excludes: Seq[ExclusionRule],
      scalaModuleInfo: Option[ScalaModuleInfo]
  ): Unit = excludes.foreach(exclude => addExclude(moduleID, scalaModuleInfo)(exclude))

  def addExclude(moduleID: DefaultModuleDescriptor, scalaModuleInfo: Option[ScalaModuleInfo])(
      exclude0: ExclusionRule
  ): Unit = {
    // this adds _2.11 postfix
    val exclude = CrossVersion.substituteCross(exclude0, scalaModuleInfo)
    val confs =
      if (exclude.configurations.isEmpty) moduleID.getConfigurationsNames.toList
      else exclude.configurations map { _.name }
    val excludeRule =
      IvyScalaUtil.excludeRule(exclude.organization, exclude.name, confs, exclude.artifact)
    moduleID.addExcludeRule(excludeRule)
  }

  def addOverrides(
      moduleID: DefaultModuleDescriptor,
      overrides: Vector[ModuleID],
      matcher: PatternMatcher
  ): Unit =
    overrides foreach addOverride(moduleID, matcher)
  def addOverride(moduleID: DefaultModuleDescriptor, matcher: PatternMatcher)(
      overrideDef: ModuleID
  ): Unit = {
    val overrideID = new ModuleId(overrideDef.organization, overrideDef.name)
    val overrideWith = new OverrideDependencyDescriptorMediator(null, overrideDef.revision)
    moduleID.addDependencyDescriptorMediator(overrideID, matcher, overrideWith)
  }

  /**
   * It is necessary to explicitly modify direct dependencies because Ivy gives
   * "IllegalStateException: impossible to get artifacts when data has not been loaded."
   * when a direct dependency is overridden with a newer version."
   */
  def overrideDirect(dependencies: Seq[ModuleID], overrides: Vector[ModuleID]): Seq[ModuleID] = {
    def key(id: ModuleID) = (id.organization, id.name)
    val overridden = overrides.map(id => (key(id), id.revision)).toMap
    dependencies map { dep =>
      overridden get key(dep) match {
        case Some(rev) => dep.withRevision(rev)
        case None      => dep
      }
    }
  }

  /** This method is used to add inline artifacts to the provided module. */
  def addArtifacts(moduleID: DefaultModuleDescriptor, artifacts: Iterable[Artifact]): Unit =
    for (art <- mapArtifacts(moduleID, artifacts.toSeq); c <- art.getConfigurations)
      moduleID.addArtifact(c, art)

  def addConfigurations(
      mod: DefaultModuleDescriptor,
      configurations: Iterable[Configuration]
  ): Unit =
    configurations.foreach(config => mod.addConfiguration(toIvyConfiguration(config)))

  def mapArtifacts(moduleID: ModuleDescriptor, artifacts: Seq[Artifact]): Seq[IArtifact] = {
    lazy val allConfigurations = moduleID.getPublicConfigurationsNames.toVector map ConfigRef.apply
    for (artifact <- artifacts) yield toIvyArtifact(moduleID, artifact, allConfigurations)
  }

  /**
   * This code converts the given ModuleDescriptor to a DefaultModuleDescriptor by casting or generating an error.
   * Ivy 2.0.0 always produces a DefaultModuleDescriptor.
   */
  private def toDefaultModuleDescriptor(md: ModuleDescriptor) =
    md match {
      case dmd: DefaultModuleDescriptor => dmd
      case _                            => sys.error("Unknown ModuleDescriptor type.")
    }
  def getConfigurations(
      module: ModuleDescriptor,
      configurations: Option[Iterable[Configuration]]
  ) =
    configurations match {
      case Some(confs) => confs.map(_.name).toList.toArray
      case None        => module.getPublicConfigurationsNames
    }
}
