/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package librarymanagement

import java.io.{ File, IOException }
import java.net.{ URI, URL }
import java.util.regex.Matcher

import gigahorse.AuthScheme
import gigahorse.support.apachehttp.Gigahorse
import sbt.internal.librarymanagement.mavenint.PomExtraDependencyAttributes
import sbt.librarymanagement.*
import sbt.util.Logger
import sbt.io.IO
import sbt.io.syntax.*
import scala.concurrent.Await
import scala.concurrent.duration.*
import lmcoursier.definitions.Project as CsrProject

/**
 * Publishes artifacts without Apache Ivy.
 */
class GenericPublisher private[sbt] (
    dependencyResolution: DependencyResolution,
    pomRepositories: Vector[Resolver],
    project: CsrProject,
    credentials: Seq[Credentials],
    resolvers: Seq[Resolver]
) extends PublisherInterface:

  // Extension used for PGP signature files; checksums are not generated for these.
  private val signatureExt = ".asc"

  override def moduleDescriptor(moduleSetting: ModuleDescriptorConfiguration): ModuleDescriptor =
    dependencyResolution.moduleDescriptor(moduleSetting)

  override def makePomFile(
      module: ModuleDescriptor,
      configuration: MakePomConfiguration,
      log: Logger
  ): File =
    val file = configuration.file.getOrElse(sys.error("makePom file must be specified."))
    val ms = module.moduleSettings.asInstanceOf[ModuleDescriptorConfiguration]
    val mid = ms.module
    val info = configuration.moduleInfo.orElse(Option(ms.moduleInfo))
    val deps = module.directDependencies
    val extra = configuration.extra.getOrElse(scala.xml.NodeSeq.Empty)
    val confs = configuration.configurations
    val scalaInfo = ms.scalaModuleInfo
    val pomXml =
      sbt.internal.PomGenerator.makePom(
        mid,
        info,
        deps,
        confs,
        extra,
        scalaInfo,
        pomRepositories,
        configuration.filterRepositories,
        configuration.allRepositories,
      )
    val processed = configuration.process(pomXml)
    val printer = new scala.xml.PrettyPrinter(1000, 4)
    val formatted = scala.xml.XML.loadString(printer.format(processed))
    scala.xml.XML.save(file.getAbsolutePath, formatted, "UTF-8", xmlDecl = true)
    log.info("Wrote " + file.getAbsolutePath)
    file

  override def publish(
      module: ModuleDescriptor,
      configuration: PublishConfiguration,
      log: Logger
  ): Unit =
    val name = configuration.resolverName.getOrElse(
      sys.error("GenericPublisher.publish requires PublishConfiguration.resolverName to be set")
    )
    val target = resolvers.filter(_.name == name) match
      case Seq(r) => r
      case Seq()  => sys.error(s"no resolver named '$name' is configured")
      case _      =>
        sys.error(
          s"multiple resolvers are named '$name'; " +
            s"'local' and '${Resolver.publishMavenLocal.name}' are reserved for " +
            "publishLocal and publishM2 respectively"
        )
    val artifacts = configuration.artifacts
    target match
      case urlRepo: URLRepository =>
        ivylessPublish(artifacts, configuration.checksums, urlRepo, configuration.overwrite, log)
      case fileRepo: FileRepository =>
        ivylessPublishToFile(
          artifacts,
          configuration.checksums,
          fileRepo,
          configuration.overwrite,
          log
        )
      case pbr: PatternsBasedRepository if pbr.patterns.artifactPatterns.headOption.exists { pat =>
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
            artifacts,
            configuration.checksums,
            repoDir,
            configuration.overwrite,
            log
          )
        else
          log.info(s"Ivyless publish (Ivy layout) to file repo: $repoDir")
          ivylessPublishLocal(
            artifacts,
            configuration.checksums,
            repoDir,
            configuration.overwrite,
            log
          )
      case mavenCache: MavenCache =>
        ivylessPublishMavenToFile(
          artifacts,
          configuration.checksums,
          mavenCache.rootFile,
          configuration.overwrite,
          log
        )
      case mavenRepo: MavenRepo =>
        val root = mavenRepo.root.stripSuffix("/")
        if root.startsWith("http://") || root.startsWith("https://") then
          ivylessPublishMavenToUrl(
            artifacts,
            configuration.checksums,
            root,
            configuration.overwrite,
            log
          )
        else if root.startsWith("file:") then
          val repoBase = new File(URI.create(root))
          ivylessPublishMavenToFile(
            artifacts,
            configuration.checksums,
            repoBase,
            configuration.overwrite,
            log
          )
        else
          sys.error(
            s"ivyless Maven publish: unsupported root '$root'; use a supported repository (http/https/file)."
          )
      case other =>
        sys.error(
          s"ivyless publish does not support ${other.getClass.getName}; use URLRepository, FileRepository, or MavenRepository."
        )
  end publish

  private def pluginCrossPath: Seq[String] =
    val attrs = project.module.attributes
    attrs.get(PomExtraDependencyAttributes.ScalaVersionKey).map("scala_" + _).toSeq ++
      attrs.get(PomExtraDependencyAttributes.SbtVersionKey).map("sbt_" + _).toSeq

  private def typeToFolder(tpe: String): String = tpe match
    case "jar"                                   => "jars"
    case "src" | "source" | "sources"            => "srcs"
    case "doc" | "docs" | "javadoc" | "javadocs" => "docs"
    case "pom"                                   => "poms"
    case "ivy"                                   => "ivys"
    case other                                   => other + "s"

  /**
   * Publishes artifacts to the local Ivy repository without using Apache Ivy.
   * Uses the pattern: [org]/[module]/[revision]/[types]/[artifact](-[classifier]).[ext]
   */
  private def ivylessPublishLocal(
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      localRepoBase: File,
      overwrite: Boolean,
      log: Logger
  ): Unit =
    val org = project.module.organization.value
    val moduleName = project.module.name.value
    val version = project.version

    // Base directory: localRepoBase / org / module / (scala_V/)(sbt_V/) / version
    val moduleDir =
      pluginCrossPath.foldLeft(localRepoBase / org / moduleName)(_ / _) / version

    log.info(s"Publishing to $moduleDir")

    // Write ivy.xml first (so ivys/ exists even if artifact copy fails)
    val ivysDir = moduleDir / "ivys"
    val ivyXmlFile = ivysDir / "ivy.xml"
    IO.createDirectory(ivysDir)
    val ivyXmlContent = lmcoursier.IvyXml(project, Nil, Nil)
    if !ivyXmlFile.exists || overwrite then
      IO.write(ivyXmlFile, ivyXmlContent)
      log.info(s"published $ivyXmlFile")
      writeChecksumsForFile(ivyXmlFile, checksumAlgorithms, log)
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
        log.info(s"published $targetFile")
        if !targetFile.getName.endsWith(signatureExt) then
          writeChecksumsForFile(targetFile, checksumAlgorithms, log)
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
    // Substitute or drop optional Ivy pattern parts (scala/sbt version), remove branch for ivyless layout
    val attrs = project.module.attributes
    val scalaV = attrs.get(PomExtraDependencyAttributes.ScalaVersionKey)
    val sbtV = attrs.get(PomExtraDependencyAttributes.SbtVersionKey)
    s = s.replaceAll(
      "\\(scala_[^)]+/\\)",
      scalaV.map(v => Matcher.quoteReplacement(s"scala_$v/")).getOrElse("")
    )
    s = s.replaceAll(
      "\\(sbt_[^)]+/\\)",
      sbtV.map(v => Matcher.quoteReplacement(s"sbt_$v/")).getOrElse("")
    )
    s = s.replaceAll("\\(\\[branch\\]/\\)", "")
    s
  }

  /**
   * Picks credentials for a URL. Matches host; when realm is given, prefers credential with matching realm (per Publishing docs).
   */
  private def credentialFor(
      url: URL,
      credentials: Seq[Credentials.DirectCredentials],
      realm: Option[String]
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
    log.info(s"published $url")

  /**
   * Publishes artifacts to a remote Ivy repo (URLRepository) without using Apache Ivy.
   * Uses HTTP PUT; supports credentials. Produces the same layout as ivylessPublishLocal.
   */
  private def ivylessPublish(
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      urlRepo: URLRepository,
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
      if !url.toString.endsWith(signatureExt) then
        val checksums = writeChecksumsToTempFiles(sourceFile, checksumAlgorithms)
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
      val checksums = writeChecksumsToTempFiles(ivyTmp, checksumAlgorithms)
      checksums.foreach { case (cf, suffix) =>
        val checksumUrl = URI.create(ivyPathPattern + suffix).toURL()
        try httpPut(checksumUrl, cf, credentialFor(checksumUrl, directCreds, None), log)
        finally cf.delete()
      }
    } finally ivyTmp.delete()
  }

  /**
   * Publishes artifacts to a local file repo (FileRepository) without using Apache Ivy.
   * Same layout as ivylessPublishLocal; used for testing without an HTTP server.
   */
  private def ivylessPublishToFile(
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      fileRepo: FileRepository,
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
      ivylessPublishMavenToFile(artifacts, checksumAlgorithms, repoDir, overwrite, log)
    else
      log.info(s"Ivyless publish (Ivy layout) to file repo: $repoDir")
      ivylessPublishLocal(artifacts, checksumAlgorithms, repoDir, overwrite, log)
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

  private def normalizedChecksumAlgorithm(algo: String): String =
    algo.toLowerCase match
      case a @ ("md5" | "sha1") => a
      case other                =>
        throw new IllegalArgumentException(s"Unsupported checksum algorithm: $other")

  /**
   * Writes a `targetFile.<algo>` checksum file alongside `targetFile` for each algorithm.
   */
  private def writeChecksumsForFile(
      targetFile: File,
      algorithms: Vector[String],
      log: Logger
  ): Unit =
    algorithms.foreach: algo =>
      val digestAlgo = normalizedChecksumAlgorithm(algo)
      val digest = sbt.util.Digest(digestAlgo, targetFile.toPath)
      val checksumFile = new File(targetFile.getPath + "." + digestAlgo)
      IO.write(checksumFile, digest.hashHexString)
      log.debug(s"Wrote checksum: $checksumFile")

  /**
   * Computes checksums for `file` and writes each to its own temp file, paired with its
   * suffix (e.g. ".md5"), for callers that need to HTTP PUT them elsewhere before discarding.
   */
  private def writeChecksumsToTempFiles(
      file: File,
      algorithms: Vector[String]
  ): Vector[(File, String)] =
    algorithms.map: algo =>
      val digestAlgo = normalizedChecksumAlgorithm(algo)
      val digest = sbt.util.Digest(digestAlgo, file.toPath)
      val suffix = "." + digestAlgo
      val tmpFile = File.createTempFile("checksum", suffix)
      IO.write(tmpFile, digest.hashHexString)
      (tmpFile, suffix)

  /**
   * Publishes artifacts to a local Maven repo (Maven layout) without using Apache Ivy.
   * Layout: groupId/artifactId/version/artifactId-version[-classifier].ext
   */
  private def ivylessPublishMavenToFile(
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
          log.info(s"published $targetFile")
          if !targetFile.toString.endsWith(signatureExt) then
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
    log.info(s"published $metadataFile")

  /**
   * Publishes artifacts to a remote Maven repo (HTTP) without using Apache Ivy.
   * Same layout as ivylessPublishMavenToFile; uses HTTP PUT with optional Basic auth.
   */
  private def ivylessPublishMavenToUrl(
      artifacts: Vector[(Artifact, File)],
      checksumAlgorithms: Vector[String],
      baseUrl: String,
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

    val base = baseUrl.stripSuffix("/") + "/"
    artifacts.foreach:
      case (artifact, sourceFile) =>
        val path = mavenLayoutPath(groupId, artifactId, version, artifact)
        val url = URI.create(base + path).toURL()
        try
          httpPut(url, sourceFile, credentialFor(url, directCreds, None), log)
          if !sourceFile.toString.endsWith(signatureExt) then
            val checksums = writeChecksumsToTempFiles(sourceFile, checksumAlgorithms)
            checksums.foreach:
              case (cf, suffix) =>
                val checksumUrl = URI.create(base + path + suffix).toURL()
                try httpPut(checksumUrl, cf, credentialFor(checksumUrl, directCreds, None), log)
                finally cf.delete()
        catch
          case e: IOException =>
            throw new IOException(s"Failed to publish $path: ${e.getMessage}", e)
end GenericPublisher

object GenericPublisher:
  def apply(
      dependencyResolution: DependencyResolution,
      pomRepositories: Vector[Resolver],
      project: CsrProject,
      credentials: Seq[Credentials]
  ): GenericPublisher =
    apply(dependencyResolution, pomRepositories, project, credentials, Nil)

  def apply(
      dependencyResolution: DependencyResolution,
      pomRepositories: Vector[Resolver],
      project: CsrProject,
      credentials: Seq[Credentials],
      resolvers: Seq[Resolver]
  ): GenericPublisher =
    new GenericPublisher(dependencyResolution, pomRepositories, project, credentials, resolvers)
end GenericPublisher
