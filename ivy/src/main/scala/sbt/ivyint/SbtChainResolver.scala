package sbt
package ivyint

import java.io.File
import java.text.ParseException
import java.util.Date

import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.{ IvyContext, LogOptions }
import org.apache.ivy.core.module.descriptor.{ ModuleDescriptor, DependencyDescriptor, Artifact => IArtifact }
import org.apache.ivy.core.resolve.{ ResolvedModuleRevision, ResolveData }
import org.apache.ivy.plugins.latest.LatestStrategy
import org.apache.ivy.plugins.repository.file.{ FileRepository => IFileRepository, FileResource }
import org.apache.ivy.plugins.repository.url.URLResource
import org.apache.ivy.plugins.resolver.{ ChainResolver, BasicResolver, DependencyResolver }
import org.apache.ivy.plugins.resolver.util.{ HasLatestStrategy, ResolvedResource }
import org.apache.ivy.util.{ Message, MessageLogger, StringUtils => IvyStringUtils }

class SbtChainResolver(name: String, resolvers: Seq[DependencyResolver], settings: IvySettings, updateOptions: UpdateOptions, log: Logger) extends ChainResolver {
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
  override def locate(artifact: IArtifact) = if (IvySbt.hasImplicitClassifier(artifact)) null else super.locate(artifact)

  override def getDependency(dd: DependencyDescriptor, data: ResolveData) =
    {
      if (data.getOptions.getLog != LogOptions.LOG_QUIET)
        Message.info("Resolving " + dd.getDependencyRevisionId + " ...")
      val gd = doGetDependency(dd, data)
      val mod = IvySbt.resetArtifactResolver(gd)
      mod
    }
  // Modified implementation of ChainResolver#getDependency.
  // When the dependency is changing, it will check all resolvers on the chain
  // regardless of what the "latest strategy" is set, and look for the published date
  // or the module descriptor to sort them.
  // This implementation also skips resolution if "return first" is set to true,
  // and if a previously resolved or cached revision has been found.
  def doGetDependency(dd: DependencyDescriptor, data0: ResolveData): ResolvedModuleRevision =
    {
      val useLatest = (dd.isChanging || (IvySbt.isChanging(dd.getDependencyRevisionId))) && updateOptions.latestSnapshots
      if (useLatest) {
        Message.verbose(s"${getName} is changing. Checking all resolvers on the chain")
      }
      val data = new ResolveData(data0, doValidate(data0))
      val resolved = Option(data.getCurrentResolvedModuleRevision)
      val resolvedOrCached =
        resolved orElse {
          Message.verbose(getName + ": Checking cache for: " + dd)
          Option(findModuleInCache(dd, data, true)) map { mr =>
            Message.verbose(getName + ": module revision found in cache: " + mr.getId)
            forcedRevision(mr)
          }
        }
      var temp: Option[ResolvedModuleRevision] =
        if (useLatest) None
        else resolvedOrCached
      val resolvers = getResolvers.toArray.toVector collect { case x: DependencyResolver => x }
      val results = resolvers map { x =>
        // if the revision is cached and isReturnFirst is set, don't bother hitting any resolvers
        if (isReturnFirst && temp.isDefined && !useLatest) Right(None)
        else {
          val resolver = x
          val oldLatest: Option[LatestStrategy] = setLatestIfRequired(resolver, Option(getLatestStrategy))
          try {
            val previouslyResolved = temp
            // if the module qualifies as changing, then resolve all resolvers
            if (useLatest) data.setCurrentResolvedModuleRevision(None.orNull)
            else data.setCurrentResolvedModuleRevision(temp.orNull)
            temp = Option(resolver.getDependency(dd, data))
            val retval = Right(
              if (temp eq previouslyResolved) None
              else if (useLatest) temp map { x =>
                (reparseModuleDescriptor(dd, data, resolver, x), resolver)
              }
              else temp map { x => (forcedRevision(x), resolver) }
            )
            retval
          } catch {
            case ex: Exception =>
              Message.verbose("problem occurred while resolving " + dd + " with " + resolver
                + ": " + IvyStringUtils.getStackTrace(ex))
              Left(ex)
          } finally {
            oldLatest map { _ => doSetLatestStrategy(resolver, oldLatest) }
            checkInterrupted
          }
        }
      }
      val errors = results collect { case Left(e) => e }
      val foundRevisions: Vector[(ResolvedModuleRevision, DependencyResolver)] = results collect { case Right(Some(x)) => x }
      val sorted =
        if (useLatest) (foundRevisions.sortBy {
          case (rmr, _) =>
            rmr.getDescriptor.getPublicationDate.getTime
        }).reverse.headOption map {
          case (rmr, resolver) =>
            // Now that we know the real latest revision, let's force Ivy to use it
            val artifactOpt = findFirstArtifactRef(rmr.getDescriptor, dd, data, resolver)
            artifactOpt match {
              case None if resolver.getName == "inter-project" => // do nothing
              case None => throw new RuntimeException("\t" + resolver.getName
                + ": no ivy file nor artifact found for " + rmr)
              case Some(artifactRef) =>
                val systemMd = toSystem(rmr.getDescriptor)
                getRepositoryCacheManager.cacheModuleDescriptor(resolver, artifactRef,
                  toSystem(dd), systemMd.getAllArtifacts().head, None.orNull, getCacheOptions(data))
            }
            rmr
        }
        else foundRevisions.reverse.headOption map { _._1 }
      val mrOpt: Option[ResolvedModuleRevision] = sorted orElse resolvedOrCached
      mrOpt match {
        case None if errors.size == 1 =>
          errors.head match {
            case e: RuntimeException => throw e
            case e: ParseException   => throw e
            case e: Throwable        => throw new RuntimeException(e.toString, e)
          }
        case None if errors.size > 1 =>
          val err = (errors.toList map { IvyStringUtils.getErrorMessage }).mkString("\n\t", "\n\t", "\n")
          throw new RuntimeException(s"several problems occurred while resolving $dd:$err")
        case _ =>
          if (resolved == mrOpt) resolved.orNull
          else (mrOpt map { resolvedRevision }).orNull
      }
    }
  // Ivy seem to not want to use the module descriptor found at the latest resolver
  private[this] def reparseModuleDescriptor(dd: DependencyDescriptor, data: ResolveData, resolver: DependencyResolver, rmr: ResolvedModuleRevision): ResolvedModuleRevision =
    Option(resolver.findIvyFileRef(dd, data)) flatMap { ivyFile =>
      ivyFile.getResource match {
        case r: FileResource =>
          try {
            val parser = rmr.getDescriptor.getParser
            val md = parser.parseDescriptor(settings, r.getFile.toURL, r, false)
            Some(new ResolvedModuleRevision(resolver, resolver, md, rmr.getReport, true))
          } catch {
            case _: ParseException => None
          }
        case _ => None
      }
    } getOrElse rmr
  /** Ported from BasicResolver#findFirstAirfactRef. */
  private[this] def findFirstArtifactRef(md: ModuleDescriptor, dd: DependencyDescriptor, data: ResolveData, resolver: DependencyResolver): Option[ResolvedResource] =
    {
      def artifactRef(artifact: IArtifact, date: Date): Option[ResolvedResource] =
        resolver match {
          case resolver: BasicResolver =>
            IvyContext.getContext.set(resolver.getName + ".artifact", artifact)
            try {
              Option(resolver.doFindArtifactRef(artifact, date)) orElse {
                Option(artifact.getUrl) map { url =>
                  Message.verbose("\tusing url for " + artifact + ": " + url)
                  val resource =
                    if ("file" == url.getProtocol) new FileResource(new IFileRepository(), new File(url.getPath()))
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
      if (artifactRefs.hasNext) Some(artifactRefs.next)
      else None
    }
  /** Ported from ChainResolver#forcedRevision. */
  private[this] def forcedRevision(rmr: ResolvedModuleRevision): ResolvedModuleRevision =
    new ResolvedModuleRevision(rmr.getResolver, rmr.getArtifactResolver, rmr.getDescriptor, rmr.getReport, true)
  /** Ported from ChainResolver#resolvedRevision. */
  private[this] def resolvedRevision(rmr: ResolvedModuleRevision): ResolvedModuleRevision =
    if (isDual) new ResolvedModuleRevision(rmr.getResolver, this, rmr.getDescriptor, rmr.getReport, rmr.isForce)
    else rmr
  /** Ported from ChainResolver#setLatestIfRequired. */
  private[this] def setLatestIfRequired(resolver: DependencyResolver, latest: Option[LatestStrategy]): Option[LatestStrategy] =
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
  private[this] def doSetLatestStrategy(resolver: DependencyResolver, latest: Option[LatestStrategy]): Option[LatestStrategy] =
    resolver match {
      case r: HasLatestStrategy =>
        val oldLatest = latestStrategy(resolver)
        r.setLatestStrategy(latest.orNull)
        oldLatest
      case _ => None
    }
}