import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.regex.Pattern

import com.typesafe.sbt.pgp.PgpKeys
import sbt._
import sbt.Keys._
import sbt.Package.ManifestAttributes
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scala.io._
import scala.sys.process.ProcessLogger

object Release {

  // adapted from https://github.com/sbt/sbt-release/blob/eccd4cb7b9818b2a731380fe31c399dc9cb7375b/src/main/scala/ReleaseExtra.scala#L239-L243
  private def toProcessLogger(st: State): ProcessLogger =
    new ProcessLogger {
      def err(s: => String) = st.log.info(s)
      def out(s: => String) = st.log.info(s)
      def buffer[T](f: => T) = st.log.buffer(f)
    }

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

    val log = toProcessLogger(state)

    for (f <- scriptFiles) {
      updateVersionInScript(f, releaseVer)
      vcs.add(f.getAbsolutePath).!!(log)
    }

    state
  }

  val updateLaunchers = ReleaseStep { state =>

    val baseDir = Project.extract(state).get(baseDirectory.in(ThisBuild))
    val scriptsDir = baseDir / "scripts"
    val scriptFiles = Seq(
      (scriptsDir / "generate-launcher.sh") -> (baseDir / "coursier")
    )

    val vcs = state.vcs
    val log = toProcessLogger(state)

    for ((f, output) <- scriptFiles) {
      sys.process.Process(Seq(f.getAbsolutePath, "-f")).!!(log)
      vcs.add(output.getAbsolutePath).!!(log)
    }

    state
  }

  val savePreviousReleaseVersion = ReleaseStep { state =>

    val cmd = Seq(state.vcs.commandName, "tag", "--sort", "version:refname")

    val tag = scala.sys.process.Process(cmd)
      .!!
      .lines
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

    val log = toProcessLogger(state)

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

    state.vcs.add(readmeFile.getAbsolutePath).!!(log)

    state
  }

  val stageReadme = ReleaseStep { state =>

    val log = toProcessLogger(state)

    val baseDir = Project.extract(state).get(baseDirectory.in(ThisBuild))
    val processedReadmeFile = baseDir / "README.md"

    state.vcs.add(processedReadmeFile.getAbsolutePath).!!(log)

    state
  }


  val coursierVersionPattern = s"(?m)^${Pattern.quote("def coursierVersion0 = \"")}[^${'"'}]*${Pattern.quote("\"")}$$".r

  val updatePluginsSbt = ReleaseStep { state =>

    val vcs = state.vcs
    val log = toProcessLogger(state)

    val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    val baseDir = Project.extract(state).get(baseDirectory.in(ThisBuild))
    val projectProjectPluginsSbtFile = baseDir / "project" / "project" / "project" / "plugins.sbt"

    val files = Seq(
      projectProjectPluginsSbtFile
    )

    for (f <- files) {
      val content = Source.fromFile(f)(Codec.UTF8).mkString

      coursierVersionPattern.findAllIn(content).toVector match {
        case Seq() => sys.error(s"Found no matches in $f")
        case Seq(_) =>
        case _ => sys.error(s"Found too many matches in $f")
      }

      val newContent = coursierVersionPattern.replaceAllIn(content, "def coursierVersion0 = \"" + releaseVer + "\"")
      Files.write(f.toPath, newContent.getBytes(StandardCharsets.UTF_8))
      vcs.add(f.getAbsolutePath).!!(log)
    }

    state
  }

  val mimaVersionsPattern = s"(?m)^(\\s+)${Pattern.quote("\"\" // binary compatibility versions")}$$".r

  val updateMimaVersions = ReleaseStep { state =>

    val vcs = state.vcs
    val log = toProcessLogger(state)

    val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    val baseDir = Project.extract(state).get(baseDirectory.in(ThisBuild))
    val mimaScalaFile = baseDir / "project" / "Mima.scala"

    val content = Source.fromFile(mimaScalaFile)(Codec.UTF8).mkString

    mimaVersionsPattern.findAllIn(content).toVector match {
      case Seq() => sys.error(s"Found no matches in $mimaScalaFile")
      case Seq(_) =>
      case _ => sys.error(s"Found too many matches in $mimaScalaFile")
    }

    val newContent = mimaVersionsPattern.replaceAllIn(
      content,
      m => {
        val indent = m.group(1)
        indent + "\"" + releaseVer + "\",\n" +
          indent + "\"\" // binary compatibility versions"
      }
    )

    Files.write(mimaScalaFile.toPath, newContent.getBytes(StandardCharsets.UTF_8))
    vcs.add(mimaScalaFile.getAbsolutePath).!!(log)

    state
  }

  val updateTestFixture = ReleaseStep(
    action = { state =>

      val initialVer = state.get(initialVersion).getOrElse {
        sys.error(s"${initialVersion.label} key not set")
      }
      val (_, nextVer) = state.get(ReleaseKeys.versions).getOrElse {
        sys.error(s"${ReleaseKeys.versions.label} key not set")
      }

      if (initialVer == nextVer)
        state
      else {
        val vcs = state.vcs
        val log = toProcessLogger(state)

        val originalFile = Paths.get(s"tests/shared/src/test/resources/resolutions/io.get-coursier/coursier_2.11/$initialVer")
        val originalContent = new String(Files.readAllBytes(originalFile), StandardCharsets.UTF_8)
        val destFile = Paths.get(s"tests/shared/src/test/resources/resolutions/io.get-coursier/coursier_2.11/$nextVer")
        val destContent = originalContent.replace(initialVer, nextVer)
        log.out(s"Writing $destFile")
        Files.write(destFile, destContent.getBytes(StandardCharsets.UTF_8))
        vcs.add(destFile.toAbsolutePath.toString).!!(log)
        vcs.cmd("rm", originalFile.toAbsolutePath.toString).!!(log)
        state
      }
    }
  )

  val commitUpdates = ReleaseStep(
    action = { state =>

      val log = toProcessLogger(state)

      val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
        sys.error(s"${ReleaseKeys.versions.label} key not set")
      }

      state.vcs.commit(s"Updates for $releaseVer", sign = true).!(log)

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

    val log = toProcessLogger(state)

    val (releaseVer, _) = state.get(ReleaseKeys.versions).getOrElse {
      sys.error(s"${ReleaseKeys.versions.label} key not set")
    }

    val sign = Project.extract(state).get(releaseVcsSign)

    val tag = "v" + releaseVer

    state.vcs.tag(tag, s"Releasing $tag", sign).!(log)

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
      updateMimaVersions,
      updateTestFixture,
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
