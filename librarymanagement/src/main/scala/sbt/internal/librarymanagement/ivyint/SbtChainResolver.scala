package sbt.internal.librarymanagement
package ivyint

import java.io.{ ByteArrayOutputStream, File, PrintWriter }
import java.text.ParseException
import java.util.Date

import org.apache.ivy.core.cache.ArtifactOrigin
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.{ IvyContext, LogOptions }
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.descriptor.{ Artifact => IArtifact }
import org.apache.ivy.core.resolve.{ ResolveData, ResolvedModuleRevision }
import org.apache.ivy.plugins.latest.LatestStrategy
import org.apache.ivy.plugins.repository.file.{ FileResource, FileRepository => IFileRepository }
import org.apache.ivy.plugins.repository.url.URLResource
import org.apache.ivy.plugins.resolver._
import org.apache.ivy.plugins.resolver.util.{ HasLatestStrategy, ResolvedResource }
import org.apache.ivy.util.{ Message, StringUtils => IvyStringUtils }
import sbt.util.Logger
import sbt.librarymanagement._

import scala.util.control.NonFatal

private[sbt] case class SbtChainResolver(
    name: String,
    resolvers: Seq[DependencyResolver],
    settings: IvySettings,
    updateOptions: UpdateOptions,
    log: Logger
) extends ChainResolver {

  override def equals(o: Any): Boolean = o match {
    case o: SbtChainResolver =>
      this.name == o.name &&
        this.resolvers == o.resolvers &&
        this.settings == o.settings &&
        this.updateOptions == o.updateOptions
    case _ => false
  }

  override def hashCode: Int = {
    var hash = 1
    hash = hash * 31 + this.name.##
    hash = hash * 31 + this.resolvers.##
    hash = hash * 31 + this.settings.##
    hash = hash * 31 + this.updateOptions.##
    hash
  }

  // TODO - We need to special case the project resolver so it always "wins" when resolving with inter-project dependencies.

  // Initialize ourselves.
  setName(name)
  setReturnFirst(true)
  setCheckmodified(false)
  // Here we append all the resolvers we were passed *AND* look for
  // a project resolver, which we will special-case.
  resolvers.foreach(add)

  // Technically, this should be applied to module configurations.
  // That would require custom subclasses of all resolver types in ConvertResolver (a delegation approach does not work).
  // It would be better to get proper support into Ivy.
  // A workaround is to configure the ModuleConfiguration resolver to be a ChainResolver.
  //
  // This method is only used by the pom parsing code in Ivy to find artifacts it doesn't know about.
  // In particular, a) it looks up source and javadoc classifiers b) it looks up a main artifact for packaging="pom"
  // sbt now provides the update-classifiers or requires explicitly specifying classifiers explicitly
  // Providing a main artifact for packaging="pom" does not seem to be correct and the lookup can be expensive.
  //
  // Ideally this could just skip the lookup, but unfortunately several artifacts in practice do not follow the
  // correct behavior for packaging="pom" and so it is only skipped for source/javadoc classifiers.
  override def locate(artifact: IArtifact): ArtifactOrigin =
    if (IvySbt.hasImplicitClassifier(artifact)) null else super.locate(artifact)

  override def getDependency(dd: DependencyDescriptor, data: ResolveData): ResolvedModuleRevision = {
    if (data.getOptions.getLog != LogOptions.LOG_QUIET)
      Message.debug("Resolving " + dd.getDependencyRevisionId + " ...")
    val gd = CustomSbtResolution.getDependency(dd, data)
    val mod = IvySbt.resetArtifactResolver(gd)
    mod
  }

  /** Implements the custom sbt chain resolution with support for snapshots and caching. */
  private object CustomSbtResolution {
    def getCached(dd: DependencyDescriptor,
                  data: ResolveData,
                  resolved0: Option[ResolvedModuleRevision]): Option[ResolvedModuleRevision] = {
      resolved0.orElse {
        val resolverName = getName
        Message.verbose(s"$resolverName: Checking cache for: $dd")
        Option(findModuleInCache(dd, data, true)).map { moduleRev =>
          Message.verbose(s"$resolverName: module revision found in cache: ${moduleRev.getId}")
          forcedRevision(moduleRev)
        }
      }
    }

    /* Copy pasted from `IvyStringUtils` to handle `Throwable` */
    private def getStackTrace(e: Throwable): String = {
      if (e == null) return ""
      val baos = new ByteArrayOutputStream()
      val printWriter = new PrintWriter(baos)
      e.printStackTrace(printWriter)
      printWriter.flush()
      val stackTrace = new String(baos.toByteArray)
      printWriter.close()
      stackTrace
    }

    /** If None, module was not found. Otherwise, hit. */
    type TriedResolution = Option[(ResolvedModuleRevision, DependencyResolver)]

    /** Attempts to resolve the artifact from each of the resolvers in the chain.
     *
     * Contract:
     *   1. It doesn't resolve anything when there is a resolved module, `isReturnFirst` is
     *      enabled and `useLatest` is false (meaning that resolution is pure, no SNAPSHOT).
     *   2. Otherwise, we try to resolve it.
     *
     * @param resolved0 The perhaps already resolved module.
     * @param useLatest Whether snapshot resolution should be enabled.
     * @param data The resolve data to use.
     * @param descriptor The dependency descriptor of the in-resolution module.
     */
    def getResults(
        resolved0: Option[ResolvedModuleRevision],
        useLatest: Boolean,
        data: ResolveData,
        descriptor: DependencyDescriptor
    ): Seq[Either[Throwable, TriedResolution]] = {
      var currentlyResolved = resolved0

      def performResolution(
          resolver: DependencyResolver): Option[(ResolvedModuleRevision, DependencyResolver)] = {
        // Resolve all resolvers when the module is changing
        val previouslyResolved = currentlyResolved
        if (useLatest) data.setCurrentResolvedModuleRevision(null)
        else data.setCurrentResolvedModuleRevision(currentlyResolved.orNull)
        currentlyResolved = Option(resolver.getDependency(descriptor, data))
        if (currentlyResolved eq previouslyResolved) None
        else if (useLatest) {
          currentlyResolved.map(x =>
            (reparseModuleDescriptor(descriptor, data, resolver, x), resolver))
        } else currentlyResolved.map(x => (forcedRevision(x), resolver))
      }

      def reportError(throwable: Throwable, resolver: DependencyResolver): Unit = {
        val trace = getStackTrace(throwable)
        Message.verbose(s"problem occurred while resolving $descriptor with $resolver: $trace")
      }

      resolvers.map { (resolver: DependencyResolver) =>
        // Return none when revision is cached and `isReturnFirst` is set
        if (isReturnFirst && currentlyResolved.isDefined && !useLatest) Right(None)
        else {
          // We actually do resolution.
          val oldLatest: Option[LatestStrategy] =
            setLatestIfRequired(resolver, Option(getLatestStrategy))
          try Right(performResolution(resolver))
          catch { case NonFatal(t) => reportError(t, resolver); Left(t) } finally {
            oldLatest.foreach(_ => doSetLatestStrategy(resolver, oldLatest))
            checkInterrupted()
          }
        }
      }
    }

    private final val prefix = "Undefined resolution order"
    def resolveLatest(foundRevisions: Seq[(ResolvedModuleRevision, DependencyResolver)],
                      descriptor: DependencyDescriptor,
                      data: ResolveData): Option[ResolvedModuleRevision] = {

      val sortedRevisions = foundRevisions.sortBy {
        case (rmr, resolver) =>
          val publicationDate = rmr.getPublicationDate
          val descriptorDate = rmr.getDescriptor.getPublicationDate
          Message.warn(s"Sorting results from $rmr, using $publicationDate and $descriptorDate.")
          // Just issue warning about issues with publication date, and fake one on it for now
          val chosenPublicationDate = Option(publicationDate).orElse(Option(descriptorDate))
          chosenPublicationDate match {
            case Some(date) => date.getTime
            case None =>
              val id = rmr.getId
              val resolvedResource = (resolver.findIvyFileRef(descriptor, data), rmr.getDescriptor)
              resolvedResource match {
                case (res: ResolvedResource, dmd: DefaultModuleDescriptor) =>
                  val resolvedPublicationDate = new java.util.Date(res.getLastModified)
                  Message.debug(s"No publication date from resolver $resolver for $id.")
                  Message.debug(s"Setting publication date to: $resolvedPublicationDate.")
                  dmd.setPublicationDate(resolvedPublicationDate)
                  res.getLastModified
                case (ivf, dmd) =>
                  // The dependency is specified by a direct URL or some sort of non-ivy file
                  if (ivf == null && descriptor.isChanging)
                    Message.warn(s"$prefix: changing dependency $id with no ivy/pom file!")
                  if (dmd == null)
                    Message.warn(s"$prefix: no publication date from resolver $resolver for $id")
                  0L
              }
          }
      }

      val firstHit = sortedRevisions.reverse.headOption
      firstHit.map { hit =>
        val (resolvedModule, resolver) = hit
        Message.warn(s"Choosing $resolver for ${resolvedModule.getId}")
        // Now that we know the real latest revision, let's force Ivy to use it
        val resolvedDescriptor = resolvedModule.getDescriptor
        val artifactOpt = findFirstArtifactRef(resolvedDescriptor, descriptor, data, resolver)
        // If `None` do nothing -- modules without artifacts. Otherwise cache.
        artifactOpt.foreach { artifactRef =>
          val dep = toSystem(descriptor)
          val first = toSystem(resolvedDescriptor).getAllArtifacts.head
          val options = getCacheOptions(data)
          val cacheManager = getRepositoryCacheManager
          cacheManager.cacheModuleDescriptor(resolver, artifactRef, dep, first, null, options)
        }
        resolvedModule
      }
    }

    def resolveByAllMeans(
        cachedModule: Option[ResolvedModuleRevision],
        useLatest: Boolean,
        interResolver: Option[DependencyResolver],
        resolveModules: () => Seq[Either[Throwable, TriedResolution]],
        dd: DependencyDescriptor,
        data: ResolveData
    ): Option[ResolvedModuleRevision] = {
      val internallyResolved: Option[ResolvedModuleRevision] = {
        if (!updateOptions.interProjectFirst) None
        else interResolver.flatMap(resolver => Option(resolver.getDependency(dd, data)))
      }
      val internalOrExternal = internallyResolved.orElse {
        val foundRevisions: Seq[(ResolvedModuleRevision, DependencyResolver)] =
          resolveModules().collect { case Right(Some(x)) => x }
        if (useLatest) resolveLatest(foundRevisions, dd, data)
        else foundRevisions.reverse.headOption.map(_._1) // Resolvers are hit in reverse order
      }
      internalOrExternal.orElse(cachedModule)
    }

    // The ivy implementation guarantees that all resolvers implement `DependencyResolver`
    def getDependencyResolvers: Vector[DependencyResolver] =
      getResolvers.toArray.collect { case r: DependencyResolver => r }.toVector

    def findInterProjectResolver(resolvers: Seq[DependencyResolver]): Option[DependencyResolver] =
      resolvers.find(_.getName == ProjectResolver.InterProject)

    /** Gets the dependency for a given descriptor with the pertinent resolve data.
     *
     * This is a custom sbt chain operation that produces better error output and deals with
     * cases that the conventional ivy resolver does not. It accumulates the resolution of
     * several resolvers and returns the module which fits the provided resolution strategy.
     */
    def getDependency(dd: DependencyDescriptor, data0: ResolveData): ResolvedModuleRevision = {
      val isDynamic = dd.isChanging || IvySbt.isChanging(dd.getDependencyRevisionId)
      val useLatest = isDynamic && updateOptions.latestSnapshots
      if (useLatest) Message.verbose(s"$getName is changing. Checking all resolvers on the chain.")

      /* Get the resolved module descriptor from:
       *   1. An already resolved branch of the resolution tree.
       *   2. The value from the cache. */
      val data = new ResolveData(data0, doValidate(data0))
      val resolved0 = Option(data.getCurrentResolvedModuleRevision)
      val resolvedOrCached = getCached(dd, data0, resolved0)

      val cached: Option[ResolvedModuleRevision] = if (useLatest) None else resolvedOrCached
      val resolvers = getDependencyResolvers
      val interResolver = findInterProjectResolver(resolvers)
      // TODO: Please, change `Option` return types so that this goes away
      lazy val results = getResults(cached, useLatest, data, dd)
      lazy val errors = results.collect { case Left(t) => t }
      val runResolution = () => results
      val resolved = resolveByAllMeans(cached, useLatest, interResolver, runResolution, dd, data)

      resolved match {
        case None if errors.size == 1 =>
          errors.head match {
            case e: RuntimeException => throw e
            case e: ParseException   => throw e
            case e: Throwable        => throw new RuntimeException(e.toString, e)
          }
        case None if errors.size > 1 =>
          val traces = errors.toList.map(e => IvyStringUtils.getErrorMessage(e))
          val msg = s"Resolution failed several times for $dd:"
          throw new RuntimeException(s"$msg: ${traces.mkString("\n\t", "\n\t", "\n")}")
        case _ =>
          // Can be either `None` with empty error or `Some`
          if (resolved0 == resolved) resolved0.orNull
          else resolved.map(resolvedRevision).orNull
      }
    }
  }

  // Ivy seem to not want to use the module descriptor found at the latest resolver
  private[this] def reparseModuleDescriptor(
      dd: DependencyDescriptor,
      data: ResolveData,
      resolver: DependencyResolver,
      rmr: ResolvedModuleRevision
  ): ResolvedModuleRevision =
    // TODO - Redownloading/parsing the ivy file is not really the best way to make this correct.
    //        We should figure out a better alternative, or directly attack the resolvers Ivy uses to
    //        give them correct behavior around -SNAPSHOT.
    Option(resolver.findIvyFileRef(dd, data)) flatMap { ivyFile =>
      ivyFile.getResource match {
        case r: FileResource =>
          try {
            val parser = rmr.getDescriptor.getParser
            val md = parser.parseDescriptor(settings, r.getFile.toURI.toURL, r, false)
            Some(new ResolvedModuleRevision(resolver, resolver, md, rmr.getReport, true))
          } catch {
            case _: ParseException => None
          }
        case _ => None
      }
    } getOrElse {
      Message.warn(
        s"Unable to reparse ${dd.getDependencyRevisionId} from $resolver, using ${rmr.getPublicationDate}"
      )
      rmr
    }

  /** Ported from BasicResolver#findFirstAirfactRef. */
  private[this] def findFirstArtifactRef(
      md: ModuleDescriptor,
      dd: DependencyDescriptor,
      data: ResolveData,
      resolver: DependencyResolver
  ): Option[ResolvedResource] = {
    def artifactRef(artifact: IArtifact, date: Date): Option[ResolvedResource] =
      resolver match {
        case resolver: BasicResolver =>
          IvyContext.getContext.set(resolver.getName + ".artifact", artifact)
          try {
            Option(resolver.doFindArtifactRef(artifact, date)) orElse {
              Option(artifact.getUrl) map { url =>
                Message.verbose("\tusing url for " + artifact + ": " + url)
                val resource =
                  if ("file" == url.getProtocol)
                    new FileResource(new IFileRepository(), new File(url.getPath))
                  else new URLResource(url)
                new ResolvedResource(resource, artifact.getModuleRevisionId.getRevision)
              }
            }
          } finally {
            IvyContext.getContext.set(resolver.getName + ".artifact", null)
          }
        case _ =>
          None
      }
    val artifactRefs = md.getConfigurations.toIterator flatMap { conf =>
      md.getArtifacts(conf.getName).toIterator flatMap { af =>
        artifactRef(af, data.getDate).toIterator
      }
    }
    if (artifactRefs.hasNext) Some(artifactRefs.next())
    else None
  }

  /** Ported from ChainResolver#forcedRevision. */
  private[this] def forcedRevision(rmr: ResolvedModuleRevision): ResolvedModuleRevision =
    new ResolvedModuleRevision(
      rmr.getResolver,
      rmr.getArtifactResolver,
      rmr.getDescriptor,
      rmr.getReport,
      true
    )

  /** Ported from ChainResolver#resolvedRevision. */
  private[this] def resolvedRevision(rmr: ResolvedModuleRevision): ResolvedModuleRevision =
    if (isDual)
      new ResolvedModuleRevision(
        rmr.getResolver,
        this,
        rmr.getDescriptor,
        rmr.getReport,
        rmr.isForce
      )
    else rmr

  /** Ported from ChainResolver#setLatestIfRequired. */
  private[this] def setLatestIfRequired(
      resolver: DependencyResolver,
      latest: Option[LatestStrategy]
  ): Option[LatestStrategy] =
    latestStrategyName(resolver) match {
      case Some(latestName) if latestName != "default" =>
        val oldLatest = latestStrategy(resolver)
        doSetLatestStrategy(resolver, latest)
        oldLatest
      case _ => None
    }

  /** Ported from ChainResolver#getLatestStrategyName. */
  private[this] def latestStrategyName(resolver: DependencyResolver): Option[String] =
    resolver match {
      case r: HasLatestStrategy => Some(r.getLatest)
      case _                    => None
    }

  /** Ported from ChainResolver#getLatest. */
  private[this] def latestStrategy(resolver: DependencyResolver): Option[LatestStrategy] =
    resolver match {
      case r: HasLatestStrategy => Some(r.getLatestStrategy)
      case _                    => None
    }

  /** Ported from ChainResolver#setLatest. */
  private[this] def doSetLatestStrategy(
      resolver: DependencyResolver,
      latest: Option[LatestStrategy]
  ): Option[LatestStrategy] =
    resolver match {
      case r: HasLatestStrategy =>
        val oldLatest = latestStrategy(resolver)
        r.setLatestStrategy(latest.orNull)
        oldLatest
      case _ => None
    }
}
