import sbt._, Keys._

import StatusPlugin.autoImport._
import com.typesafe.sbt.site.SitePlugin.autoImport._
import com.typesafe.sbt.site.SiteScaladocPlugin
import SiteScaladocPlugin.autoImport._
import com.typesafe.sbt.sbtghpages.GhpagesPlugin
import GhpagesPlugin.autoImport._
import com.typesafe.sbt.SbtGit, SbtGit.{ git, GitKeys }
import sbtunidoc.{ BaseUnidocPlugin, ScalaUnidocPlugin }
import BaseUnidocPlugin.autoImport._
import ScalaUnidocPlugin.autoImport._

object DocsPlugin extends AutoPlugin {
  override def requires = GhpagesPlugin && ScalaUnidocPlugin && SiteScaladocPlugin

  val siteExcludes = Set(".buildinfo", "objects.inv")
  def siteInclude(f: File) = !siteExcludes.contains(f.getName)

  override def projectSettings: Seq[Setting[_]] = Def settings (
    siteSubdirName in SiteScaladoc := "api",
    ghPagesSettings,
    unidocProjectFilter in (ScalaUnidoc, unidoc) := {
      inProjects(projectRefs(baseDirectory.value): _*)
    },
    doc in Compile := { (unidoc in Compile).value.head }
  )

  /**
   * This is a list manually constructed from
   * sbt:sbtRoot> projects 
   */
  def projectRefs(base: File): Seq[ProjectReference] = {
    val parent = base.getParentFile
    val lmPath = parent / "librarymanagement"
    val zincPath = parent / "zinc"
    val ioPath = parent / "io"
    Vector(
      LocalProject("actionsProj"),
      LocalProject("collectionProj"),
      LocalProject("commandProj"),
      LocalProject("completeProj"),
      LocalProject("coreMacrosProj"),
      LocalProject("logicProj"),
      LocalProject("mainProj"),
      LocalProject("mainSettingsProj"),
      LocalProject("protocolProj"),
      LocalProject("runProj"),
      LocalProject("sbtProj"),
      LocalProject("scriptedPluginProj"),
      LocalProject("scriptedSbtProj"),
      LocalProject("stdTaskProj"),
      LocalProject("taskProj"),
      LocalProject("testAgentProj"),
      LocalProject("utilCache"),
      LocalProject("utilControl"),
      LocalProject("utilInterface"),
      LocalProject("utilLogging"),
      LocalProject("utilPosition"),
      LocalProject("utilRelation"),
      LocalProject("utilScripted"),
      LocalProject("utilTracking"),

    ) ++
    Vector(
      ProjectRef(lmPath, "lmCore"),
      ProjectRef(lmPath, "lmIvy"),
    ) ++
    Vector(
      // skipping some of the internal subprojects here
      ProjectRef(zincPath, "compilerBridge"),
      ProjectRef(zincPath, "compilerInterface"),
      ProjectRef(zincPath, "zinc"),
      ProjectRef(zincPath, "zincApiInfo"),
      ProjectRef(zincPath, "zincClassfile"),
      ProjectRef(zincPath, "zincClasspath"),
      ProjectRef(zincPath, "zincCompile"),
      ProjectRef(zincPath, "zincCompileCore"),
      ProjectRef(zincPath, "zincCore"),
      ProjectRef(zincPath, "zincPersist"),
    ) ++
    Vector(
      ProjectRef(ioPath, "io"),
    )
  }

  def ghPagesSettings = Def settings (
    git.remoteRepo := "git@github.com:sbt/sbt.github.com.git",
    localRepoDirectory,
    ghpagesSynchLocal := synchLocalImpl.value,
    ghpagesBranch := "master"
  )

  def localRepoDirectory = ghpagesRepository := {
    // distinguish between building to update the site or not so that CI jobs
    //  that don't commit+publish don't leave uncommitted changes in the working directory
    val status = if (isSnapshot.value) "snapshot" else "public"
    Path.userHome / ".sbt" / "ghpages" / status / organization.value / name.value
  }

  def synchLocalImpl = Def task {
    val repo = ghpagesUpdatedRepository.value
    val versioned = repo / version.value
    IO.delete(versioned / "api")
    val mappings = ghpagesPrivateMappings.value
    val toCopy = for ((file, target) <- mappings if siteInclude(file))
      yield (file, versioned / target)
    IO.copy(toCopy)
    repo
  }
}
