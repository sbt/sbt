import sbt._
import Keys._
import StatusPlugin.autoImport._
import com.typesafe.sbt.{ SbtGhPages, SbtGit, SbtSite, site => sbtsite }
import SbtSite.{ site, SiteKeys }
import SbtGhPages.{ ghpages, GhPagesKeys => ghkeys }
import SbtGit.{ git, GitKeys }
import sbtsite.SphinxSupport
import SiteKeys.{ makeSite, siteMappings }
import Sxr.sxr
import SiteMap.Entry

object Docs {
  val siteExcludes = Set(".buildinfo", "objects.inv")
  def siteInclude(f: File) = !siteExcludes.contains(f.getName)

  def settings: Seq[Setting[_]] =
    site.settings ++
      site.includeScaladoc("api") ++
      siteIncludeSxr("sxr") ++
      ghPagesSettings

  def ghPagesSettings = ghpages.settings ++ Seq(
    git.remoteRepo := "git@github.com:sbt/sbt.github.com.git",
    localRepoDirectory,
    ghkeys.synchLocal <<= synchLocalImpl,
    GitKeys.gitBranch in ghkeys.updatedRepository := Some("master")
  )

  def localRepoDirectory = ghkeys.repository := {
    // distinguish between building to update the site or not so that CI jobs 
    //  that don't commit+publish don't leave uncommitted changes in the working directory
    val status = if (isSnapshot.value) "snapshot" else "public"
    Path.userHome / ".sbt" / "ghpages" / status / organization.value / name.value
  }

  def siteIncludeSxr(prefix: String) = Seq(
    mappings in sxr <<= sxr.map(dir => Path.allSubpaths(dir).toSeq)
  ) ++ site.addMappingsToSiteDir(mappings in sxr, prefix)

  def synchLocalImpl = (ghkeys.privateMappings, ghkeys.updatedRepository, version, streams) map {
    (mappings, repo, v, s) =>
      val versioned = repo / v
      IO.delete(versioned / "sxr")
      IO.delete(versioned / "api")
      val toCopy = for ((file, target) <- mappings if siteInclude(file)) yield (file, versioned / target)
      IO.copy(toCopy)
      repo
  }
}
