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
import sbt.util.Logger
import sbt.internal.VcsUriFragment

object Resolvers {
  type Resolver = BuildLoader.Resolver

  val local: Resolver = (info: ResolveInfo) => {
    val uri = info.uri
    val from = new File(uri)
    val to = uniqueSubdirectoryFor(uri, in = info.staging)

    if (from.isDirectory) Some { () =>
      if (from.canWrite) from else creates(to) { IO.copyDirectory(from, to) }
    } else None
  }

  val remote: Resolver = (info: ResolveInfo) => {
    val url = info.uri.toURL
    val to = uniqueSubdirectoryFor(info.uri, in = info.staging)

    Some { () =>
      creates(to) { IO.unzipURL(url, to); () }
    }
  }

  val subversion: Resolver = (info: ResolveInfo) => {
    def normalized(uri: URI) = uri.copy(scheme = "svn")

    val uri = info.uri.withoutMarkerScheme
    val localCopy = uniqueSubdirectoryFor(normalized(uri), in = info.staging)
    val from = uri.withoutFragment.toASCIIString
    val to = localCopy.getAbsolutePath

    if (uri.hasFragment) {
      val revision = uri.getFragment
      VcsUriFragment.validate(revision)
      Some { () =>
        creates(localCopy) {
          run("svn", "checkout", "-q", "-r", revision, from, to)
        }
      }
    } else
      Some { () =>
        creates(localCopy) {
          run("svn", "checkout", "-q", from, to)
        }
      }
  }

  val mercurial: Resolver = new DistributedVCS {
    override val scheme = "hg"

    override def clone(from: String, to: File): Unit = {
      run("hg", "clone", "-q", from, to.getAbsolutePath)
    }

    override def checkout(branch: String, in: File): Unit = {
      run(Some(in), "hg", "checkout", "-q", branch)
    }
  }.toResolver

  val git: Resolver = (info: ResolveInfo) => {
    val uri = info.uri.withoutMarkerScheme
    val localCopy = uniqueSubdirectoryFor(uri.copy(scheme = "git"), in = info.staging)
    val from = uri.withoutFragment.toASCIIString

    if (uri.hasFragment) {
      val branch = uri.getFragment
      VcsUriFragment.validate(branch)
      Some { () =>
        creates(localCopy) {
          run("git", "clone", from, localCopy.getAbsolutePath)
          run(Some(localCopy), "git", "checkout", "-q", branch)
        }
      }
    } else
      Some { () =>
        creates(localCopy) {
          run("git", "clone", "--depth", "1", from, localCopy.getAbsolutePath)
        }
      }
  }

  abstract class DistributedVCS {
    val scheme: String

    def clone(from: String, to: File): Unit

    def checkout(branch: String, in: File): Unit

    def toResolver: Resolver = (info: ResolveInfo) => {
      val uri = info.uri.withoutMarkerScheme
      val localCopy = uniqueSubdirectoryFor(normalized(uri), in = info.staging)
      val from = uri.withoutFragment.toASCIIString

      if (uri.hasFragment) {
        val branch = uri.getFragment
        VcsUriFragment.validate(branch)
        Some { () =>
          creates(localCopy) {
            clone(from, to = localCopy)
            checkout(branch, in = localCopy)
          }
        }
      } else
        Some { () =>
          creates(localCopy) { clone(from, to = localCopy) }
        }
    }

    private def normalized(uri: URI) = uri.copy(scheme = scheme)
  }

  def run(command: String*): Unit =
    run(None, command: _*)

  def run(cwd: Option[File], command: String*): Unit =
    run(cwd, None, command: _*)

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

  def creates(file: File)(f: => Unit) = {
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

  def uniqueSubdirectoryFor(uri: URI, in: File) = {
    in.mkdirs()
    val base = new File(in, Hash.halfHashString(uri.normalize.toASCIIString))
    val last = shortName(uri) match {
      case Some(n) => normalizeDirectoryName(n); case None => "root"
    }
    new File(base, last)
  }

  private[this] def shortName(uri: URI): Option[String] =
    Option(uri.withoutMarkerScheme.getPath).flatMap {
      _.split("/").map(_.trim).filterNot(_.isEmpty).lastOption
    }

  private[this] def normalizeDirectoryName(name: String): String =
    dropExtensions(name).toLowerCase(Locale.ENGLISH).replaceAll("""\W+""", "-")

  private def dropExtensions(name: String): String = name.takeWhile(_ != '.')

}
