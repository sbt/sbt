/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.BuildLoader

import sbt.io.{ Hash, IO }

import java.io.File
import java.net.URI

import BuildLoader.ResolveInfo
import RichURI.fromURI
import java.util.Locale

import scala.sys.process.{ BasicIO, Process }
import scala.util.control.NonFatal
import sbt.internal.{ BuildDependencies, LoadedBuild, LoadedBuildUnit }
import sbt.util.Logger
import sbt.internal.{ RetrieveUnit, VcsUriFragment }

object Resolvers {

  private[sbt] sealed trait RemoteVcs
  private[sbt] object RemoteVcs {
    case class Git(uri: URI) extends RemoteVcs
    case class Hg(uri: URI) extends RemoteVcs
    case class Svn(uri: URI) extends RemoteVcs
  }

  private val LastUpdatedFileName = ".sbt-last-updated"

  type Resolver = BuildLoader.Resolver

  val local: Resolver = (info: ResolveInfo) => {
    val uri = info.uri
    val from = new File(uri)
    val to = uniqueSubdirectoryFor(uri, in = info.staging)

    if (from.isDirectory) Some { () =>
      if (from.canWrite) from else creates(to) { IO.copyDirectory(from, to) }
    }
    else None
  }

  val remote: Resolver = (info: ResolveInfo) => {
    val url = info.uri.toURL
    val to = uniqueSubdirectoryFor(info.uri, in = info.staging)

    Some { () =>
      creates(to) { IO.unzipURL(url, to); () }
    }
  }

  val subversion: Resolver = (info: ResolveInfo) => {
    val uri = info.uri.withoutMarkerScheme
    val localCopy = uniqueSubdirectoryFor(uri.copy(scheme = "svn"), in = info.staging)
    val from = uri.withoutFragment.toASCIIString
    val to = localCopy.getAbsolutePath

    if (uri.hasFragment) {
      val revision = uri.getFragment
      VcsUriFragment.validate(revision)
      Some { () =>
        creates(localCopy) {
          run(cwd = None, log = None, "svn", "checkout", "-q", "-r", revision, from, to)
        }
      }
    } else
      Some { () =>
        creates(localCopy) {
          run(cwd = None, log = None, "svn", "checkout", "-q", from, to)
        }
      }
  }

  val mercurial: Resolver = (info: ResolveInfo) => {
    val uri = info.uri.withoutMarkerScheme
    val localCopy = uniqueSubdirectoryFor(uri.copy(scheme = "hg"), in = info.staging)
    val from = uri.withoutFragment.toASCIIString

    if (uri.hasFragment) {
      val branch = uri.getFragment
      VcsUriFragment.validate(branch)
      Some { () =>
        creates(localCopy) {
          run(cwd = None, log = None, "hg", "clone", "-q", from, localCopy.getAbsolutePath)
          run(Some(localCopy), log = None, "hg", "checkout", "-q", branch)
        }
      }
    } else
      Some { () =>
        creates(localCopy) {
          run(cwd = None, log = None, "hg", "clone", "-q", from, localCopy.getAbsolutePath)
        }
      }
  }

  val git: Resolver = (info: ResolveInfo) => {
    val uri = info.uri.withoutMarkerScheme
    val localCopy = uniqueSubdirectoryFor(uri.copy(scheme = "git"), in = info.staging)
    val from = uri.withoutFragment.toASCIIString

    if (uri.hasFragment) {
      val branch = uri.getFragment
      VcsUriFragment.validate(branch)
      Some { () =>
        creates(localCopy) {
          run(cwd = None, log = None, "git", "clone", from, localCopy.getAbsolutePath)
          run(Some(localCopy), log = None, "git", "checkout", "-q", branch)
        }
      }
    } else
      Some { () =>
        creates(localCopy) {
          run(
            cwd = None,
            log = None,
            "git",
            "clone",
            "--depth",
            "1",
            from,
            localCopy.getAbsolutePath
          )
        }
      }
  }

  private def run(cwd: Option[File], log: Option[Logger], command: String*): Unit = {
    val process = Process(command, cwd)
    val result = (log match {
      case Some(log) =>
        val io = BasicIO(false, log).withInput(_.close())
        process.run(io).exitValue()
      case None =>
        process.run().exitValue()
    })

    if (result != 0)
      sys.error("Nonzero exit code (" + result + "): " + command.mkString(" "))
  }

  private def creates(file: File)(f: => Unit): File = {
    if (!file.exists)
      try {
        f
      } catch {
        case NonFatal(e) =>
          IO.delete(file)
          throw e
      }
    file
  }

  private def uniqueSubdirectoryFor(uri: URI, in: File): File = {
    in.mkdirs()
    val base = new File(in, Hash.halfHashString(uri.normalize.toASCIIString))
    val last = shortName(uri) match {
      case Some(n) => normalizeDirectoryName(n); case None => "root"
    }
    new File(base, last)
  }

  private def shortName(uri: URI): Option[String] =
    Option(uri.withoutMarkerScheme.getPath).flatMap {
      _.split("/").map(_.trim).filterNot(_.isEmpty).lastOption
    }

  private def normalizeDirectoryName(name: String): String =
    dropExtensions(name).toLowerCase(Locale.ENGLISH).replaceAll("""\W+""", "-")

  private def dropExtensions(name: String): String = name.takeWhile(_ != '.')

  private def markUpdated(dir: File): Unit = {
    val marker = new File(dir, LastUpdatedFileName)
    IO.write(marker, System.currentTimeMillis().toString)
  }

  private[sbt] def shouldUpdate(dir: File, strategy: RepositoryUpdateStrategy): Boolean =
    strategy match {
      case RepositoryUpdateStrategy.Manual          => false
      case RepositoryUpdateStrategy.Always          => true
      case RepositoryUpdateStrategy.Every(interval) =>
        val marker = new File(dir, LastUpdatedFileName)
        if (!marker.exists()) true
        else {
          try {
            val lastUpdated = IO.read(marker).trim.toLong
            val elapsed = System.currentTimeMillis() - lastUpdated
            elapsed >= interval.toMillis
          } catch {
            case NonFatal(_) => true
          }
        }
    }

  private[sbt] def updateLoadedBuild(
      lb: LoadedBuild,
      log: Logger,
      extracted: Extracted,
      force: Boolean
  ): Boolean = {
    def needsUpdate(uri: URI, unit: LoadedBuildUnit): Boolean = {
      val rootProjectId = unit.rootProjects.headOption.getOrElse("root")
      val projectRef = ProjectRef(uri, rootProjectId)
      val strategy = extracted
        .getOpt(projectRef / Keys.repositoryUpdateStrategy)
        .getOrElse(RepositoryUpdateStrategy.Manual)
      shouldUpdate(unit.localBase, strategy)
    }

    log.info("Updating remote repos")
    val repos = (for {
      (uri, unit) <- lb.units if unit.localBase.exists()
      vcs <- RetrieveUnit(uri) if force || needsUpdate(uri, unit)
    } yield (unit.localBase, vcs))

    val isUpdated = repos.foldLeft(false) { case (acc, (repo, vcs)) =>
      log.info(s"Updating remote project: $repo ...")
      acc || Resolvers.updateRepository(repo, vcs, log)
    }

    isUpdated
  }

  private[sbt] def transitiveVcsRootRefs(
      startRef: ProjectRef,
      buildDependencies: BuildDependencies,
      lb: LoadedBuild
  ): Set[ProjectRef] =
    for {
      uri <- collectTransitiveRemoteBuildURIs(startRef, buildDependencies)
      if RetrieveUnit(uri).isDefined
      unit <- lb.units.get(uri)
      rootId = unit.rootProjects.headOption.getOrElse("root")
    } yield ProjectRef(uri, rootId)

  private def collectTransitiveRemoteBuildURIs(
      startRef: ProjectRef,
      buildDeps: BuildDependencies
  ): Set[URI] = {
    @annotation.tailrec
    def go(
        queue: List[ProjectRef],
        visited: Set[ProjectRef],
        result: Set[URI]
    ): Set[URI] =
      queue match {
        case Nil         => result
        case ref :: rest =>
          if (visited.contains(ref)) go(rest, visited, result)
          else {
            val classpathDeps = buildDeps.classpath.getOrElse(ref, Nil).map(_.project)
            val aggregateDeps = buildDeps.aggregate.getOrElse(ref, Nil)
            val allDeps = classpathDeps ++ aggregateDeps
            val newResult = allDeps.foldLeft(result) { (acc, dep) =>
              if (dep.build != startRef.build) acc + dep.build else acc
            }
            go(allDeps.toList ::: rest, visited + ref, newResult)
          }
      }
    go(List(startRef), Set.empty, Set.empty)
  }

  private[sbt] def updateRepository(localCopy: File, uri: URI, log: Logger): Boolean =
    RetrieveUnit(uri) match {
      case Some(vcs) => updateRepository(localCopy, vcs, log)
      case None      => false
    }

  private[sbt] def updateRepository(localCopy: File, vcs: RemoteVcs, log: Logger): Boolean =
    vcs match {
      case RemoteVcs.Git(uri) => updateGit(localCopy, uri, log)
      case RemoteVcs.Hg(uri)  => updateMercurial(localCopy, uri, log)
      case RemoteVcs.Svn(uri) => updateSubversion(localCopy, uri, log)
    }

  private def updateGit(localCopy: File, uri: URI, log: Logger): Boolean =
    try {
      val status = captureOutput(Some(localCopy), "git", "status", "--porcelain", "-uno")
      if (status.nonEmpty) {
        log.warn(
          s"Skipping update of $localCopy: uncommitted changes detected. " +
            "Commit or discard them before updating."
        )
        false
      } else {
        val headBefore = captureOutput(Some(localCopy), "git", "rev-parse", "HEAD")
        val ref = if (fromURI(uri).hasFragment) {
          val f = uri.getFragment
          VcsUriFragment.validate(f)
          f
        } else "HEAD"
        run(Some(localCopy), Some(log), "git", "fetch", "origin", ref)
        run(Some(localCopy), Some(log), "git", "reset", "--hard", "FETCH_HEAD")
        markUpdated(localCopy)
        captureOutput(Some(localCopy), "git", "rev-parse", "HEAD") != headBefore
      }
    } catch {
      case NonFatal(e) =>
        log.error(
          s"Failed to update git repository at $localCopy: ${e.getMessage}"
        )
        throw e
    }

  private def captureOutput(cwd: Option[File], command: String*): String =
    Process(command, cwd).!!.trim

  private def updateMercurial(localCopy: File, uri: URI, log: Logger): Boolean =
    try {
      val idBefore = captureOutput(Some(localCopy), "hg", "id", "-i")
      if (fromURI(uri).hasFragment) {
        val branch = uri.getFragment
        VcsUriFragment.validate(branch)
        run(Some(localCopy), Some(log), "hg", "pull")
        run(Some(localCopy), Some(log), "hg", "update", branch)
      } else {
        run(Some(localCopy), Some(log), "hg", "pull", "-u")
      }
      markUpdated(localCopy)
      captureOutput(Some(localCopy), "hg", "id", "-i") != idBefore
    } catch {
      case NonFatal(e) =>
        log.error(
          s"Failed to update mercurial repository at $localCopy: ${e.getMessage}"
        )
        throw e
    }

  private def updateSubversion(localCopy: File, uri: URI, log: Logger): Boolean =
    try {
      val revBefore = captureOutput(Some(localCopy), "svn", "info", "--show-item", "revision")
      if (fromURI(uri).hasFragment) {
        val revision = uri.getFragment
        VcsUriFragment.validate(revision)
        run(Some(localCopy), Some(log), "svn", "update", "-q", "-r", revision)
      } else {
        run(Some(localCopy), Some(log), "svn", "update", "-q")
      }
      markUpdated(localCopy)
      captureOutput(Some(localCopy), "svn", "info", "--show-item", "revision") != revBefore
    } catch {
      case NonFatal(e) =>
        log.error(
          s"Failed to update subversion working copy at $localCopy: ${e.getMessage}"
        )
        throw e
    }

}
