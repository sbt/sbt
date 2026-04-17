/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.{ File, IOException }
import java.net.{ URI, URL }
import java.util.concurrent.Callable

import gigahorse.{ AuthScheme }
import gigahorse.support.apachehttp.Gigahorse
import sbt.Def.ScopedKey
import sbt.internal.librarymanagement.*
import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import sbt.util.{ CacheStore, CacheStoreFactory, Level, Logger, Tracked }
import sbt.io.IO
import sbt.io.syntax.*
import sbt.ProjectExtra.*
import sjsonnew.JsonFormat
import scala.concurrent.*
import scala.concurrent.duration.*
import lmcoursier.definitions.Project as CsrProject

private[sbt] object LibraryManagement {
  given linter: sbt.dsl.LinterLevel.Ignore.type = sbt.dsl.LinterLevel.Ignore

  // The fourth element is transitive dependency stamps for cross-command cache invalidation
  private type UpdateInputs = (Long, ModuleSettings, UpdateConfiguration, Vector[String])

  def cachedUpdate(
      lm: DependencyResolution,
      module: ModuleDescriptor,
      cacheStoreFactory: CacheStoreFactory,
      label: String,
      updateConfig: UpdateConfiguration,
      transform: UpdateReport => UpdateReport,
      skip: Boolean,
      force: Boolean,
      transitiveUpdates: Seq[UpdateReport],
      uwConfig: UnresolvedWarningConfiguration,
      evictionLevel: Level.Value,
      evictionWarningOptions: EvictionWarningOptions,
      versionSchemeOverrides: Seq[ModuleID],
      assumedEvictionErrorLevel: Level.Value,
      assumedVersionScheme: String,
      assumedVersionSchemeJava: String,
      mavenStyle: Boolean,
      compatWarning: CompatibilityWarningOptions,
      includeCallers: Boolean,
      includeDetails: Boolean,
      log: Logger
  ): UpdateReport = {

    /* Resolve the module settings from the inputs. */
    def resolve: UpdateReport = {
      import sbt.util.ShowLines.*

      log.debug(s"Updating $label...")
      val reportOrUnresolved: Either[UnresolvedWarning, UpdateReport] =
        lm.update(module, updateConfig, uwConfig, log)
      val report = reportOrUnresolved match {
        case Right(report0) => report0
        case Left(unresolvedWarning) =>
          unresolvedWarning.lines.foreach(log.warn(_))
          throw unresolvedWarning.resolveException
      }
      log.debug(s"Done updating $label")
      val report1 = transform(report)

      // Warn of any eviction and compatibility warnings
      val evictionError = EvictionError(
        report1,
        module,
        versionSchemeOverrides,
        assumedVersionScheme,
        assumedVersionSchemeJava,
        assumedEvictionErrorLevel,
        evictionWarningOptions.configurations,
      )
      def extraLines = List(
        "",
        "this can be overridden using libraryDependencySchemes or evictionErrorLevel"
      )
      val errorLines: Seq[String] =
        (if (
           evictionError.incompatibleEvictions.isEmpty
           || evictionLevel != Level.Error
         ) Nil
         else evictionError.lines) ++
          (if (
             evictionError.assumedIncompatibleEvictions.isEmpty
             || assumedEvictionErrorLevel != Level.Error
           ) Nil
           else evictionError.toAssumedLines)
      if (errorLines.nonEmpty) sys.error((errorLines ++ extraLines).mkString(System.lineSeparator))
      else {
        if (evictionError.incompatibleEvictions.isEmpty) ()
        else evictionError.lines.foreach(log.log(evictionLevel, _: String))
        if (evictionError.assumedIncompatibleEvictions.isEmpty) ()
        else
          evictionError.toAssumedLines.foreach(log.log(assumedEvictionErrorLevel, _: String))
      }
      CompatibilityWarning.run(compatWarning, module, mavenStyle, log)
      val report2 = transformDetails(report1, includeCallers, includeDetails)
      report2
    }

    /* Check if a update report is still up to date or we must resolve again. */
    def upToDate(inChanged: Boolean, out: UpdateReport): Boolean = {
      // Transitive dependency stamps are now part of UpdateInputs, so inChanged
      // will be true if any transitive stamp changed (cross-command invalidation).
      !force &&
      !inChanged &&
      out.allFiles.forall(f => fileUptodate(f.toString, out.stamps, log)) &&
      fileUptodate(out.cachedDescriptor.toString, out.stamps, log)
    }

    /* Skip resolve if last output exists, otherwise error. */
    def skipResolve(cache: CacheStore)(inputs: UpdateInputs): UpdateReport = {
      UpdateReportPersistence.readFrom(cache) match
        case Some(cached) =>
          markAsCached(UpdateReportPersistence.fromCache(cached))
        case None =>
          sys.error("Skipping update requested, but update has not previously run successfully.")
    }

    // Mark UpdateReport#stats as "cached." This is used by the dependers later
    // to determine whether they now need to run update in the above `upToDate`.
    def markAsCached(ur: UpdateReport): UpdateReport =
      ur.withStats(ur.stats.withCached(true))

    def doResolve(cache: CacheStore): UpdateInputs => UpdateReport = {
      val doCachedResolve = { (inChanged: Boolean, updateInputs: UpdateInputs) =>
        try
          val previous =
            UpdateReportPersistence.readFrom(cache).map(UpdateReportPersistence.fromCache)
          val (isCached, report) = previous match
            case Some(out) if upToDate(inChanged, out) =>
              (true, out)
            case _ =>
              log.debug(s"""not up to date. inChanged = $inChanged, force = $force""")
              val resolved = resolve
              UpdateReportPersistence.writeTo(cache, UpdateReportPersistence.toCache(resolved))
              (false, resolved)
          if isCached then markAsCached(report) else report
        catch
          case r: ResolveException
              if r.failed.exists(isMissingScalaLib) &&
                module.scalaModuleInfo.exists(_.scalaBinaryVersion == "2.13") =>
            informSandwich()
            throw r
          case t: (NullPointerException | OutOfMemoryError) =>
            val resolvedAgain = resolve
            val culprit = t.getClass.getSimpleName
            log.warn(s"Update task caching failed due to $culprit.")
            log.warn("Report the following output to sbt:")
            resolvedAgain.toString.linesIterator.foreach(log.warn(_))
            log.trace(t)
            resolvedAgain
      }
      import LibraryManagementCodec.given
      Tracked.inputChanged(cacheStoreFactory.make("inputs"))(doCachedResolve)
    }

    def informSandwich(): Unit =
      log.warn("[sbt-8728] Smorrebrod - the end of Scala 2.13-3.x sandwich")
      log.warn("")
      log.warn("Scala 3.8+ cannot be used in a Scala 2.13 subproject.")
      log.warn(
        "Dependency resolution failed because scala-reflect or -compiler 3.x does not exist."
      )
      log.warn(
        "This happens when a Scala 2.13 subproject depends on Scala 3.8+ directly or transitively."
      )
      log.warn("To fix this, either")
      log.warn("  - Keep Scala 3 subproject or transitive dependency to 3.7 or below, or")
      log.warn("  - Migrate the Scala 2.13 subproject to Scala 3.x")
      log.warn("See https://github.com/sbt/sbt/discussions/8728")

    def isMissingScalaLib(m: ModuleID): Boolean =
      m.organization == "org.scala-lang" &&
        (m.name == "scala-compiler" || m.name == "scala-reflect") &&
        (m.revision.startsWith("3."))

    // Get the handler to use and feed it in the inputs
    // This is lm-engine specific input hashed into Long
    val extraInputHash = module.extraInputHash
    val settings = module.moduleSettings
    val outStore = cacheStoreFactory.make("output")
    val handler = if (skip && !force) skipResolve(outStore)(_) else doResolve(outStore)
    // Remove clock for caching purpose
    val withoutClock = updateConfig.withLogicalClock(LogicalClock.unknown)
    // Collect transitive stamps for cross-command cache invalidation
    val transitiveStamps = transitiveUpdates.flatMap(_.stats.stamp).toVector
    handler((extraInputHash, settings, withoutClock, transitiveStamps))
  }

  private def fileUptodate(file0: String, stamps: Map[String, Long], log: Logger): Boolean = {
    val file = File(file0)
    val exists = file.exists
    // https://github.com/sbt/sbt/issues/5292 warn the user that the file is missing since this indicates
    // that UpdateReport was persisted but Coursier cache was not.
    if (!exists) {
      log.warn(s"${file.getName} no longer exists at $file")
    }
    // coursier doesn't populate stamps
    val timeStampIsSame = stamps
      .get(file0)
      .forall(_ == IO.getModifiedTimeOrZero(file))
    exists && timeStampIsSame
  }

  private[sbt] def transitiveScratch(
      lm: DependencyResolution,
      label: String,
      config: GetClassifiersConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] = {
    import config.{ updateConfiguration, module }
    import module.{ id, dependencies, scalaModuleInfo }
    val base = restrictedCopy(id, true).withName(id.name + "$" + label)
    val mod = lm.moduleDescriptor(base, dependencies, scalaModuleInfo)
    val report = lm.update(mod, updateConfiguration, uwconfig, log) match {
      case Right(r) => r
      case Left(w) =>
        throw w.resolveException
    }
    val key = (m: ModuleID) => (m.organization, m.name, m.revision)
    val originalKeys = dependencies.map(key).toSet
    val transitiveOnly = report.allModules.filterNot(m => originalKeys contains key(m))
    val mergedDependencies = dependencies ++ transitiveOnly
    val newConfig = config
      .withModule(module.withDependencies(mergedDependencies))
    lm.updateClassifiers(newConfig, uwconfig, Vector(), log)
  }

  private[sbt] def restrictedCopy(m: ModuleID, confs: Boolean) =
    ModuleID(m.organization, m.name, m.revision)
      .withCrossVersion(m.crossVersion)
      .withExtraAttributes(m.extraAttributes)
      .withConfigurations(if (confs) m.configurations else None)
      .branch(m.branchName)

  private def transformDetails(
      ur: UpdateReport,
      includeCallers: Boolean,
      includeDetails: Boolean
  ): UpdateReport = {
    val crs0 = ur.configurations
    val crs1 =
      if (includeDetails) crs0
      else crs0 map { _.withDetails(Vector()) }
    val crs2 =
      if (includeCallers) crs1
      else
        crs1 map { cr =>
          val mrs0 = cr.modules
          val mrs1 = mrs0 map { _.withCallers(Vector()) }
          cr.withModules(mrs1)
        }
    ur.withConfigurations(crs2)
  }

  val moduleIdJsonKeyFormat: sjsonnew.JsonKeyFormat[ModuleID] =
    new sjsonnew.JsonKeyFormat[ModuleID] {
      import LibraryManagementCodec.given
      import sjsonnew.support.scalajson.unsafe.*
      val moduleIdFormat: JsonFormat[ModuleID] = implicitly[JsonFormat[ModuleID]]
      def write(key: ModuleID): String =
        CompactPrinter(Converter.toJsonUnsafe(key)(using moduleIdFormat))
      def read(key: String): ModuleID =
        Converter.fromJsonUnsafe[ModuleID](Parser.parseUnsafe(key))(using moduleIdFormat)
    }

  /**
   * Resolves and optionally retrieves classified artifacts, such as javadocs and sources,
   * for dependency definitions, transitively.
   */
  def updateClassifiersTask: Def.Initialize[Task[UpdateReport]] =
    TupleWrap[
      (
          DependencyResolution,
          State,
          Keys.TaskStreams,
          UpdateConfiguration,
          Option[Level.Value],
          Seq[ScopedKey[?]],
          ScopedKey[?],
          Option[FiniteDuration],
          ModuleSettings,
          String,
          ProjectRef,
          Boolean,
          Seq[UpdateReport],
          UnresolvedWarningConfiguration,
          Boolean,
          CompatibilityWarningOptions,
          GetClassifiersModule,
          File,
          xsbti.AppConfiguration,
          Seq[String],
          Seq[String],
      )
    ](
      Keys.dependencyResolution,
      Keys.state,
      Keys.streams,
      Keys.updateConfiguration.toTaskable,
      (Keys.update / Keys.logLevel).?.toTaskable,
      Keys.executionRoots,
      Keys.resolvedScoped.toTaskable,
      Keys.forceUpdatePeriod.toTaskable,
      Keys.moduleSettings.toTaskable,
      Keys.updateCacheName.toTaskable,
      Keys.thisProjectRef.toTaskable,
      (Keys.update / Keys.skip).toTaskable,
      Keys.transitiveUpdate,
      (Keys.update / Keys.unresolvedWarningConfiguration).toTaskable,
      Keys.publishMavenStyle.toTaskable,
      Keys.compatibilityWarningOptions.toTaskable,
      Keys.classifiersModule,
      Keys.dependencyCacheDirectory,
      Keys.appConfiguration.toTaskable,
      Keys.sourceArtifactTypes.toTaskable,
      Keys.docArtifactTypes.toTaskable,
    ).mapN {
      (
          lm,
          state0,
          s,
          conf,
          maybeUpdateLevel,
          er,
          rs,
          fup,
          ms,
          ucn,
          thisRef,
          sk,
          tu,
          uwConfig,
          mavenStyle,
          cwo,
          mod,
          dcd,
          app,
          srcTypes,
          docTypes,
      ) =>
        import Keys.*
        val cacheDirectory = s.cacheDirectory
        val isRoot = er.contains(rs)
        // following copied from https://github.com/coursier/sbt-coursier/blob/9173406bb399879508aa481fed16efda72f55820/modules/sbt-lm-coursier/src/main/scala/sbt/hack/Foo.scala
        val shouldForce = isRoot || {
          fup match
            case None => false
            case Some(period) =>
              val fullUpdateOutput = cacheDirectory / "output"
              val now = System.currentTimeMillis
              val diff = now - fullUpdateOutput.lastModified()
              val elapsedDuration = new FiniteDuration(
                diff,
                java.util.concurrent.TimeUnit.MILLISECONDS
              )
              fullUpdateOutput.exists() && elapsedDuration > period
        }
        val updateConf = {
          import UpdateLogging.{ Full, DownloadOnly, Default }
          val conf1 = maybeUpdateLevel.orElse(state0.get(logLevel.key)) match
            case Some(Level.Debug) if conf.logging == Default => conf.withLogging(logging = Full)
            case Some(_) if conf.logging == Default => conf.withLogging(logging = DownloadOnly)
            case _                                  => conf
          // logical clock is folded into UpdateConfiguration
          conf1.withLogicalClock(LogicalClock(state0.hashCode))
        }
        cachedUpdate(
          lm = lm,
          module = lm.moduleDescriptor(ms.asInstanceOf[ModuleDescriptorConfiguration]),
          s.cacheStoreFactory.sub(ucn),
          Reference.display(thisRef),
          updateConf,
          identity,
          skip = sk,
          force = shouldForce,
          transitiveUpdates = tu,
          uwConfig = uwConfig,
          evictionLevel = Level.Debug,
          evictionWarningOptions = EvictionWarningOptions.default,
          versionSchemeOverrides = Nil,
          assumedEvictionErrorLevel = Level.Debug,
          assumedVersionScheme = VersionScheme.Always,
          assumedVersionSchemeJava = VersionScheme.Always,
          mavenStyle = mavenStyle,
          compatWarning = cwo,
          includeCallers = false,
          includeDetails = false,
          log = s.log
        )
    }.tag(Tags.Update, Tags.Network)

  // Used by Defaults.withExcludes
  def withExcludes(out: File, classifiers: Seq[String], lock: xsbti.GlobalLock)(
      f: Map[ModuleID, Vector[ConfigRef]] => UpdateReport
  ): UpdateReport = {
    import sbt.librarymanagement.LibraryManagementCodec.given
    import sbt.util.FileBasedStore
    val exclName = "exclude_classifiers"
    val file = out / exclName
    val store = new FileBasedStore(file)
    lock(
      out / (exclName + ".lock"),
      new Callable[UpdateReport] {
        def call = {
          given midJsonKeyFmt: sjsonnew.JsonKeyFormat[ModuleID] = moduleIdJsonKeyFormat
          val excludes =
            store
              .read[Map[ModuleID, Vector[ConfigRef]]](
                default = Map.empty[ModuleID, Vector[ConfigRef]]
              )
          val report = f(excludes)
          val allExcludes: Map[ModuleID, Vector[ConfigRef]] = excludes ++
            UpdateClassifiersUtil
              .extractExcludes(report)
              .view
              .mapValues(cs => cs.map(c => ConfigRef(c)).toVector)
          store.write(allExcludes)
          UpdateClassifiersUtil
            .addExcluded(
              report,
              classifiers.toVector,
              allExcludes.view.mapValues(_.map(_.name).toSet).toMap
            )
        }
      }
    )
  }

  def lock(app: xsbti.AppConfiguration): xsbti.GlobalLock =
    app.provider.scalaProvider.launcher.globalLock

  // Pattern for Scala 3 release candidates: X.Y.Z-RCN (not nightlies)
  private val Scala3RCPattern = """^(\d+)\.(\d+)\.(\d+)-RC(\d+)$""".r

  // Cache for the latest Scala 3 RC version lookup (24-hour TTL)
  private var cachedScala3RC: Option[(String, Long)] = None
  private val scala3RCCacheLock = new Object
  private val cacheTtlMillis = 24L * 60 * 60 * 1000 // 24 hours

  /**
   * Fetches the latest Scala 3 release candidate version from Maven Central.
   * Results are cached for 24 hours.
   *
   * @param log Logger for debug output
   * @return The latest Scala 3 RC version string (e.g., "3.8.1-RC1")
   */
  def fetchLatestScala3RC(log: Logger): String = {
    scala3RCCacheLock.synchronized {
      val now = System.currentTimeMillis()
      cachedScala3RC match {
        case Some((version, timestamp)) if (now - timestamp) < cacheTtlMillis =>
          log.debug(s"Using cached Scala 3 RC version: $version")
          version
        case _ =>
          log.info("Fetching latest Scala 3 release candidate from Maven Central...")
          val version = fetchScala3RCFromMaven(log)
          cachedScala3RC = Some((version, now))
          version
      }
    }
  }

  private def fetchScala3RCFromMaven(log: Logger): String = {
    import scala.util.Using
    import java.net.{ HttpURLConnection, URI }
    import scala.xml.XML

    val metadataUrl =
      "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/maven-metadata.xml"
    val uri = new URI(metadataUrl)
    val conn = uri.toURL.openConnection().asInstanceOf[HttpURLConnection]
    conn.setConnectTimeout(10000)
    conn.setReadTimeout(30000)

    try {
      val xml = Using.resource(conn.getInputStream) { is =>
        XML.load(is)
      }
      val versions = (xml \\ "version").map(_.text)

      // Filter for RC versions (not nightlies) and sort to get the latest
      val rcVersions = versions.flatMap { v =>
        v match {
          case Scala3RCPattern(major, minor, patch, rc) =>
            Some((major.toInt, minor.toInt, patch.toInt, rc.toInt, v))
          case _ => None
        }
      }

      if (rcVersions.isEmpty) {
        sys.error("No Scala 3 release candidates found in Maven Central")
      }

      // Sort by version components (major, minor, patch, rc) descending
      val latestRC =
        rcVersions.minBy { case (maj, min, pat, rc, _) => (-maj, -min, -pat, -rc) }._5
      log.info(s"Latest Scala 3 release candidate: $latestRC")
      latestRC
    } finally {
      conn.disconnect()
    }
  }

  /**
   * Resolves a dynamic Scala version string to a concrete version.
   * Currently supports:
   * - "3-latest.candidate" - resolves to the latest Scala 3 RC
   *
   * @param version The version string (may be dynamic or concrete)
   * @param log Logger for debug output
   * @return The resolved concrete version string
   */
  def resolveDynamicScalaVersion(version: String, log: Logger): String = {
    version match {
      case "3-latest.candidate" => fetchLatestScala3RC(log)
      case other                => other
    }
  }

  /**
   * Resolves a dynamic Scala version string to a concrete version.
   * Uses a silent logger for background resolution.
   */
  def resolveDynamicScalaVersion(version: String): String = {
    resolveDynamicScalaVersion(version, Logger.Null)
  }

  /**
   * Checks if a version string is a dynamic version that needs resolution.
   */
  def isDynamicScalaVersion(version: String): Boolean = {
    version == "3-latest.candidate"
  }

  /**
   * Publishes artifacts to the local Ivy repository without using Apache Ivy.
   * Uses the pattern: [org]/[module]/[revision]/[types]/[artifact](-[classifier]).[ext]
   */
  def ivylessPublishLocal(
      project: CsrProject,
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      localRepoBase: File,
      overwrite: Boolean,
      log: Logger
  ): Unit =
    val org = project.module.organization.value
    val moduleName = project.module.name.value
    val version = project.version

    // Base directory: localRepoBase / org / module / version
    val moduleDir = localRepoBase / org / moduleName / version

    log.info(s"Publishing to $moduleDir")

    // Helper to map artifact type to folder name
    def typeToFolder(tpe: String): String = tpe match
      case "jar"                                   => "jars"
      case "src" | "source" | "sources"            => "srcs"
      case "doc" | "docs" | "javadoc" | "javadocs" => "docs"
      case "pom"                                   => "poms"
      case "ivy"                                   => "ivys"
      case other                                   => other + "s"

    // Helper to write checksums for a file using sbt.util.Digest
    def writeChecksums(file: File): Unit =
      checksumAlgorithms.foreach: algo =>
        val digestAlgo = algo.toLowerCase match
          case "md5"  => sbt.util.Digest.Md5
          case "sha1" => sbt.util.Digest.Sha1
          case other =>
            throw new IllegalArgumentException(s"Unsupported checksum algorithm: $other")
        val digest = sbt.util.Digest(digestAlgo, file.toPath)
        val checksumFile = new File(file.getPath + "." + algo.toLowerCase)
        IO.write(checksumFile, digest.hashHexString)
        log.debug(s"Wrote checksum: $checksumFile")

    // Write ivy.xml first (so ivys/ exists even if artifact copy fails)
    val ivysDir = moduleDir / "ivys"
    val ivyXmlFile = ivysDir / "ivy.xml"
    IO.createDirectory(ivysDir)
    val ivyXmlContent = lmcoursier.IvyXml(project, Nil, Nil)
    if !ivyXmlFile.exists || overwrite then
      IO.write(ivyXmlFile, ivyXmlContent)
      log.info(s"Published $ivyXmlFile")
      writeChecksums(ivyXmlFile)
    else log.warn(s"$ivyXmlFile already exists, skipping (overwrite=$overwrite)")

    // Build a lookup from (type, classifier, ext) to cross-versioned publication name
    val pubNameLookup: Map[(String, String, String), String] =
      project.publications.map { (_, pub) =>
        (pub.`type`.value, pub.classifier.value, pub.ext.value) -> pub.name
      }.toMap

    // Publish each artifact
    artifacts.foreach: (artifact, sourceFile) =>
      val folder = typeToFolder(artifact.`type`)
      val targetDir = moduleDir / folder

      // Look up the cross-versioned artifact name from publications, fall back to module name
      val classifierStr = artifact.classifier.getOrElse("")
      val artName = pubNameLookup
        .getOrElse((artifact.`type`, classifierStr, artifact.extension), moduleName)
      val classifier = artifact.classifier.map("-" + _).getOrElse("")
      val fileName = s"$artName$classifier.${artifact.extension}"
      val targetFile = targetDir / fileName

      if !targetFile.exists || overwrite then
        IO.createDirectory(targetDir)
        IO.copyFile(sourceFile, targetFile)
        log.info(s"Published $targetFile")
        writeChecksums(targetFile)
      else log.warn(s"$targetFile already exists, skipping (overwrite=$overwrite)")
  end ivylessPublishLocal

  /**
   * Substitutes Ivy pattern placeholders for artifact URL.
   * Matches ivylessPublishLocal layout: [organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]
   */
  private def substituteIvyArtifactPattern(
      pattern: String,
      org: String,
      moduleName: String,
      version: String,
      typeFolder: String,
      artifactName: String,
      classifier: String,
      ext: String
  ): String = {
    var s = pattern
    s = s.replace("[organisation]", org)
    s = s.replace("[module]", moduleName)
    s = s.replace("[revision]", version)
    s = s.replace("[type]s", typeFolder)
    s = s.replace("[artifact]", artifactName)
    s = s.replace("[ext]", ext)
    if (classifier.nonEmpty) s = s.replace("(-[classifier])", s"-$classifier")
    else s = s.replace("(-[classifier])", "")
    // Remove optional Ivy pattern parts (scala/sbt version, branch) for ivyless layout
    s = s.replaceAll("\\(scala_[^)]+/\\)", "").replaceAll("\\(sbt_[^)]+/\\)", "")
    s = s.replaceAll("\\(\\[branch\\]/\\)", "")
    s
  }

  /**
   * Picks credentials for a URL. Matches host; when realm is given, prefers credential with matching realm (per Publishing docs).
   */
  private def credentialFor(
      url: URL,
      credentials: Seq[Credentials.DirectCredentials],
      realm: Option[String] = None
  ): Option[Credentials.DirectCredentials] =
    val byHost = credentials.filter(_.host == url.getHost)
    realm match
      case Some(r) => byHost.find(_.realm == r).orElse(byHost.headOption)
      case None    => byHost.headOption

  /**
   * HTTP PUT a file to a URL with optional Basic auth.
   * Uses Gigahorse (Apache HttpClient) per sbt tech stack.
   */
  private def httpPut(
      url: URL,
      sourceFile: File,
      credentials: Option[Credentials.DirectCredentials],
      log: Logger
  ): Unit =
    val baseReq = Gigahorse.url(url.toString).put(sourceFile)
    val req = credentials match
      case Some(dc) => baseReq.withAuth(dc.userName, dc.passwd, AuthScheme.Basic)
      case None     => baseReq
    val f = sbt.librarymanagement.Http.http.processFull(req)
    val response = Await.result(f, 5.minutes)
    val body = response.bodyAsString
    if response.status < 200 || response.status >= 300 then
      throw new IOException(
        s"PUT $url failed: ${response.status} ${response.statusText}$body"
      )
    log.info(s"Published $url")

  /**
   * Publishes artifacts to a remote Ivy repo (URLRepository) without using Apache Ivy.
   * Uses HTTP PUT; supports credentials. Produces the same layout as ivylessPublishLocal.
   */
  def ivylessPublish(
      project: CsrProject,
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      urlRepo: sbt.librarymanagement.URLRepository,
      credentials: Seq[Credentials],
      overwrite: Boolean,
      log: Logger
  ): Unit = {
    val org = project.module.organization.value
    val moduleName = project.module.name.value
    val version = project.version
    val artifactPattern = urlRepo.patterns.artifactPatterns.headOption.getOrElse(
      sys.error("URLRepository has no artifact pattern")
    )
    val ivyPattern = urlRepo.patterns.ivyPatterns.headOption.getOrElse(
      sys.error("URLRepository has no ivy pattern")
    )
    val directCreds = credentials.collect { case d: Credentials.DirectCredentials => d }

    def typeToFolder(tpe: String): String = tpe match
      case "jar"                                   => "jars"
      case "src" | "source" | "sources"            => "srcs"
      case "doc" | "docs" | "javadoc" | "javadocs" => "docs"
      case "pom"                                   => "poms"
      case "ivy"                                   => "ivys"
      case other                                   => other + "s"

    def writeChecksums(file: File): Vector[(File, String)] =
      checksumAlgorithms.map { algo =>
        val digestAlgo = algo.toLowerCase match
          case "md5"  => sbt.util.Digest.Md5
          case "sha1" => sbt.util.Digest.Sha1
          case other =>
            throw new IllegalArgumentException(s"Unsupported checksum algorithm: $other")
        val digest = sbt.util.Digest(digestAlgo, file.toPath)
        val content = digest.hashHexString
        val suffix = "." + algo.toLowerCase
        val tmpFile = File.createTempFile("checksum", suffix)
        IO.write(tmpFile, content)
        (tmpFile, suffix)
      }

    artifacts.foreach { case (artifact, sourceFile) =>
      val folder = typeToFolder(artifact.`type`)
      val classifier = artifact.classifier.map("-" + _).getOrElse("")
      val artifactName = moduleName
      val pathPattern = substituteIvyArtifactPattern(
        artifactPattern,
        org,
        moduleName,
        version,
        folder,
        artifactName,
        classifier,
        artifact.extension
      )
      val url = URI.create(pathPattern).toURL()
      httpPut(url, sourceFile, credentialFor(url, directCreds, None), log)
      val checksums = writeChecksums(sourceFile)
      checksums.foreach { case (cf, suffix) =>
        val checksumUrl = URI.create(pathPattern + suffix).toURL()
        try httpPut(checksumUrl, cf, credentialFor(checksumUrl, directCreds, None), log)
        finally cf.delete()
      }
    }

    val ivyXmlContent = lmcoursier.IvyXml(project, Nil, Nil)
    val ivyPathPattern = substituteIvyArtifactPattern(
      ivyPattern,
      org,
      moduleName,
      version,
      "ivys",
      "ivy",
      "",
      "xml"
    )
    val ivyUrl = URI.create(ivyPathPattern).toURL()
    val ivyTmp = File.createTempFile("ivy", ".xml")
    try {
      IO.write(ivyTmp, ivyXmlContent)
      httpPut(ivyUrl, ivyTmp, credentialFor(ivyUrl, directCreds, None), log)
      val checksums = writeChecksums(ivyTmp)
      checksums.foreach { case (cf, suffix) =>
        val checksumUrl = URI.create(ivyPathPattern + suffix).toURL()
        try httpPut(checksumUrl, cf, credentialFor(checksumUrl, directCreds, None), log)
        finally cf.delete()
      }
    } finally ivyTmp.delete()
  }

  /**
   * Maven layout path: groupId/artifactId/version/artifactId-version[-classifier].ext
   */
  private def mavenLayoutPath(
      groupId: String,
      artifactId: String,
      version: String,
      artifact: Artifact
  ): String =
    val groupPath = groupId.replace('.', '/')
    val classifierPart = artifact.classifier.map("-" + _).getOrElse("")
    val fileName = s"$artifactId-$version$classifierPart.${artifact.extension}"
    s"$groupPath/$artifactId/$version/$fileName"

  private def writeChecksumsForFile(
      targetFile: File,
      algorithms: Vector[String],
      log: Logger
  ): Unit =
    algorithms.foreach: algo =>
      val digestAlgo = algo.toLowerCase match
        case "md5"  => sbt.util.Digest.Md5
        case "sha1" => sbt.util.Digest.Sha1
        case other =>
          throw new IllegalArgumentException(s"Unsupported checksum algorithm: $other")
      val digest = sbt.util.Digest(digestAlgo, targetFile.toPath)
      val checksumFile = new File(targetFile.getPath + "." + algo.toLowerCase)
      IO.write(checksumFile, digest.hashHexString)
      log.debug(s"Wrote checksum: $checksumFile")

  /**
   * Publishes artifacts to a local Maven repo (Maven layout) without using Apache Ivy.
   * Layout: groupId/artifactId/version/artifactId-version[-classifier].ext
   */
  def ivylessPublishMavenToFile(
      project: CsrProject,
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      repoBase: File,
      overwrite: Boolean,
      log: Logger
  ): Unit =
    if repoBase == null then throw new IllegalArgumentException("repoBase must not be null")
    val groupId = project.module.organization.value
    // Derive artifactId: for sbt 2 plugins, module.name has cross-version (e.g. sbt-example_sbt2_3).
    // For sbt 1 plugins, mavenArtifactsOfSbtPlugin cross-versions the POM artifact name (e.g. sbt-example_2.12_1.0).
    val baseModuleName = project.module.name.value
    val pomArtName = artifacts.collectFirst { case (a, _) if a.`type` == "pom" => a.name }
    val artifactId = pomArtName match
      case Some(name) if name.startsWith(baseModuleName) && name != baseModuleName => name
      case _                                                                       => baseModuleName
    val version = project.version
    val groupPath = groupId.replace('.', '/')
    val versionDir = new File(repoBase, s"$groupPath/$artifactId/$version")
    log.info(s"Publishing to Maven repo: $versionDir")

    artifacts.foreach:
      case (artifact, sourceFile) =>
        val path = mavenLayoutPath(groupId, artifactId, version, artifact)
        val targetFile = new File(repoBase, path.replace('/', File.separatorChar))
        if !targetFile.exists || overwrite then
          targetFile.getParentFile.mkdirs()
          IO.copyFile(sourceFile, targetFile)
          log.info(s"Published $targetFile")
          writeChecksumsForFile(targetFile, checksumAlgorithms, log)
        else log.warn(s"$targetFile already exists, skipping (overwrite=$overwrite)")

    if version.endsWith("-SNAPSHOT") then
      writeMavenMetadataLocal(versionDir, groupId, artifactId, version, log)

  private def writeMavenMetadataLocal(
      versionDir: File,
      groupId: String,
      artifactId: String,
      version: String,
      log: Logger
  ): Unit =
    val timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date())
    val metadata =
      s"""|<?xml version="1.0" encoding="UTF-8"?>
          |<metadata modelVersion="1.1.0">
          |  <groupId>$groupId</groupId>
          |  <artifactId>$artifactId</artifactId>
          |  <version>$version</version>
          |  <versioning>
          |    <snapshot>
          |      <localCopy>true</localCopy>
          |    </snapshot>
          |    <lastUpdated>$timestamp</lastUpdated>
          |  </versioning>
          |</metadata>
          |""".stripMargin
    val metadataFile = new File(versionDir, "maven-metadata-local.xml")
    IO.write(metadataFile, metadata)
    log.info(s"Published $metadataFile")

  /**
   * Publishes artifacts to a remote Maven repo (HTTP) without using Apache Ivy.
   * Same layout as ivylessPublishMavenToFile; uses HTTP PUT with optional Basic auth.
   */
  def ivylessPublishMavenToUrl(
      project: CsrProject,
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      baseUrl: String,
      credentials: Seq[Credentials],
      overwrite: Boolean,
      log: Logger
  ): Unit =
    if baseUrl == null || baseUrl.trim.isEmpty then
      throw new IllegalArgumentException("baseUrl must not be null or empty")
    val groupId = project.module.organization.value
    // Derive artifactId: for sbt 2 plugins, module.name has cross-version (e.g. sbt-example_sbt2_3).
    // For sbt 1 plugins, mavenArtifactsOfSbtPlugin cross-versions the POM artifact name (e.g. sbt-example_2.12_1.0).
    val baseModuleName = project.module.name.value
    val pomArtName = artifacts.collectFirst { case (a, _) if a.`type` == "pom" => a.name }
    val artifactId = pomArtName match
      case Some(name) if name.startsWith(baseModuleName) && name != baseModuleName => name
      case _                                                                       => baseModuleName
    val version = project.version
    val directCreds = credentials.collect:
      case d: Credentials.DirectCredentials => d

    def writeChecksums(file: File): Vector[(File, String)] =
      checksumAlgorithms
        .map: algo =>
          val digestAlgo = algo.toLowerCase match
            case "md5"  => sbt.util.Digest.Md5
            case "sha1" => sbt.util.Digest.Sha1
            case other =>
              throw new IllegalArgumentException(s"Unsupported checksum algorithm: $other")
          val digest = sbt.util.Digest(digestAlgo, file.toPath)
          val content = digest.hashHexString
          val suffix = "." + algo.toLowerCase
          val tmpFile = File.createTempFile("checksum", suffix)
          IO.write(tmpFile, content)
          (tmpFile, suffix)

    val base = baseUrl.stripSuffix("/") + "/"
    artifacts.foreach:
      case (artifact, sourceFile) =>
        val path = mavenLayoutPath(groupId, artifactId, version, artifact)
        val url = URI.create(base + path).toURL()
        try
          httpPut(url, sourceFile, credentialFor(url, directCreds, None), log)
          val checksums = writeChecksums(sourceFile)
          checksums.foreach:
            case (cf, suffix) =>
              val checksumUrl = URI.create(base + path + suffix).toURL()
              try httpPut(checksumUrl, cf, credentialFor(checksumUrl, directCreds, None), log)
              finally cf.delete()
        catch
          case e: IOException =>
            throw new IOException(s"Failed to publish $path: ${e.getMessage}", e)

  /**
   * Publishes artifacts to a local file repo (FileRepository) without using Apache Ivy.
   * Same layout as ivylessPublishLocal; used for testing without an HTTP server.
   */
  def ivylessPublishToFile(
      project: CsrProject,
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      fileRepo: sbt.librarymanagement.FileRepository,
      overwrite: Boolean,
      log: Logger
  ): Unit = {
    val pattern = fileRepo.patterns.artifactPatterns.headOption.getOrElse(
      sys.error("FileRepository has no artifact pattern")
    )
    val baseStr =
      if (pattern.contains("[organisation]"))
        pattern.substring(0, pattern.indexOf("[organisation]"))
      else pattern
    val normalized = baseStr.replace('\\', '/').stripSuffix("/")
    val localRepoBase =
      if (normalized.startsWith("file:")) new File(new java.net.URI(normalized))
      else new File(normalized)
    val repoDir = localRepoBase.getAbsoluteFile
    val isMavenLayout = fileRepo.patterns.isMavenCompatible
    if isMavenLayout then
      log.info(s"Ivyless publish (Maven layout) to file repo: $repoDir")
      ivylessPublishMavenToFile(project, artifacts, checksumAlgorithms, repoDir, overwrite, log)
    else
      log.info(s"Ivyless publish (Ivy layout) to file repo: $repoDir")
      ivylessPublishLocal(project, artifacts, checksumAlgorithms, repoDir, overwrite, log)
  }

  /**
   * Task initializer for ivyless publish (remote Ivy repo or file repo).
   * When useIvy is false and publishTo is URLRepository or FileRepository, uses ivyless publish; otherwise uses Ivy.
   */
  def ivylessPublishTask: Def.Initialize[Task[Unit]] =
    import Keys.*
    Def.ifS(Def.task { (publish / skip).value })(
      Def.task {
        val log = streams.value.log
        val ref = thisProjectRef.value
        log.debug(s"Skipping publish for ${Reference.display(ref)}")
      }
    )(
      Def.ifS(Def.task { useIvy.value })(
        Def.task {
          val log = streams.value.log
          val conf = publishConfiguration.value
          val module = ivyModule.value.asInstanceOf[ModuleDescriptor]
          val publisherInterface = publisher.value
          publisherInterface.publish(module, conf, log)
        }
      )(
        Def.task {
          val log = streams.value.log
          val resolver = sbt.Classpaths.getPublishTo(publishTo.value)
          val project = csrProject.value.withPublications(csrPublications.value)
          val config = publishConfiguration.value
          val artifacts = config.artifacts.map { case (a, f) => (a, f) }
          resolver match {
            case urlRepo: sbt.librarymanagement.URLRepository =>
              val creds = allCredentials.value
              ivylessPublish(
                project,
                artifacts,
                config.checksums,
                urlRepo,
                creds,
                config.overwrite,
                log
              )
            case fileRepo: sbt.librarymanagement.FileRepository =>
              ivylessPublishToFile(
                project,
                artifacts,
                config.checksums,
                fileRepo,
                config.overwrite,
                log
              )
            case pbr: sbt.librarymanagement.PatternsBasedRepository
                if pbr.patterns.artifactPatterns.headOption.exists { pat =>
                  pat.contains("[organisation]") && !pat.trim.startsWith("http")
                } =>
              // File repo detected by pattern (e.g. scripted classloader makes type match fail)
              val pat = pbr.patterns.artifactPatterns.head
              val baseStr =
                pat.substring(0, pat.indexOf("[organisation]")).replace('\\', '/').stripSuffix("/")
              val repoDir =
                (if (baseStr.startsWith("file:")) new File(new java.net.URI(baseStr))
                 else new File(baseStr)).getAbsoluteFile
              if pbr.patterns.isMavenCompatible then
                log.info(s"Ivyless publish (Maven layout) to file repo: $repoDir")
                ivylessPublishMavenToFile(
                  project,
                  artifacts,
                  config.checksums,
                  repoDir,
                  config.overwrite,
                  log
                )
              else
                log.info(s"Ivyless publish (Ivy layout) to file repo: $repoDir")
                ivylessPublishLocal(
                  project,
                  artifacts,
                  config.checksums,
                  repoDir,
                  config.overwrite,
                  log
                )
            case mavenCache: sbt.librarymanagement.MavenCache =>
              ivylessPublishMavenToFile(
                project,
                artifacts,
                config.checksums,
                mavenCache.rootFile,
                config.overwrite,
                log
              )
            case mavenRepo: sbt.librarymanagement.MavenRepo =>
              val root = mavenRepo.root.stripSuffix("/")
              if root.startsWith("http://") || root.startsWith("https://") then
                val creds = allCredentials.value
                ivylessPublishMavenToUrl(
                  project,
                  artifacts,
                  config.checksums,
                  root,
                  creds,
                  config.overwrite,
                  log
                )
              else if root.startsWith("file:") then
                val repoBase = new File(URI.create(root))
                ivylessPublishMavenToFile(
                  project,
                  artifacts,
                  config.checksums,
                  repoBase,
                  config.overwrite,
                  log
                )
              else
                sys.error(
                  s"Ivyless Maven publish: unsupported root '$root'. Set useIvy := true or use a supported repository (http/https/file)."
                )
            case other =>
              sys.error(
                s"Ivyless publish does not support ${other.getClass.getName}. Set useIvy := true or use URLRepository, FileRepository, or MavenRepository."
              )
          }
        }
      )
    )

  /**
   * Task initializer for ivyless publishLocal.
   * Uses Def.ifS for proper selective functor behavior.
   */
  def ivylessPublishLocalTask: Def.Initialize[Task[Unit]] =
    import Keys.*
    Def.ifS(Def.task { (publishLocal / skip).value })(
      // skip = true
      Def.task {
        val log = streams.value.log
        val ref = thisProjectRef.value
        log.debug(s"Skipping publishLocal for ${Reference.display(ref)}")
      }
    )(
      // skip = false
      Def.ifS(Def.task { useIvy.value })(
        // useIvy = true: use Ivy-based publisher
        Def.task {
          val log = streams.value.log
          val conf = publishLocalConfiguration.value
          val module = ivyModule.value.asInstanceOf[ModuleDescriptor]
          val publisherInterface = publisher.value
          publisherInterface.publish(module, conf, log)
        }
      )(
        // useIvy = false: use ivyless publisher
        Def.task {
          val log = streams.value.log
          val project = csrProject.value.withPublications(csrPublications.value)
          val config = publishLocalConfiguration.value
          val artifacts = config.artifacts.map { case (a, f) => (a, f) }
          val checksumAlgos = config.checksums
          val ivyHome = ivyPaths.value.ivyHome.map(new File(_)).getOrElse {
            val userHome = new File(System.getProperty("user.home"))
            userHome / ".ivy2"
          }
          val localRepoBase = ivyHome / "local"
          val overwriteFlag = config.overwrite
          ivylessPublishLocal(project, artifacts, checksumAlgos, localRepoBase, overwriteFlag, log)
        }
      )
    )

  /**
   * Task initializer for ivyless publishM2 (publish to local Maven ~/.m2 repository).
   * Uses Def.ifS for proper selective functor behavior.
   */
  def ivylessPublishM2Task: Def.Initialize[Task[Unit]] =
    import Keys.*
    Def.ifS(Def.task { (publishM2 / skip).value })(
      // skip = true
      Def.task {
        val log = streams.value.log
        val ref = thisProjectRef.value
        log.debug(s"Skipping publishM2 for ${Reference.display(ref)}")
      }
    )(
      // skip = false
      Def.ifS(Def.task { useIvy.value })(
        // useIvy = true: use Ivy-based publisher
        Def.task {
          val log = streams.value.log
          val conf = publishM2Configuration.value
          val module = ivyModule.value.asInstanceOf[ModuleDescriptor]
          val publisherInterface = publisher.value
          publisherInterface.publish(module, conf, log)
        }
      )(
        // useIvy = false: use ivyless publisher to Maven local
        Def.task {
          val log = streams.value.log
          val project = csrProject.value.withPublications(csrPublications.value)
          val config = publishM2Configuration.value
          val artifacts = config.artifacts.map { case (a, f) => (a, f) }
          val m2Repo = Resolver.publishMavenLocal
          ivylessPublishMavenToFile(
            project,
            artifacts,
            config.checksums,
            m2Repo.rootFile,
            config.overwrite,
            log
          )
        }
      )
    )
}
