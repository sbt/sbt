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

import java.io.File
import java.net.InetSocketAddress
import com.sun.net.httpserver.{ HttpExchange, HttpHandler, HttpServer }
import hedgehog.*
import hedgehog.runner.*
import lmcoursier.definitions.{ Info, Module, ModuleName, Organization, Project as CsrProject }
import _root_.sbt.io.IO
import _root_.sbt.io.syntax.*
import _root_.sbt.librarymanagement.*
import _root_.sbt.util.{ Digest, Level, Logger }
import scala.collection.mutable.ListBuffer

object GenericPublisherOverwriteTest extends Properties:

  override def tests: List[Test] = List(
    example("ivy layout republishes changed bytes", ivyLayoutRepublishes),
    example("ivy layout refreshes checksums on republish", ivyLayoutRefreshesChecksums),
    example("ivy.xml is rewritten on republish", ivyXmlRepublishes),
    example("maven layout republishes changed bytes", mavenLayoutRepublishes),
    example("republish warns when overwrite is disabled", republishWarns),
    example("first publish does not warn", firstPublishIsQuiet),
    example("file: URLRepository publishes to the filesystem", fileUrlRepositoryPublishes),
    example(
      "file: Maven URLRepository publishes to the filesystem",
      fileMavenUrlRepositoryPublishes
    ),
    example("remote publish refuses to clobber", remoteRefusesToClobber),
    example("remote publish overwrites when allowed", remoteOverwritesWhenAllowed),
  )

  private val org = "com.example"
  private val moduleName = "demo"
  private val release = "1.0.0"

  private def csrProject(version: String): CsrProject =
    CsrProject(
      Module(Organization(org), ModuleName(moduleName), Map.empty),
      version,
      Nil,
      Map.empty,
      Nil,
      None,
      Nil,
      Info("", "", Nil, Nil, None),
    )

  private def publisher(version: String, resolvers: Seq[Resolver]): GenericPublisher =
    GenericPublisher(null, Vector.empty, csrProject(version), Nil, resolvers)

  private def config(
      resolverName: String,
      artifacts: Vector[(Artifact, File)],
      overwrite: Boolean,
  ): PublishConfiguration =
    PublishConfiguration(
      false,
      "dummy-deliver-pattern",
      "release",
      Vector.empty,
      resolverName,
      artifacts,
      Vector("sha1", "md5"),
      UpdateLogging.Default,
      overwrite,
    )

  private def jarArtifact = Artifact(moduleName, "jar", "jar")

  private def sourceFileWith(dir: File, content: String): File =
    val f = dir / s"${content}-source.jar"
    IO.write(f, content)
    f

  /** Collects warnings so a test can assert on them; everything else is discarded. */
  private class RecordingLogger extends Logger:
    val warnings: ListBuffer[String] = ListBuffer.empty
    override def log(level: Level.Value, message: => String): Unit =
      if level == Level.Warn then warnings += message
    override def success(message: => String): Unit = ()
    override def trace(t: => Throwable): Unit = ()

  private def withTempDir[A](f: File => A): A =
    IO.withTemporaryDirectory(f)

  private def ivyRepo(base: File): Resolver =
    Resolver.file("local", base)(using Resolver.ivyStylePatterns)

  private def publishedJar(repo: File): File =
    repo / org / moduleName / release / "jars" / s"$moduleName.jar"

  private def publishedMavenJar(repo: File): File =
    repo / "com" / "example" / moduleName / release / s"$moduleName-$release.jar"

  /**
   * Publishes each of `contents` in turn into an Ivy-layout file repository, with overwrite
   * disabled on every publish.
   */
  private def publishIvy(
      repo: File,
      work: File,
      contents: Seq[String],
      log: Logger,
  ): Unit =
    val p = publisher(release, Seq(ivyRepo(repo)))
    contents.foreach: content =>
      val artifacts = Vector(jarArtifact -> sourceFileWith(work, content))
      p.publish(null, config("local", artifacts, overwrite = false), log)

  def ivyLayoutRepublishes: Result =
    withTempDir: dir =>
      val repo = dir / "repo"
      publishIvy(repo, dir, Seq("first", "second"), Logger.Null)
      val target = publishedJar(repo)
      Result
        .assert(target.exists)
        .log(s"$target was never published")
        .and(
          Result
            .assert(IO.read(target) == "second")
            .log(s"expected the republished bytes, got ${IO.read(target)}")
        )

  def ivyLayoutRefreshesChecksums: Result =
    withTempDir: dir =>
      val repo = dir / "repo"
      publishIvy(repo, dir, Seq("first", "second"), Logger.Null)
      val target = publishedJar(repo)
      val expected = Digest("sha1", target.toPath).hashHexString
      val recorded = IO.read(new File(target.getPath + ".sha1"))
      Result
        .assert(recorded == expected)
        .log(s"checksum $recorded does not match the published bytes ($expected)")

  def ivyXmlRepublishes: Result =
    withTempDir: dir =>
      val repo = dir / "repo"
      publishIvy(repo, dir, Seq("first"), Logger.Null)
      val ivyXml = repo / org / moduleName / release / "ivys" / "ivy.xml"
      val firstStamp = IO.read(ivyXml)
      IO.write(ivyXml, "<clobbered/>")
      publishIvy(repo, dir, Seq("second"), Logger.Null)
      Result
        .assert(IO.read(ivyXml) == firstStamp)
        .log("ivy.xml was not rewritten on republish")

  def mavenLayoutRepublishes: Result =
    withTempDir: dir =>
      val repo = dir / "repo"
      val p = publisher(release, Seq(MavenCache("local", repo)))
      Seq("first", "second").foreach: content =>
        val artifacts = Vector(jarArtifact -> sourceFileWith(dir, content))
        p.publish(null, config("local", artifacts, overwrite = false), Logger.Null)
      val target = publishedMavenJar(repo)
      Result
        .assert(target.exists && IO.read(target) == "second")
        .log(s"expected the republished bytes in $target")

  def republishWarns: Result =
    withTempDir: dir =>
      val log = new RecordingLogger
      publishIvy(dir / "repo", dir, Seq("first", "second"), log)
      Result
        .assert(log.warnings.exists(_.contains("already exists, overwriting")))
        .log(s"expected an overwrite warning, got ${log.warnings.toList}")

  def firstPublishIsQuiet: Result =
    withTempDir: dir =>
      val log = new RecordingLogger
      publishIvy(dir / "repo", dir, Seq("first"), log)
      Result
        .assert(log.warnings.isEmpty)
        .log(s"a first publish should not warn, got ${log.warnings.toList}")

  private def fileUrlRepository(repo: File, layout: String, mavenCompatible: Boolean): Resolver =
    val pattern = s"${repo.toURI.toString.stripSuffix("/")}/$layout"
    val patterns = Patterns()
      .withIvyPatterns(Vector(pattern))
      .withArtifactPatterns(Vector(pattern))
      .withIsMavenCompatible(mavenCompatible)
    URLRepository("local", patterns)

  def fileUrlRepositoryPublishes: Result =
    withTempDir: dir =>
      val repo = dir / "repo"
      val layout = "[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]"
      val p = publisher(release, Seq(fileUrlRepository(repo, layout, mavenCompatible = false)))
      val artifacts = Vector(jarArtifact -> sourceFileWith(dir, "first"))
      p.publish(null, config("local", artifacts, overwrite = false), Logger.Null)
      Result
        .assert(publishedJar(repo).exists)
        .log("a file: URLRepository should publish to the filesystem, not over HTTP")

  def fileMavenUrlRepositoryPublishes: Result =
    withTempDir: dir =>
      val repo = dir / "repo"
      val layout = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
      val p = publisher(release, Seq(fileUrlRepository(repo, layout, mavenCompatible = true)))
      val artifacts = Vector(jarArtifact -> sourceFileWith(dir, "first"))
      p.publish(null, config("local", artifacts, overwrite = false), Logger.Null)
      Result
        .assert(publishedMavenJar(repo).exists)
        .log("a Maven-compatible file: URLRepository should publish to the filesystem")

  /** A repository that answers HEAD from what it has already accepted via PUT. */
  private def withHttpRepo[A](f: (String, collection.mutable.Set[String]) => A): A =
    val stored = collection.mutable.Set.empty[String]
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/",
      new HttpHandler:
        override def handle(exchange: HttpExchange): Unit =
          val path = exchange.getRequestURI.getPath
          val status = exchange.getRequestMethod match
            case "PUT"  => stored += path; 201
            case "HEAD" => if stored.contains(path) then 200 else 404
            case _      => 405
          exchange.sendResponseHeaders(status, -1)
          exchange.close()
    )
    server.start()
    try f(s"http://127.0.0.1:${server.getAddress.getPort}/repo", stored)
    finally server.stop(0)

  private def publishRemote(
      base: String,
      dir: File,
      content: String,
      overwrite: Boolean,
  ): Unit =
    val p = publisher(release, Seq(MavenRepository("remote", base)))
    val artifacts = Vector(jarArtifact -> sourceFileWith(dir, content))
    p.publish(null, config("remote", artifacts, overwrite), Logger.Null)

  def remoteRefusesToClobber: Result =
    withTempDir: dir =>
      withHttpRepo: (base, _) =>
        publishRemote(base, dir, "first", overwrite = false)
        val thrown =
          try
            publishRemote(base, dir, "second", overwrite = false)
            None
          catch case e: java.io.IOException => Some(e.getMessage)
        thrown match
          case None      => Result.failure.log("republishing to a remote repo should have failed")
          case Some(msg) =>
            Result
              .assert(msg.contains("already exists") && msg.contains(moduleName))
              .log(s"the failure should name the artifact, got: $msg")

  def remoteOverwritesWhenAllowed: Result =
    withTempDir: dir =>
      withHttpRepo: (base, stored) =>
        publishRemote(base, dir, "first", overwrite = false)
        publishRemote(base, dir, "second", overwrite = true)
        Result
          .assert(stored.exists(_.endsWith(s"$moduleName-$release.jar")))
          .log(s"expected the jar to be PUT, got ${stored.toList}")
end GenericPublisherOverwriteTest
