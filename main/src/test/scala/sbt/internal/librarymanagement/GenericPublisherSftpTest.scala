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
import java.util.Collections
import com.jcraft.jsch.JSch
import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import lmcoursier.definitions.{ Info, Module, ModuleName, Organization, Project as CsrProject }
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import sbt.io.IO
import sbt.io.syntax.*
import sbt.librarymanagement.*
import sbt.util.Logger
import scala.util.Try

object GenericPublisherSftpTest extends Properties:

  override def tests: List[Test] = List(
    example("sftp resolver without base path publishes remotely", sftpWithoutBasePath),
    example("ssh resolver publishes Ivy layout", sshIvyLayout),
    example("sftp falls back to credentials for the host", sftpUsesCredentials),
    example("sftp refuses to clobber a remote artifact", sftpRefusesToClobber),
    example("sftp follows a custom artifact pattern", sftpCustomPattern),
    example("a malformed default key is skipped", malformedKeySkipped),
    example("optional pattern groups need every token", substitutesOptionalGroups),
    example("sftp rejects an unknown host key", sftpRejectsUnknownHost),
  )

  private val org = "com.example"
  private val moduleName = "demo"
  private val release = "1.0.0"
  private val user = "deployer"
  private val secret = "s3cret"
  private val host = "127.0.0.1"

  private def csrProject: CsrProject =
    CsrProject(
      Module(Organization(org), ModuleName(moduleName), Map.empty),
      release,
      Nil,
      Map.empty,
      Nil,
      None,
      Nil,
      Info("", "", Nil, Nil, None),
    )

  private def config(name: String, artifacts: Vector[(Artifact, File)], overwrite: Boolean) =
    PublishConfiguration(
      false,
      "dummy-deliver-pattern",
      "release",
      Vector.empty,
      name,
      artifacts,
      Vector("sha1", "md5"),
      UpdateLogging.Default,
      overwrite,
    )

  private def jar(dir: File, content: String): Vector[(Artifact, File)] =
    val f = dir / s"$content.jar"
    IO.write(f, content)
    Vector(Artifact(moduleName, "jar", "jar") -> f)

  private def publish(
      repo: Resolver,
      artifacts: Vector[(Artifact, File)],
      overwrite: Boolean = false,
      credentials: Seq[Credentials] = Nil,
  ): Unit =
    GenericPublisher(null, Vector.empty, csrProject, credentials, Seq(repo))
      .publish(null, config(repo.name, artifacts, overwrite), Logger.Null)

  /** Runs `f` against an SFTP server rooted at `dir / "server"`, trusting its host key. */
  private def withServer[A](dir: File, trustHost: Boolean = true)(f: (Int, File) => A): A =
    val root = dir / "server"
    IO.createDirectory(root)
    val sshd = SshServer.setUpDefaultServer()
    sshd.setHost(host)
    sshd.setPort(0)
    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider((dir / "hostkey.ser").toPath))
    sshd.setPasswordAuthenticator((u, p, _) => u == user && p == secret)
    sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()))
    sshd.setFileSystemFactory(new VirtualFileSystemFactory(root.toPath))
    sshd.start()
    val knownHosts = dir / "known_hosts"
    if trustHost then
      val key = sshd.getKeyPairProvider.loadKeys(null).iterator.next.getPublic
      IO.write(knownHosts, s"[$host]:${sshd.getPort} ${PublicKeyEntry.toString(key)}\n")
    sys.props(SshPublish.knownHostsProperty) = knownHosts.getAbsolutePath
    try f(sshd.getPort, root)
    finally
      sys.props.remove(SshPublish.knownHostsProperty)
      sshd.stop(true)

  private def mavenJar(base: File): File =
    base / "com" / "example" / moduleName / release / s"$moduleName-$release.jar"

  def sftpWithoutBasePath: Result =
    IO.withTemporaryDirectory: dir =>
      withServer(dir): (port, root) =>
        publish(Resolver.sftp("test-sftp", host, port).as(user, secret), jar(dir, "first"))
        Result
          .assert(IO.read(mavenJar(root)) == "first")
          .and(Result.assert(new File(mavenJar(root).getPath + ".sha1").exists))

  def sshIvyLayout: Result =
    IO.withTemporaryDirectory: dir =>
      withServer(dir): (port, root) =>
        val repo = Resolver.ssh("test-ssh", host, port, "/ivy")(using Resolver.ivyStylePatterns)
        publish(repo.as(user, secret), jar(dir, "ivy"))
        val moduleDir = root / "ivy" / org / moduleName / release
        Result
          .assert(IO.read(moduleDir / "jars" / s"$moduleName.jar") == "ivy")
          .and(Result.assert((moduleDir / "ivys" / "ivy.xml").exists))
          .and(Result.assert((moduleDir / "ivys" / "ivy.xml.sha1").exists))

  def sftpUsesCredentials: Result =
    IO.withTemporaryDirectory: dir =>
      withServer(dir): (port, root) =>
        val creds = Credentials("realm", host, user, secret)
        publish(Resolver.sftp("test-sftp", host, port), jar(dir, "c"), credentials = Seq(creds))
        Result.assert(IO.read(mavenJar(root)) == "c")

  def sftpRefusesToClobber: Result =
    IO.withTemporaryDirectory: dir =>
      withServer(dir): (port, root) =>
        val repo = Resolver.sftp("test-sftp", host, port).as(user, secret)
        publish(repo, jar(dir, "first"))
        val second = Try(publish(repo, jar(dir, "second")))
        publish(repo, jar(dir, "third"), overwrite = true)
        Result
          .assert(second.failed.toOption.exists(_.getMessage.contains("already exists")))
          .and(Result.assert(IO.read(mavenJar(root)) == "third"))

  def sftpCustomPattern: Result =
    IO.withTemporaryDirectory: dir =>
      withServer(dir): (port, root) =>
        val patterns = Patterns(true, "/c/[organisation]/[module]/[artifact](-[classifier]).[ext]")
        val src = dir / "src.jar"
        IO.write(src, "src")
        val sources = Artifact(moduleName, "src", "jar", "sources") -> src
        publish(
          Resolver.sftp("test-sftp", host, port)(using patterns).as(user, secret),
          jar(dir, "main") :+ sources
        )
        val moduleDir = root / "c" / "com" / "example" / moduleName
        Result
          .assert(IO.read(moduleDir / s"$moduleName.jar") == "main")
          .and(Result.assert(IO.read(moduleDir / s"$moduleName-sources.jar") == "src"))

  def malformedKeySkipped: Result =
    IO.withTemporaryDirectory: dir =>
      val key = dir / "id_rsa"
      IO.write(key, "not a key")
      val jsch = new JSch()
      SshPublish.addIdentities(jsch, Seq(key), Logger.Null)
      Result.assert(jsch.getIdentityNames.isEmpty)

  def substitutesOptionalGroups: Result =
    val pattern = "[organisation]/[module](_[scalaVersion])/[artifact](-[classifier]).[ext]"
    val tokens = Map("organisation" -> "o", "module" -> "m", "artifact" -> "a", "ext" -> "jar")
    val full = tokens ++ Map("scalaVersion" -> "3", "classifier" -> "src")
    Result
      .assert(GenericPublisher.substitutePattern(pattern, tokens) == "o/m/a.jar")
      .and(Result.assert(GenericPublisher.substitutePattern(pattern, full) == "o/m_3/a-src.jar"))

  def sftpRejectsUnknownHost: Result =
    IO.withTemporaryDirectory: dir =>
      withServer(dir, trustHost = false): (port, root) =>
        val attempt = Try(
          publish(Resolver.sftp("test-sftp", host, port).as(user, secret), jar(dir, "x"))
        )
        Result
          .assert(attempt.isFailure)
          .and(Result.assert(!mavenJar(root).exists))
end GenericPublisherSftpTest
