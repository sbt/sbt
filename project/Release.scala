	import sbt._
	import Keys._
	import Status.{isSnapshot, publishStatus}
	import org.apache.ivy.util.url.CredentialsStore

object Release extends Build
{
	lazy val publishRelease = TaskKey[Unit]("publish-release")
	lazy val publishAllArtifacts = TaskKey[Unit]("publish-all-artifacts")
	lazy val launcherRemotePath = SettingKey[String]("launcher-remote-path")
	lazy val remoteBase = SettingKey[String]("remote-base")
	lazy val remoteID = SettingKey[String]("remote-id")
	lazy val publishLauncher = TaskKey[String]("publish-launcher")
	lazy val fullRelease = TaskKey[Unit]("full-release")
	lazy val prerelease = TaskKey[Unit]("prerelease")

	lazy val wikiRepository = SettingKey[File]("wiki-repository")
	lazy val pagesRepository = SettingKey[File]("pages-repository")
	lazy val updatedPagesRepository = TaskKey[File]("updated-pages-repository")
	lazy val updatedWikiRepository = TaskKey[File]("updated-wiki-repository")
	lazy val copyAPIDoc = TaskKey[File]("copy-api-doc")
	lazy val pushAPIDoc = TaskKey[Unit]("push-api-doc")
	lazy val pushWiki = TaskKey[Unit]("push-wiki")
	lazy val pushMain = TaskKey[Unit]("push-main")
	lazy val sbtRemoteRepo = SettingKey[String]("sbt-remote-repo")
	lazy val wikiRemoteRepo = SettingKey[String]("wiki-remote-repo")

	def settings(nonRoots: => Seq[ProjectReference], launcher: ScopedTask[File]): Seq[Setting[_]] =
		(if(CredentialsFile.exists) releaseSettings(nonRoots, launcher) else Nil) ++
		(if(file(".release.sbt") exists) fullReleaseSettings else Nil)

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
	def fullReleaseSettings: Seq[Setting[_]] = Seq(
		pushAPIDoc <<= pushAPIDoc0,
		copyAPIDoc <<= copyAPIDoc0,
		pushWiki <<= pushWiki0,
		pushMain <<= pushMain0,
		prerelease := println(Prerelease),
		fullRelease <<= fullRelease0,
		sbtRemoteRepo := "git@github.com:harrah/xsbt.git",
		wikiRemoteRepo := "git@github.com:harrah/xsbt.wiki.git",
		updatedPagesRepository <<= updatedRepo(pagesRepository, sbtRemoteRepo, Some("gh-pages")),
		updatedWikiRepository <<= updatedRepo(wikiRepository, wikiRemoteRepo, None)		
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
		val Some(creds) = Credentials.forHost(cs, "typesafe.artifactoryonline.com")
		(creds.userName, creds.passwd)
	}
	def snapshotPattern(version: String) = Resolver.localBasePattern.replaceAll("""\[revision\]""", version)
	def publishResolver: Project.Initialize[Option[Resolver]] = (remoteID, remoteBase) { (id, base) =>
		Some( Resolver.url(id, url(base))(Resolver.ivyStylePatterns) )
	}

	def updatedRepo(repo: SettingKey[File], remote: SettingKey[String], branch: Option[String]) =
		(repo, remote, streams) map { (local, uri, s) => updated(remote = uri, cwd = local, branch = branch, log = s.log); local }

	def copyAPIDoc0 = (updatedPagesRepository, doc, Sxr.sxr, streams) map { (repo, newAPI, newSXR, s) =>
		git("rm", "-r", "latest")(repo, s.log)
		IO.copyDirectory(newAPI, repo / "latest" / "api")
		IO.copyDirectory(newSXR, repo / "latest" / "sxr")
		repo
	}
	def fullRelease0 = Seq(pushWiki, pushMain, pushAPIDoc, publishRelease).dependOn
	def pushMain0 = (baseDirectory, version, streams) map { (repo, v, s) => commitAndPush(v, tag = Some("v" + v))(repo, s.log) }
	def pushWiki0 = (wikiRepository, streams) map { (repo, s) => commitAndPush("updated for release")(repo, s.log) }
	def pushAPIDoc0 = (copyAPIDoc, streams) map { (repo, s) => commitAndPush("updated api and sxr documentation")(repo, s.log) }
	def commitAndPush(msg: String, tag: Option[String] = None)(repo: File, log: Logger)
	{
		git("add", ".")(repo, log)
		git("commit", "-m", msg, "--allow-empty")(repo, log)
		for(tagString <- tag) git("tag", tagString)(repo, log)
		push(repo, log)
	}
	def push(cwd: File, log: Logger) = git("push", "--tags", "-n")(cwd, log)
	def pull(cwd: File, log: Logger) = git("pull")(cwd, log)
	def updated(remote: String, branch: Option[String], cwd: File, log: Logger): Unit =
		if(cwd.exists)
			pull(cwd, log)
		else
			branch match {
				case None => git("clone", remote, ".")(cwd, log)
				case Some(b) => git("clone", "-b", b, remote, ".")(cwd, log)
			}

	def git(args: String*)(cwd: File, log: Logger): Unit =
	{
		IO.createDirectory(cwd)
		val full = "git" +: args
		log.info(cwd + "$ " + full.mkString(" "))
		val code = Process(full, cwd) ! log
		if(code != 0) error("Nonzero exit code for git " + args.take(1).mkString + ": " + code)
	}

	final val BinaryType = "binary/octet-stream"
	final val RemoteHost = "typesafe.artifactoryonline.com"
	final val RemoteRealm = "Artifactory Realm"
	final val LaunchJarName = "sbt-launch"
	lazy val CredentialsFile: File = Path.userHome / ".ivy2" / ".typesafe-credentials"

	def Prerelease = """
Before running full-release, the following should be done manually from the root 'xsbt' project:
1. Ensure all code is committed and the working directory is completely clean.  'git status' should show no untracked files.
2. 'test'
3. 'scripted'
4. Set the release version in README, build definition, and in src/main/conscript configurations.
5. Run 'show updated-wiki-repository'.  Update versions, documentation for release in displayed directory.
6. Add notes/<version>.markdown (pending)
7. 'preview-notes' (pending)
"""
}
