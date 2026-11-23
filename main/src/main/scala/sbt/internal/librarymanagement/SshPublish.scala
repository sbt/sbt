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
import com.jcraft.jsch.{ ChannelSftp, JSch, JSchException, Session, SftpException }
import sbt.librarymanagement.*
import sbt.util.Logger
import scala.util.Using

/**
 * Uploads files to an SSH or SFTP resolver over JSch's SFTP channel.
 * Host keys are checked strictly against `~/.ssh/known_hosts`, or the file named by `sbt.ssh.knownHosts`.
 */
private[sbt] object SshPublish:
  val knownHostsProperty = "sbt.ssh.knownHosts"
  private val timeoutMillis = 30000
  private val defaultKeys = Seq("id_ed25519", "id_ecdsa", "id_rsa")

  given Using.Releasable[Session] = _.disconnect()
  given Using.Releasable[ChannelSftp] = _.disconnect()

  /** Uploads each file to its remote path. */
  def upload(
      repo: SshBasedRepository,
      credentials: Seq[Credentials.DirectCredentials],
      files: Seq[(String, File)],
      overwrite: Boolean,
      log: Logger
  ): Unit =
    val host = repo.connection.hostname.getOrElse(sys.error(s"${repo.name} has no hostname"))
    val port = repo.connection.port.getOrElse(22)
    val permissions = repo match
      case r: SshRepository => r.publishPermissions.map(Integer.parseInt(_, 8))
      case _                => None
    withSftp(host, port, repo.connection.authentication, credentials.find(_.host == host)): sftp =>
      if !overwrite then
        files
          .collectFirst:
            case (path, _) if !isChecksum(path) && exists(sftp, path) => path
          .foreach: path =>
            throw new IOException(
              s"$host:$path already exists and overwriting is disabled.\n\t" +
                "Publish a new version, or relax the setting as follows:\n\t" +
                "publish / publishConfiguration := publishConfiguration.value.withOverwrite(true)"
            )
      files.foreach: (path, f) =>
        mkdirs(sftp, path.substring(0, path.lastIndexOf('/').max(0)))
        sftp.put(f.getAbsolutePath, path, ChannelSftp.OVERWRITE)
        permissions.foreach(sftp.chmod(_, path))
        log.info(s"published $host:$path")
  end upload

  private def withSftp[A](
      host: String,
      port: Int,
      auth: Option[SshAuthentication],
      hostCredentials: Option[Credentials.DirectCredentials]
  )(f: ChannelSftp => A): A =
    val jsch = new JSch()
    val sshDir = new File(sys.props("user.home"), ".ssh")
    val knownHosts =
      sys.props.get(knownHostsProperty).map(new File(_)).getOrElse(new File(sshDir, "known_hosts"))
    if knownHosts.isFile then jsch.setKnownHosts(knownHosts.getAbsolutePath)
    val (user, password) = auth match
      case Some(a: KeyFileAuthentication) =>
        jsch.addIdentity(a.keyfile.getAbsolutePath, a.password.map(_.getBytes("UTF-8")).orNull)
        (a.user, None)
      case Some(a: PasswordAuthentication) =>
        (a.user, a.password.orElse(hostCredentials.filter(_.userName == a.user).map(_.passwd)))
      case _ =>
        hostCredentials match
          case Some(c) => (c.userName, Some(c.passwd))
          case None    => (sys.props("user.name"), None)
    if !auth.exists(_.isInstanceOf[KeyFileAuthentication]) then
      defaultKeys
        .map(new File(sshDir, _))
        .filter(_.isFile)
        .foreach(k => jsch.addIdentity(k.getAbsolutePath))
    try
      Using
        .Manager: use =>
          val session = use(jsch.getSession(user, host, port))
          password.foreach(session.setPassword)
          session.setConfig("StrictHostKeyChecking", "yes")
          session.connect(timeoutMillis)
          val channel = use(session.openChannel("sftp").asInstanceOf[ChannelSftp])
          channel.connect(timeoutMillis)
          f(channel)
        .get
    catch
      case e: JSchException =>
        throw new IOException(
          s"SSH connection to $user@$host:$port failed: ${e.getMessage}\n\t" +
            s"The host key must be listed in $knownHosts, for example via ssh-keyscan.",
          e
        )
  end withSftp

  private def isChecksum(path: String): Boolean =
    path.endsWith(".md5") || path.endsWith(".sha1")

  private def exists(sftp: ChannelSftp, path: String): Boolean =
    try
      sftp.stat(path)
      true
    catch case e: SftpException if e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE => false

  private def mkdirs(sftp: ChannelSftp, dir: String): Unit =
    val root = if dir.startsWith("/") then "/" else ""
    dir
      .split('/')
      .filter(_.nonEmpty)
      .scanLeft(root)((acc, p) => if acc.isEmpty || acc == "/" then acc + p else s"$acc/$p")
      .drop(1)
      .foreach(d => if !exists(sftp, d) then sftp.mkdir(d))
end SshPublish
