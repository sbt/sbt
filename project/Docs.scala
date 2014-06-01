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
	import SiteMap.Entry

object Docs
{
	val latestRelease = SettingKey[Boolean]("latest-release")

	val siteExcludes = Set(".buildinfo", "objects.inv")
	def siteInclude(f: File) = !siteExcludes.contains(f.getName)
	def siteSourceBase(siteSourceVersion: String) = s"https://github.com/sbt/sbt/raw/$siteSourceVersion/src/sphinx/"
	val sbtSiteBase = uri("http://www.scala-sbt.org/")

	val SnapshotPath = "snapshot"
	val ReleasePath = "release"
	val DocsPath = "docs"
	val IndexHtml = "index.html"
	val HomeHtml = "home.html"
	val VersionPattern = """(\d+)\.(\d+)\.(\d+)(-.+)?""".r.pattern

	// There shouldn't be any reason to publish docs from 0.13.5 any more.
	// Uncomment the below after it merges to 0.13 for 0.13.6 API and SXR.
	def settings: Seq[Setting[_]] = Nil
		// site.settings ++
		// site.includeScaladoc("api") ++
		// siteIncludeSxr("sxr") ++
		// ghPagesSettings
	
	def ghPagesSettings = ghpages.settings ++ Seq(
		git.remoteRepo := "git@github.com:sbt/sbt.github.com.git",
		localRepoDirectory,
		ghkeys.synchLocal <<= synchLocalImpl,
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

	def synchLocalImpl = (ghkeys.privateMappings, ghkeys.updatedRepository, version, isSnapshot, latestRelease, streams) map {
		(mappings, repo, v, snap, latest, s) =>
		val versioned = repo / v
		IO.delete(versioned)
		val toCopy = for( (file, target) <- mappings if siteInclude(file) ) yield (file, versioned / target)
		IO.copy(toCopy)

		// IO.write(repo / "versions.js", versionsJs(sortVersions(collectVersions(repo))))
		s.log.info("Copied site to " + versioned)
		/*
		if(latest) {
			val (index, siteMaps) = SiteMap.generate(repo, sbtSiteBase, gzip=true, siteEntry(v), s.log)
			s.log.info(s"Generated site map index: $index")
			s.log.debug(s"Generated site maps: ${siteMaps.mkString("\n\t", "\n\t", "")}")
		}
		*/

		repo
	}
	def siteEntry(CurrentVersion: String)(file: File, relPath: String): Option[Entry] =
	{
		val apiOrSxr = """([^/]+)/(api|sxr)/.*""".r
		val docs = """([^/]+)/docs/.*""".r
		val old077 = """0\.7\.7/.*""".r 
		val manualRedirects = """[^/]+\.html""".r
		val snapshot = """(.+-SNAPSHOT|snapshot)/.+/.*""".r
			// highest priority is the home page
			// X/docs/ are higher priority than X/(api|sxr)/
			// release/ is slighty higher priority than <releaseVersion>/
			// non-current releases are low priority
			// 0.7.7 documentation is very low priority
			// snapshots docs are very low priority
			// the manual redirects from the old version of the site have no priority at all
		relPath match {
			case "index.html" => Some(Entry("weekly", 1.0))
			case docs(ReleasePath) => Some( Entry("weekly", 0.9) )
			case docs(CurrentVersion) => Some( Entry("weekly", 0.8) )
			case apiOrSxr(ReleasePath, _) => Some( Entry("weekly", 0.6) )
			case apiOrSxr(CurrentVersion, _) => Some( Entry("weekly", 0.5) )
			case snapshot(_) => Some( Entry("weekly", 0.02) )
			case old077() =>  Some( Entry("never", 0.01) )
			case docs(_) => Some( Entry("never", 0.2) )
			case apiOrSxr(_, _) => Some( Entry("never", 0.1) )
			case x => Some( Entry("never", 0.0) )
		}
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