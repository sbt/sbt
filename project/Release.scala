	import sbt._
	import Keys._
	import Status.{isSnapshot, publishStatus}
	import org.apache.ivy.util.url.CredentialsStore

object Release extends Plugin
{
	lazy val publishRelease = TaskKey[Unit]("publish-release")
	lazy val publishAllArtifacts = TaskKey[Unit]("publish-all-artifacts")
	lazy val launcherRemotePath = SettingKey[String]("launcher-remote-path")
	lazy val remoteBase = SettingKey[String]("remote-base")
	lazy val remoteID = SettingKey[String]("remote-id")
	lazy val publishLauncher = TaskKey[String]("publish-launcher")

	def settings(nonRoots: => Seq[ProjectReference], launcher: ScopedTask[File]): Seq[Setting[_]] =
		if(CredentialsFile.exists) releaseSettings(nonRoots, launcher) else Nil

	def releaseSettings(nonRoots: => Seq[ProjectReference], launcher: ScopedTask[File]): Seq[Setting[_]] = Seq(
		publishTo in ThisBuild <<= publishResolver,
		remoteID <<= publishStatus("typesafe-ivy-" + _),
		credentials in Global += Credentials(CredentialsFile),
		remoteBase <<= publishStatus( "https://typesafe.artifactoryonline.com/typesafe/ivy-" + _ ),
		publishAllArtifacts <<= Util.inAll(nonRoots, publish.task),
		publishLauncher <<= deployLauncher(launcher),
		publishRelease <<= Seq(publishLauncher, publishAllArtifacts).dependOn,
		launcherRemotePath <<= (organization, version) { (org, v) => List(org, LaunchJarName, v, LaunchJarName + ".jar").mkString("/") }
	)
	def deployLauncher(launcher: ScopedTask[File]) =
		(launcher, launcherRemotePath, credentials, remoteBase, streams) map { (launchJar, remotePath, creds, base, s) =>
			val (uname, pwd) = getCredentials(creds, s.log)
			val request = dispatch.url(base) / remotePath <<< (launchJar, BinaryType) as (uname, pwd)
			val http = new dispatch.Http
			try { http(request.as_str) } finally { http.shutdown() }
		}
	def getCredentials(cs: Seq[Credentials], log: Logger): (String, String) =
	{
		// 0.10.1
		// val creds = Credentials.forHost(cs, "typesafe.artifactoryonline.com")
		// (creds.userName, creds.passwd)

		Credentials.register(cs, log)
		val creds = CredentialsStore.INSTANCE.getCredentials(RemoteRealm, RemoteHost)
		(creds.getUserName, creds.getPasswd)
	}
	def snapshotPattern(version: String) = Resolver.localBasePattern.replaceAll("""\[revision\]""", version)
	def publishResolver: Project.Initialize[Option[Resolver]] = (remoteID, remoteBase) { (id, base) =>
		Some( Resolver.url(id, url(base))(Resolver.ivyStylePatterns) )
	}

	final val BinaryType = "binary/octet-stream"
	final val RemoteHost = "typesafe.artifactoryonline.com"
	final val RemoteRealm = "Artifactory Realm"
	final val LaunchJarName = "sbt-launch"
	lazy val CredentialsFile: File = Path.userHome / ".ivy2" / ".typesafe-credentials"
}