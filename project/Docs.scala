	import sbt._
	import Keys._
	import Status.{isSnapshot, publishStatus}
	import com.typesafe.sbt.{SbtGhPages,SbtGit,SbtSite,site=>sbtsite}
	import SbtSite.{site, SiteKeys}
	import SbtGhPages.{ghpages, GhPagesKeys => ghkeys}
	import SbtGit.{git, GitKeys}
	import sbtsite.SphinxSupport
	import SiteKeys.{makeSite,siteMappings}
	import Sxr.sxr

object Docs
{
	val cnameFile = SettingKey[File]("cname-file", "Location of the CNAME file for the website.")

	def settings: Seq[Setting[_]] =
		site.settings ++
		site.sphinxSupport("docs") ++
		site.includeScaladoc("api") ++
		siteIncludeSxr("sxr") ++
		ghPagesSettings

	def ghPagesSettings = ghpages.settings ++ Seq(
		git.remoteRepo := "git@github.com:sbt/sbt.github.com.git",
		ghkeys.synchLocal <<= synchLocalImpl,
		cnameFile <<= (sourceDirectory in SphinxSupport.Sphinx) / "CNAME",
		GitKeys.gitBranch in ghkeys.updatedRepository := Some("master")
	)

	def siteIncludeSxr(prefix: String) = Seq(
		mappings in sxr <<= sxr.map(dir => Path.allSubpaths(dir).toSeq),
		site.addMappingsToSiteDir(mappings in sxr, prefix)
	)

	def synchLocalImpl = (ghkeys.privateMappings, ghkeys.updatedRepository, version, isSnapshot, streams, cnameFile) map { (mappings, repo, v, snap, s, cname) =>
		val versioned = repo / v
		if(snap)
			IO.delete(versioned)
		else if(versioned.exists)
			error("Site for " + v + " already exists: " + versioned.getAbsolutePath)
		IO.copy(mappings map { case (file, target) => (file, versioned / target) })
		IO.copyFile(cname, repo / cname.getName)
		IO.touch(repo / ".nojekyll")
		linkSite(repo, v, if(snap) "snapshot" else "release", s.log)
		s.log.info("Copied site to " + versioned)
		repo
	}

	def linkSite(base: File, to: String, from: String, log: Logger) {
		val current = base / to
		assert(current.isDirectory, "Versioned site not present at " + current.getAbsolutePath)
		val symlinkDir = base / from
		symlinkDir.delete()
		symlink(path = to, file = symlinkDir, log = log)
	}

	// TODO: platform independence/use symlink from Java 7
	def symlink(path: String, file: File, log: Logger): Unit =
		"ln" :: "-s" :: path :: file.getAbsolutePath :: Nil ! log match {
			case 0 => ()
			case code => error("Could not create symbolic link '" + file.getAbsolutePath + "' with path " + path)
		}
}