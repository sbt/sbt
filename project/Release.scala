import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

import com.typesafe.sbt.pgp.PgpKeys
import sbt._
import sbt.Keys._
import sbt.Package.ManifestAttributes
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scala.io._

object Release {

  implicit final class StateOps(val state: State) extends AnyVal {
    def vcs: sbtrelease.Vcs =
      Project.extract(state).get(releaseVcs).getOrElse {
        sys.error("VCS not set")
      }
  }

  val checkTravisStatus = ReleaseStep { state =>

    val currentHash = state.vcs.currentHash

    val build = Travis.builds("coursier/coursier", state.log)
      .find { build =>
        build.job_ids.headOption.exists { id =>
          Travis.job(id, state.log).commit.sha == currentHash
        }
      }
      .getOrElse {
        sys.error(s"Status for commit $currentHash not found on Travis")
      }

    state.log.info(s"Found build ${build.id.value} for commit $currentHash, state: ${build.state}")

    build.state match {
      case "passed" =>
      case _ =>
        sys.error(s"Build for $currentHash in state ${build.state}")
    }

    state
  }

  val checkAppveyorStatus = ReleaseStep { state =>

    val currentHash = state.vcs.currentHash

    val build = Appveyor.branchLastBuild("alexarchambault/coursier-a7n6k", "master", state.log)

    state.log.info(s"Found last build ${build.buildId} for branch master, status: ${build.status}")

    if (build.commitId != currentHash)
      sys.error(s"Last master Appveyor build corresponds to commit ${build.commitId}, expected $currentHash")

    if (build.status != "success")
      sys.error(s"Last master Appveyor build status: ${build.status}")

    state
  }

  val previousReleaseVersion = AttributeKey[String]("previousReleaseVersion")
  val initialVersion = AttributeKey[String]("initialVersion")

  val saveInitialVersion = ReleaseStep { state =>
    val currentVer = Project.extract(state).get(version)
    state.put(initialVersion, currentVer)
  }

  def versionChanges(state: State): Boolean = {

    val initialVer = state.get(initialVersion).getOrElse {
      sys.error(s"${initialVersion.label} key not set")
    }
    val (_, nextVer) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    initialVer == nextVer
  }


  val updateVersionPattern = "(?m)^VERSION=.*$".r
  def updateVersionInScript(file: File, newVersion: String): Unit = {
    val content = Source.fromFile(file)(Codec.UTF8).mkString

    updateVersionPattern.findAllIn(content).toVector match {
      case Seq() => sys.error(s"Found no matches in $file")
      case Seq(_) =>
      case _ => sys.error(s"Found too many matches in $file")
    }

    val newContent = updateVersionPattern.replaceAllIn(content, "VERSION=" + newVersion)
    Files.write(file.toPath, newContent.getBytes(StandardCharsets.UTF_8))
  }


  val updateScripts = ReleaseStep { state =>

    val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    val scriptsDir = Project.extract(state).get(baseDirectory.in(ThisBuild)) / "scripts"
    val scriptFiles = Seq(
      scriptsDir / "generate-launcher.sh",
      scriptsDir / "generate-sbt-launcher.sh"
    )

    val vcs = state.vcs

    for (f <- scriptFiles) {
      updateVersionInScript(f, releaseVer)
      vcs.add(f.getAbsolutePath).!!(state.log)
    }

    state
  }

  val updateLaunchers = ReleaseStep { state =>

    val baseDir = Project.extract(state).get(baseDirectory.in(ThisBuild))
    val scriptsDir = baseDir / "scripts"
    val scriptFiles = Seq(
      (scriptsDir / "generate-launcher.sh") -> (baseDir / "coursier"),
      (scriptsDir / "generate-sbt-launcher.sh") -> (baseDir / "csbt")
    )

    val vcs = state.vcs

    for ((f, output) <- scriptFiles) {
      sbt.Process(Seq(f.getAbsolutePath, "-f")).!!(state.log)
      vcs.add(output.getAbsolutePath).!!(state.log)
    }

    state
  }

  val savePreviousReleaseVersion = ReleaseStep { state =>

    val cmd = Seq(state.vcs.commandName, "tag", "-l")

    val tag = scala.sys.process.Process(cmd)
      .!!
      .linesIterator
      .toVector
      .lastOption
      .getOrElse {
        sys.error(s"Found no tags when running ${cmd.mkString(" ")}")
      }

    val ver =
      if (tag.startsWith("v"))
        tag.stripPrefix("v")
      else
        sys.error(s"Last tag '$tag' doesn't start with 'v'")

    state.put(previousReleaseVersion, ver)
  }

  val updateTutReadme = ReleaseStep { state =>

    val previousVer = state.get(previousReleaseVersion).getOrElse {
      sys.error(s"${previousReleaseVersion.label} key not set")
    }
    val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    val readmeFile = Project.extract(state).get(baseDirectory.in(ThisBuild)) / "doc" / "README.md"
    val pattern = Pattern.quote(previousVer).r

    val content = Source.fromFile(readmeFile)(Codec.UTF8).mkString
    val newContent = pattern.replaceAllIn(content, releaseVer)
    Files.write(readmeFile.toPath, newContent.getBytes(StandardCharsets.UTF_8))

    state.vcs.add(readmeFile.getAbsolutePath).!!(state.log)

    state
  }

  val stageReadme = ReleaseStep { state =>

    val baseDir = Project.extract(state).get(baseDirectory.in(ThisBuild))
    val processedReadmeFile = baseDir / "README.md"

    state.vcs.add(processedReadmeFile.getAbsolutePath).!!(state.log)

    state
  }


  val coursierVersionPattern = s"(?m)^${Pattern.quote("def coursierVersion = \"")}[^${'"'}]*${Pattern.quote("\"")}$$".r

  val updatePluginsSbt = ReleaseStep { state =>

    val vcs = state.vcs

    val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    val baseDir = Project.extract(state).get(baseDirectory.in(ThisBuild))
    val pluginsSbtFile = baseDir / "project" / "plugins.sbt"
    val projectPluginsSbtFile = baseDir / "project" / "project" / "plugins.sbt"
    val projectProjectPluginsSbtFile = baseDir / "project" / "project" / "project" / "plugins.sbt"

    val files = Seq(
      pluginsSbtFile,
      projectPluginsSbtFile,
      projectProjectPluginsSbtFile
    )

    for (f <- files) {
      val content = Source.fromFile(f)(Codec.UTF8).mkString

      coursierVersionPattern.findAllIn(content).toVector match {
        case Seq() => sys.error(s"Found no matches in $f")
        case Seq(_) =>
        case _ => sys.error(s"Found too many matches in $f")
      }

      val newContent = coursierVersionPattern.replaceAllIn(content, "def coursierVersion = \"" + releaseVer + "\"")
      Files.write(f.toPath, newContent.getBytes(StandardCharsets.UTF_8))
      vcs.add(f.getAbsolutePath).!!(state.log)
    }

    state
  }

  val commitUpdates = ReleaseStep(
    action = { state =>

      val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
        sys.error(s"${ReleaseKeys.versions.label} key not set")
      }

      state.vcs.commit(s"Updates for $releaseVer", sign = true).!(state.log)

      state
    },
    check = { state =>

      val vcs = state.vcs

      if (vcs.hasModifiedFiles)
        sys.error("Aborting release: unstaged modified files")

      if (vcs.hasUntrackedFiles && !Project.extract(state).get(releaseIgnoreUntrackedFiles))
        sys.error(
          "Aborting release: untracked files. Remove them or specify 'releaseIgnoreUntrackedFiles := true' in settings"
        )

      state
    }
  )

  val addReleaseToManifest = ReleaseStep { state =>

    val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    val tag = "v" + releaseVer

    reapply(
      Seq(
        // Tag will be one commit after the one with which the publish was really made, because of the commit
        // updating scripts / plugins.
        packageOptions += ManifestAttributes("Vcs-Release-Tag" -> tag)
      ),
      state
    )
  }

  // tagRelease from sbt-release seem to use the next version (snapshot one typically) rather than the released one :/
  val reallyTagRelease = ReleaseStep { state =>

    val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    val sign = Project.extract(state).get(releaseVcsSign)

    val tag = "v" + releaseVer

    state.vcs.tag(tag, s"Releasing $tag", sign).!(state.log)

    state
  }


  val settings = Seq(
    releaseProcess := Seq[ReleaseStep](
      checkTravisStatus,
      checkAppveyorStatus,
      savePreviousReleaseVersion,
      checkSnapshotDependencies,
      inquireVersions,
      saveInitialVersion,
      setReleaseVersion,
      commitReleaseVersion,
      addReleaseToManifest,
      publishArtifacts,
      releaseStepCommand("sonatypeRelease"),
      updateScripts,
      updateLaunchers,
      updateTutReadme,
      releaseStepCommand("tut"),
      stageReadme,
      updatePluginsSbt,
      commitUpdates,
      reallyTagRelease,
      setNextVersion,
      commitNextVersion,
      ReleaseStep(_.reload),
      pushChanges
    ),
    releasePublishArtifactsAction := PgpKeys.publishSigned.value
  )

}
