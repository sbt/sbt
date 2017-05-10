/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt.internal.librarymanagement

import java.net.URL
import java.util.Collections
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.resolve.ResolveData
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.repository.{ RepositoryCopyProgressListener, TransferEvent }
import org.apache.ivy.plugins.resolver.{
  BasicResolver,
  DependencyResolver,
  IBiblioResolver,
  RepositoryResolver
}
import org.apache.ivy.plugins.resolver.{
  AbstractPatternsBasedResolver,
  AbstractSshBasedResolver,
  FileSystemResolver,
  SFTPResolver,
  SshResolver,
  URLResolver
}
import org.apache.ivy.plugins.repository.url.{ URLRepository => URLRepo }
import org.apache.ivy.plugins.repository.file.{ FileRepository => FileRepo, FileResource }
import java.io.{ IOException, File }
import org.apache.ivy.util.{ FileUtil, ChecksumHelper }
import org.apache.ivy.core.module.descriptor.{ Artifact => IArtifact }
import sbt.io.IO
import sbt.util.Logger
import sbt.librarymanagement._

private[sbt] object ConvertResolver {
  import UpdateOptions.ResolverConverter

  /**
   * This class contains all the reflective lookups used in the
   * checksum-friendly URL publishing shim.
   */
  private object ChecksumFriendlyURLResolver {
    // TODO - When we dump JDK6 support we can remove this hackery
    // import java.lang.reflect.AccessibleObject
    type AccessibleObject = {
      def setAccessible(value: Boolean): Unit
    }
    private def reflectiveLookup[A <: AccessibleObject](f: Class[_] => A): Option[A] =
      try {
        val cls = classOf[RepositoryResolver]
        val thing = f(cls)
        import scala.language.reflectiveCalls
        thing.setAccessible(true)
        Some(thing)
      } catch {
        case (_: java.lang.NoSuchFieldException) | (_: java.lang.SecurityException) |
            (_: java.lang.NoSuchMethodException) =>
          None
      }
    private val signerNameField: Option[java.lang.reflect.Field] =
      reflectiveLookup(_.getDeclaredField("signerName"))
    private val putChecksumMethod: Option[java.lang.reflect.Method] =
      reflectiveLookup(
        _.getDeclaredMethod(
          "putChecksum",
          classOf[IArtifact],
          classOf[File],
          classOf[String],
          classOf[Boolean],
          classOf[String]
        )
      )
    private val putSignatureMethod: Option[java.lang.reflect.Method] =
      reflectiveLookup(
        _.getDeclaredMethod(
          "putSignature",
          classOf[IArtifact],
          classOf[File],
          classOf[String],
          classOf[Boolean]
        )
      )
  }

  /**
   * The default behavior of ivy's overwrite flags ignores the fact that a lot of repositories
   * will autogenerate checksums *for* an artifact if it doesn't already exist.  Therefore
   * if we succeed in publishing an artifact, we need to just blast the checksums in place.
   * This acts as a "shim" on RepositoryResolvers so that we can hook our methods into
   * both the IBiblioResolver + URLResolver without having to duplicate the code in two
   * places.   However, this does mean our use of reflection is awesome.
   *
   * TODO - See about contributing back to ivy.
   */
  private trait ChecksumFriendlyURLResolver extends RepositoryResolver {
    import ChecksumFriendlyURLResolver._
    private def signerName: String = signerNameField match {
      case Some(field) => field.get(this).asInstanceOf[String]
      case None        => null
    }
    override protected def put(
        artifact: IArtifact,
        src: File,
        dest: String,
        overwrite: Boolean
    ): Unit = {
      // verify the checksum algorithms before uploading artifacts!
      val checksums = getChecksumAlgorithms()
      val repository = getRepository()
      for {
        checksum <- checksums
        if !ChecksumHelper.isKnownAlgorithm(checksum)
      } throw new IllegalArgumentException("Unknown checksum algorithm: " + checksum)
      repository.put(artifact, src, dest, overwrite);
      // Fix for sbt#1156 - Artifactory will auto-generate MD5/sha1 files, so
      // we need to overwrite what it has.
      for (checksum <- checksums) {
        putChecksumMethod match {
          case Some(method) =>
            method.invoke(this, artifact, src, dest, true: java.lang.Boolean, checksum)
          case None => // TODO - issue warning?
        }
      }
      if (signerName != null) {
        putSignatureMethod match {
          case None         => ()
          case Some(method) => method.invoke(artifact, src, dest, true: java.lang.Boolean); ()
        }
      }
    }
  }

  /** Converts the given sbt resolver into an Ivy resolver. */
  @deprecated("Use the variant with updateOptions", "0.13.8")
  def apply(r: Resolver, settings: IvySettings, log: Logger): DependencyResolver =
    apply(r, settings, UpdateOptions(), log)

  /** Converts the given sbt resolver into an Ivy resolver. */
  def apply(
      r: Resolver,
      settings: IvySettings,
      updateOptions: UpdateOptions,
      log: Logger
  ): DependencyResolver =
    (updateOptions.resolverConverter orElse defaultConvert)((r, settings, log))

  /** The default implementation of converter. */
  lazy val defaultConvert: ResolverConverter = {
    case (r, settings, log) =>
      r match {
        case repo: MavenRepository => {
          val pattern = Collections.singletonList(
            Resolver.resolvePattern(repo.root, Resolver.mavenStyleBasePattern)
          )
          final class PluginCapableResolver
              extends IBiblioResolver
              with ChecksumFriendlyURLResolver
              with DescriptorRequired {
            def setPatterns(): Unit = {
              // done this way for access to protected methods.
              setArtifactPatterns(pattern)
              setIvyPatterns(pattern)
            }
          }
          val resolver = new PluginCapableResolver
          if (repo.localIfFile) resolver.setRepository(new LocalIfFileRepo)
          initializeMavenStyle(resolver, repo.name, repo.root)
          resolver
            .setPatterns() // has to be done after initializeMavenStyle, which calls methods that overwrite the patterns
          resolver
        }
        case repo: SshRepository => {
          val resolver = new SshResolver with DescriptorRequired
          initializeSSHResolver(resolver, repo, settings)
          repo.publishPermissions.foreach(perm => resolver.setPublishPermissions(perm))
          resolver
        }
        case repo: SftpRepository => {
          val resolver = new SFTPResolver
          initializeSSHResolver(resolver, repo, settings)
          resolver
        }
        case repo: FileRepository => {
          val resolver = new FileSystemResolver with DescriptorRequired {
            // Workaround for #1156
            // Temporarily in sbt 0.13.x we deprecate overwriting
            // in local files for non-changing revisions.
            // This will be fully enforced in sbt 1.0.
            setRepository(new WarnOnOverwriteFileRepo())
          }
          resolver.setName(repo.name)
          initializePatterns(resolver, repo.patterns, settings)
          import repo.configuration.{ isLocal, isTransactional }
          resolver.setLocal(isLocal)
          isTransactional.foreach(value => resolver.setTransactional(value.toString))
          resolver
        }
        case repo: URLRepository => {
          val resolver = new URLResolver with ChecksumFriendlyURLResolver with DescriptorRequired
          resolver.setName(repo.name)
          initializePatterns(resolver, repo.patterns, settings)
          resolver
        }
        case repo: ChainedResolver =>
          IvySbt.resolverChain(repo.name, repo.resolvers, settings, log)
        case repo: RawRepository => repo.resolver
      }
  }

  private sealed trait DescriptorRequired extends BasicResolver {
    override def getDependency(dd: DependencyDescriptor, data: ResolveData) = {
      val prev = descriptorString(isAllownomd)
      setDescriptor(descriptorString(hasExplicitURL(dd)))
      try super.getDependency(dd, data)
      finally setDescriptor(prev)
    }
    def descriptorString(optional: Boolean) =
      if (optional) BasicResolver.DESCRIPTOR_OPTIONAL else BasicResolver.DESCRIPTOR_REQUIRED
    def hasExplicitURL(dd: DependencyDescriptor): Boolean =
      dd.getAllDependencyArtifacts.exists(_.getUrl != null)
  }
  private def initializeMavenStyle(resolver: IBiblioResolver, name: String, root: String): Unit = {
    resolver.setName(name)
    resolver.setM2compatible(true)
    resolver.setRoot(root)
  }
  private def initializeSSHResolver(
      resolver: AbstractSshBasedResolver,
      repo: SshBasedRepository,
      settings: IvySettings
  ): Unit = {
    resolver.setName(repo.name)
    resolver.setPassfile(null)
    initializePatterns(resolver, repo.patterns, settings)
    initializeConnection(resolver, repo.connection)
  }
  private def initializeConnection(
      resolver: AbstractSshBasedResolver,
      connection: SshConnection
  ): Unit = {
    import resolver._
    import connection._
    hostname.foreach(setHost)
    port.foreach(setPort)
    authentication foreach {
      case pa: PasswordAuthentication =>
        setUser(pa.user)
        pa.password.foreach(setUserPassword)
      case kfa: KeyFileAuthentication =>
        setKeyFile(kfa.keyfile)
        kfa.password.foreach(setKeyFilePassword)
        setUser(kfa.user)
    }
  }
  private def initializePatterns(
      resolver: AbstractPatternsBasedResolver,
      patterns: Patterns,
      settings: IvySettings
  ): Unit = {
    resolver.setM2compatible(patterns.isMavenCompatible)
    resolver.setDescriptor(
      if (patterns.descriptorOptional) BasicResolver.DESCRIPTOR_OPTIONAL
      else BasicResolver.DESCRIPTOR_REQUIRED
    )
    resolver.setCheckconsistency(!patterns.skipConsistencyCheck)
    patterns.ivyPatterns.foreach(p => resolver.addIvyPattern(settings substitute p))
    patterns.artifactPatterns.foreach(p => resolver.addArtifactPattern(settings substitute p))
  }

  /**
   * A custom Ivy URLRepository that returns FileResources for file URLs.
   * This allows using the artifacts from the Maven local repository instead of copying them to the Ivy cache.
   */
  private[this] final class LocalIfFileRepo extends URLRepo {
    private[this] val repo = new WarnOnOverwriteFileRepo()
    private[this] val progress = new RepositoryCopyProgressListener(this);
    override def getResource(source: String) = {
      val url = new URL(source)
      if (url.getProtocol == IO.FileScheme)
        new FileResource(repo, IO.toFile(url))
      else
        super.getResource(source)
    }

    override def put(source: File, destination: String, overwrite: Boolean): Unit = {
      val url = new URL(destination)
      if (url.getProtocol != IO.FileScheme) super.put(source, destination, overwrite)
      else {
        // Here we duplicate the put method for files so we don't just bail on trying ot use Http handler
        val resource = getResource(destination)
        if (!overwrite && resource.exists()) {
          throw new IOException("destination file exists and overwrite == false");
        }
        fireTransferInitiated(resource, TransferEvent.REQUEST_PUT);
        try {
          val totalLength = source.length
          if (totalLength > 0) {
            progress.setTotalLength(totalLength);
          }
          FileUtil.copy(source, new java.io.File(url.toURI), progress, overwrite)
          ()
        } catch {
          case ex: IOException =>
            fireTransferError(ex)
            throw ex
          case ex: RuntimeException =>
            fireTransferError(ex)
            throw ex
        } finally {
          progress.setTotalLength(null);
        }
      }
    }
  }

  private[this] final class WarnOnOverwriteFileRepo extends FileRepo() {
    override def put(source: java.io.File, destination: String, overwrite: Boolean): Unit = {
      try super.put(source, destination, overwrite)
      catch {
        case e: java.io.IOException if e.getMessage.contains("destination already exists") =>
          import org.apache.ivy.util.Message
          Message.warn(
            s"Attempting to overwrite $destination\n\tThis usage is deprecated and will be removed in sbt 1.0."
          )
          super.put(source, destination, true)
      }
    }
  }
}
