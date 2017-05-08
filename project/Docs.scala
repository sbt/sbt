import sbt._
import Keys._
import StatusPlugin.autoImport._
import com.typesafe.sbt.site.SitePlugin.autoImport._
import com.typesafe.sbt.site.SiteScaladocPlugin.autoImport._
import com.typesafe.sbt.{ SbtGhPages, SbtGit }
import SbtGhPages.{ ghpages, GhPagesKeys => ghkeys }
import SbtGit.{ git, GitKeys }
import Sxr.{ sxr, sxrConf }
import SiteMap.Entry

object Docs {
  val siteExcludes = Set(".buildinfo", "objects.inv")
  def siteInclude(f: File) = !siteExcludes.contains(f.getName)

  def settings: Seq[Setting[_]] = Def settings (
    siteSubdirName in SiteScaladoc := "api",
    siteIncludeSxr("sxr"),
    ghPagesSettings
  )

  def ghPagesSettings = ghpages.settings ++ Seq(
    git.remoteRepo := "git@github.com:sbt/sbt.github.com.git",
    localRepoDirectory,
    ghkeys.synchLocal := synchLocalImpl.value,
    GitKeys.gitBranch in ghkeys.updatedRepository := Some("master")
  )

  def localRepoDirectory = ghkeys.repository := {
    // distinguish between building to update the site or not so that CI jobs
    //  that don't commit+publish don't leave uncommitted changes in the working directory
    val status = if (isSnapshot.value) "snapshot" else "public"
    Path.userHome / ".sbt" / "ghpages" / status / organization.value / name.value
  }

  def siteIncludeSxr(prefix: String) = Def settings (
    mappings in sxr := Path.allSubpaths(sxr.value).toSeq,
    siteSubdirName in sxrConf := prefix,
    addMappingsToSiteDir(mappings in sxr, siteSubdirName in sxrConf)
  )

  def synchLocalImpl = Def task {
    val repo = ghkeys.updatedRepository.value
    val versioned = repo / version.value
    IO.delete(versioned / "sxr")
    IO.delete(versioned / "api")
    val mappings = ghkeys.privateMappings.value
    val toCopy = for ((file, target) <- mappings if siteInclude(file))
      yield (file, versioned / target)
    IO.copy(toCopy)
    repo
  }
}
