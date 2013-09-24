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
	val latestRelease = SettingKey[Boolean]("latest-release")

	val siteExcludes = Set(".buildinfo", "objects.inv")
	def siteInclude(f: File) = !siteExcludes.contains(f.getName)

	val SnapshotPath = "snapshot"
	val ReleasePath = "release"
	val DocsPath = "docs"
	val IndexHtml = "index.html"
	val HomeHtml = "home.html"
	val VersionPattern = """(\d+)\.(\d+)\.(\d+)(-.+)?""".r.pattern

	def settings: Seq[Setting[_]] =
		site.settings ++
		site.sphinxSupport(DocsPath) ++
		site.includeScaladoc("api") ++
		siteIncludeSxr("sxr") ++
		ghPagesSettings ++
		Seq(
			SphinxSupport.sphinxEnv in SphinxSupport.Sphinx <<= sphinxEnvironmentVariables,
			SphinxSupport.sphinxIncremental in SphinxSupport.Sphinx := true,
			// TODO: set to true with newer sphinx plugin release
			SphinxSupport.enableOutput in SphinxSupport.generatePdf := false
		)

	def ghPagesSettings = ghpages.settings ++ Seq(
		git.remoteRepo := "git@github.com:sbt/sbt.github.com.git",
		localRepoDirectory,
		ghkeys.synchLocal <<= synchLocalImpl,
		cnameFile <<= (sourceDirectory in SphinxSupport.Sphinx) / "CNAME",
		latestRelease in ThisBuild := false,
		commands += setLatestRelease,
		GitKeys.gitBranch in ghkeys.updatedRepository := Some("master")
	)

	def localRepoDirectory = ghkeys.repository := {
		// distinguish between building to update the site or not so that CI jobs 
		//  that don't commit+publish don't leave uncommitted changes in the working directory
		val status = if(isSnapshot.value) "snapshot" else "public"
		Path.userHome / ".sbt" / "ghpages" / status / organization.value / name.value
	}

	def siteIncludeSxr(prefix: String) = Seq(
		mappings in sxr <<= sxr.map(dir => Path.allSubpaths(dir).toSeq),
		site.addMappingsToSiteDir(mappings in sxr, prefix)
	)

	def sphinxEnvironmentVariables = (scalaVersion, version) map { (scalaV, sbtV) =>
		// major.minor
		def release(v: String): String = CrossVersion.partialVersion(v) match {
			case Some((major,minor)) => major + "." + minor
			case None => v
		}
		Map[String,String](
			"sbt.full.version" -> sbtV,
			"sbt.partial.version" -> release(sbtV),
			"sbt.site.version" -> sbtV.takeWhile(_ != '-'),
			"sbt.binary.version" -> CrossVersion.binarySbtVersion(sbtV),
			"scala.full.version" -> scalaV,
			"scala.partial.version" -> release(scalaV),
			"scala.binary.version" -> CrossVersion.binaryScalaVersion(scalaV)
		)
	}

	def synchLocalImpl = (ghkeys.privateMappings, ghkeys.updatedRepository, version, isSnapshot, latestRelease, streams, cnameFile) map {
		(mappings, repo, v, snap, latest, s, cname) =>
		val versioned = repo / v
		IO.delete(versioned)
		val toCopy = for( (file, target) <- mappings if siteInclude(file) ) yield (file, versioned / target)
		IO.copy(toCopy)
		IO.copyFile(cname, repo / cname.getName)
		IO.touch(repo / ".nojekyll")
		IO.write(repo / "versions.js", versionsJs(sortVersions(collectVersions(repo))))
		if(!snap && latest)
			RootIndex(versioned / DocsPath / "home.html", repo / IndexHtml)
		if(snap || latest)
			linkSite(repo, v, if(snap) SnapshotPath else ReleasePath, s.log)
		s.log.info("Copied site to " + versioned)
		repo
	}
	def versionsJs(vs: Seq[String]): String = "var availableDocumentationVersions = " + vs.mkString("['", "', '", "']")
	// names of all directories that are explicit versions
	def collectVersions(base: File): Seq[String] = (base * versionFilter).get.map(_.getName)
	def sortVersions(vs: Seq[String]): Seq[String] = vs.sortBy(versionComponents).reverse
	def versionComponents(v: String): Option[(Int,Int,Int,Option[String])] = {
		val m = VersionPattern.matcher(v)
		if(m.matches())
			Some( (m.group(1).toInt, m.group(2).toInt, m.group(3).toInt, Option(m.group(4))) )
		else
			None
	}
	def versionFilter = new PatternFilter(VersionPattern) && DirectoryFilter

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

	def setLatestRelease = Command.command("latest-release-docs") { state =>
		Project.extract(state).append((latestRelease in ThisBuild := true) :: Nil, state)
	}

}
object RootIndex
{
		import Docs._
		import org.jsoup._

	def apply(versionIndex: File, to: File)
	{
		val doc = Jsoup.parse(versionIndex, "UTF-8")
		rewriteLinks(doc)
		removeSearch(doc)
		IO.write(to, doc.outerHtml)
	}
	def retargetIndexLink(original: String): String =
		if(isAbsolute(original) || original.startsWith("#"))
			original
		else
			ReleasePath + "/docs/" + original

	def isAbsolute(s: String): Boolean = (new java.net.URI(s)).isAbsolute

	def rewriteLinks(doc: nodes.Document)
	{
		rewriteLinks(doc, "*", "href")
		rewriteLinks(doc, "script", "src")
	}
	def rewriteLinks(doc: nodes.Document, elemName: String, attrName: String): Unit =
		for(elem <- select(doc, elemName + "[" + attrName + "]"))
			elem.attr(attrName, retargetIndexLink(elem.attr(attrName)))

	def removeSearch(doc: nodes.Document): Unit =
		doc.select(".search").remove()

	def select(doc: nodes.Document, s: String) =
	{
		import collection.JavaConverters._
		doc.select(s).iterator.asScala
	}
}