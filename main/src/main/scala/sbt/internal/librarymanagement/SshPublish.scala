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

import java.io.{ ByteArrayOutputStream, File, IOException, InputStream, OutputStream }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import com.jcraft.jsch.{
  Channel,
  ChannelExec,
  ChannelSftp,
  JSch,
  JSchException,
  Session,
  SftpException
}
import sbt.librarymanagement.*
import sbt.util.Logger
import scala.util.Using

/**
 * Uploads files to an SSH resolver over SCP, or to an SFTP resolver over SFTP with an SCP fallback.
 * Host keys are checked strictly against `~/.ssh/known_hosts`, or the file named by `sbt.ssh.knownHosts`.
 */
private[sbt] object SshPublish:
  val knownHostsProperty = "sbt.ssh.knownHosts"
  private val timeoutMillis = 30000
  private val defaultKeys = Seq("id_ed25519", "id_ecdsa", "id_rsa")

  given Using.Releasable[Session] = _.disconnect()
  given Using.Releasable[Channel] = _.disconnect()

  private trait Transport:
    def exists(path: String): Boolean
    def put(file: File, path: String): Unit

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
    val creds = credentials.find(_.host == host)
    withSession(host, port, repo.connection.authentication, creds, log): session =>
      withTransport(session, repo, log): transport =>
        if !overwrite then
          files
            .collectFirst:
              case (path, _) if !isChecksum(path) && transport.exists(path) => path
            .foreach: path =>
              throw new IOException(
                s"$host:$path already exists and overwriting is disabled.\n\t" +
                  "Publish a new version, or relax the setting as follows:\n\t" +
                  "publish / publishConfiguration := publishConfiguration.value.withOverwrite(true)"
              )
        files.foreach: (path, f) =>
          transport.put(f, path)
          log.info(s"published $host:$path")
  end upload

  /** Adds each readable key, skipping one that fails to load so other authentication can proceed. */
  private[sbt] def addIdentities(jsch: JSch, keys: Seq[File], log: Logger): Unit =
    keys
      .filter(_.isFile)
      .foreach: k =>
        try jsch.addIdentity(k.getAbsolutePath)
        catch case e: JSchException => log.debug(s"skipping SSH key $k: ${e.getMessage}")

  private def withSession[A](
      host: String,
      port: Int,
      auth: Option[SshAuthentication],
      hostCredentials: Option[Credentials.DirectCredentials],
      log: Logger
  )(f: Session => A): A =
    val jsch = new JSch()
    val sshDir = new File(sys.props("user.home"), ".ssh")
    val knownHosts =
      sys.props.get(knownHostsProperty).map(new File(_)).getOrElse(new File(sshDir, "known_hosts"))
    if knownHosts.isFile then jsch.setKnownHosts(knownHosts.getAbsolutePath)
    val (user, password) = auth match
      case Some(a: KeyFileAuthentication) =>
        jsch.addIdentity(a.keyfile.getAbsolutePath, a.password.map(_.getBytes(UTF_8)).orNull)
        (a.user, None)
      case Some(a: PasswordAuthentication) =>
        (a.user, a.password.orElse(hostCredentials.filter(_.userName == a.user).map(_.passwd)))
      case _ =>
        hostCredentials match
          case Some(c) => (c.userName, Some(c.passwd))
          case None    => (sys.props("user.name"), None)
    if !auth.exists(_.isInstanceOf[KeyFileAuthentication]) then
      addIdentities(jsch, defaultKeys.map(new File(sshDir, _)), log)
    Using.resource(jsch.getSession(user, host, port)): session =>
      password.foreach(session.setPassword)
      session.setConfig("StrictHostKeyChecking", "yes")
      try session.connect(timeoutMillis)
      catch
        case e: JSchException =>
          throw new IOException(
            s"SSH connection to $user@$host:$port failed: ${e.getMessage}\n\t" +
              s"The host key must be listed in $knownHosts, for example via ssh-keyscan.",
            e
          )
      f(session)
  end withSession

  private def withTransport[A](session: Session, repo: SshBasedRepository, log: Logger)(
      f: Transport => A
  ): A =
    repo match
      case r: SshRepository => f(Scp(session, r.publishPermissions))
      case _                =>
        val sftp = session.openChannel("sftp").asInstanceOf[ChannelSftp]
        try sftp.connect(timeoutMillis)
        catch
          case e: JSchException =>
            sftp.disconnect()
            log.warn(s"SFTP is unavailable on ${repo.name} (${e.getMessage}); falling back to SCP")
        if sftp.isConnected then Using.resource(sftp)(c => f(Sftp(c)))
        else f(Scp(session, None))

  private def isChecksum(path: String): Boolean =
    path.endsWith(".md5") || path.endsWith(".sha1")

  private final class Sftp(sftp: ChannelSftp) extends Transport:
    def exists(path: String): Boolean =
      try
        sftp.stat(path)
        true
      catch case e: SftpException if e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE => false

    def put(file: File, path: String): Unit =
      val (root, dirs, _) = split(path)
      dirs.indices
        .map(i => root + dirs.take(i + 1).mkString("/"))
        .foreach(d => if !exists(d) then sftp.mkdir(d))
      sftp.put(file.getAbsolutePath, path, ChannelSftp.OVERWRITE)

  /** Speaks the SCP protocol, which creates directories itself, so no remote shell is needed. */
  private final class Scp(session: Session, permissions: Option[String]) extends Transport:
    private val mode = "%04o".format(Integer.parseInt(permissions.getOrElse("0644"), 8))

    def exists(path: String): Boolean =
      exec(s"scp -f ${quote(path)}"): (in, out) =>
        out.write(0)
        out.flush()
        in.read() == 'C'

    def put(file: File, path: String): Unit =
      val (root, dirs, name) = split(path)
      exec(s"scp -r -t ${quote(if root.isEmpty then "." else root)}"): (in, out) =>
        def send(line: String): Unit =
          out.write(line.getBytes(UTF_8))
          out.flush()
          ack(in)
        ack(in)
        dirs.foreach(d => send(s"D0755 0 $d\n"))
        send(s"C$mode ${file.length} $name\n")
        Files.copy(file.toPath, out)
        send("\u0000")
        dirs.foreach(_ => send("E\n"))

    private def exec[A](command: String)(f: (InputStream, OutputStream) => A): A =
      Using.resource(session.openChannel("exec").asInstanceOf[ChannelExec]): channel =>
        channel.setCommand(command)
        val in = channel.getInputStream
        val out = channel.getOutputStream
        channel.connect(timeoutMillis)
        f(in, out)

    private def ack(in: InputStream): Unit =
      in.read() match
        case 0  => ()
        case -1 => throw new IOException("scp: unexpected end of stream")
        case _  =>
          val msg = new ByteArrayOutputStream()
          Iterator.continually(in.read()).takeWhile(c => c != '\n' && c != -1).foreach(msg.write)
          throw new IOException(s"scp: ${msg.toString(UTF_8)}")

    private def quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
  end Scp

  private def split(path: String): (String, Vector[String], String) =
    val parts = path.split('/').toVector.filter(_.nonEmpty)
    (if path.startsWith("/") then "/" else "", parts.init, parts.last)
end SshPublish
