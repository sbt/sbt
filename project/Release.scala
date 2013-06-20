	import sbt._
	import Keys._
	import Status.{isSnapshot, publishStatus}
	import org.apache.ivy.util.url.CredentialsStore

object Release extends Build
{
	lazy val remoteBase = SettingKey[String]("remote-base")
	lazy val remoteID = SettingKey[String]("remote-id")
	lazy val launcherRemotePath = SettingKey[String]("launcher-remote-path")
	lazy val deployLauncher = TaskKey[Unit]("deploy-launcher", "Upload the launcher to its traditional location for compatibility with existing scripts.")


	val PublishRepoHost = "typesafe.artifactoryonline.com"

	def settings(nonRoots: => Seq[ProjectReference], launcher: ScopedTask[File]): Seq[Setting[_]] =
		if(CredentialsFile.exists) releaseSettings(nonRoots, launcher) else Nil

	def releaseSettings(nonRoots: => Seq[ProjectReference], launcher: ScopedTask[File]): Seq[Setting[_]] = Seq(
		publishTo in ThisBuild <<= publishResolver,
		remoteID <<= publishStatus("typesafe-ivy-" + _),
		credentials in ThisBuild += Credentials(CredentialsFile),
		remoteBase <<= publishStatus( "https://" + PublishRepoHost + "/typesafe/ivy-" + _ ),
		launcherRemotePath <<= (organization, version, moduleName) { (org, v, n) => List(org, n, v, n + ".jar").mkString("/") },
		publish <<= Seq(publish, Release.deployLauncher).dependOn,
 		deployLauncher <<= deployLauncher(launcher)
	)

	def snapshotPattern(version: String) = Resolver.localBasePattern.replaceAll("""\[revision\]""", version)
	def publishResolver: Project.Initialize[Option[Resolver]] = (remoteID, remoteBase) { (id, base) =>
		Some( Resolver.url("publish-" + id, url(base))(Resolver.ivyStylePatterns) )
	}

	lazy val CredentialsFile: File = Path.userHome / ".ivy2" / ".typesafe-credentials"

	// this is no longer strictly necessary, since the launcher is now published as normal
	// however, existing scripts expect the launcher to be in a certain place and normal publishing adds "jars/" 
	// to the published path
	def deployLauncher(launcher: ScopedTask[File]) =
		(launcher, launcherRemotePath, credentials, remoteBase, streams) map { (launchJar, remotePath, creds, base, s) =>
			val (uname, pwd) = getCredentials(creds, s.log)
			val request = dispatch.classic.url(base) / remotePath <<< (launchJar, "binary/octet-stream") as (uname, pwd)
			val http = new dispatch.classic.Http
			try { http(request.as_str) } finally { http.shutdown() }
			()
		}
	def getCredentials(cs: Seq[Credentials], log: Logger): (String, String) =
	{
		Credentials.forHost(cs, PublishRepoHost) match {
			case Some(creds) => (creds.userName, creds.passwd)
			case None => error("No credentials defined for " + PublishRepoHost)
		}
	}
}
