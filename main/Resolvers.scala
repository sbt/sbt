/* sbt -- Simple Build Tool
 * Copyright 2011 Sanjin Sehic
 */

package sbt

import java.io.File
import java.net.URI

import BuildLoader.ResolveInfo
import RichURI.fromURI

object Resolvers
{
	type Resolver = BuildLoader.Resolver

	val local: Resolver = (info: ResolveInfo) => {
		def retrieveRODir(at: File, into: File) = creates(into) {IO.copyDirectory(at, into)}

		val uri = info.uri
		val dir = new File(uri)
		if (dir.isDirectory) {
			Some {
				() =>
					if (dir.canWrite)
						dir
					else
						retrieveRODir(at = dir, into = uniqueSubdirectoryFor(uri, in = info.staging))
			}
		} else None
	}

	val remote: Resolver = (info: ResolveInfo) => {
		def downloadAndExtract(at: URI, into: File) = creates(into) {IO.unzipURL(at.toURL, into)}

		val uri = info.uri
		Some {
			() =>
				downloadAndExtract(at = uri, into = uniqueSubdirectoryFor(uri, in = info.staging))
		}
	}

	val subversion: Resolver = (info: ResolveInfo) => {
		def normalized(uri: URI) = uri.copy(scheme = "svn")

		def checkout(at: URI, into: File, revision: Option[String]) =
		{
			creates(into) {
				revision match {
					case Some(r) =>
						run(None, "svn", "checkout", "-r", r, at.toASCIIString, into.getAbsolutePath)
					case None =>
						run(None, "svn", "checkout", at.toASCIIString, into.getAbsolutePath)
				}
			}
		}

		val uri = info.uri.withoutMarkerScheme
		Some {
			() =>
				checkout(
					at = uri.withoutFragment,
					into = uniqueSubdirectoryFor(normalized(uri), in = info.staging),
					revision = Option(uri.getFragment)
				)
		}
	}

	val mercurial: Resolver = new DistributedVCS
	{
		override val scheme = "hg"

		override def clone(at: String, into: File)
		{
			run(None, "hg", "clone", at, into.getAbsolutePath)
		}

		override def checkout(branch: String, in: File)
		{
			run(Some(in), "hg", "-q", "checkout", branch)
		}
	}.toResolver

	val git: Resolver = new DistributedVCS
	{
		override val scheme = "git"

		override def clone(at: String, into: File)
		{
			run(None, "git", "clone", at, into.getAbsolutePath)
		}

		override def checkout(branch: String, in: File)
		{
			run(Some(in), "git", "checkout", "-q", branch)
		}
	}.toResolver

	abstract class DistributedVCS
	{
		val scheme: String

		def clone(at: String, into: File)

		def checkout(branch: String, in: File)

		def toResolver: Resolver = (info: ResolveInfo) => {
			val uri = info.uri.withoutMarkerScheme
			val staging = info.staging
			Some {
				() =>
					val localCopy = retrieveLocalCopy(at = uri, into = uniqueSubdirectoryFor(normalized(uri.withoutFragment), in = staging))
					if (uri.hasFragment)
						retrieveBranch(branch = uri.getFragment, from = localCopy, into = uniqueSubdirectoryFor(normalized(uri), in = staging))
					else
						localCopy
			}
		}

		private def normalized(uri: URI) = uri.copy(scheme = scheme)

		private def retrieveLocalCopy(at: URI, into: File) = creates(into) {clone(at.withoutFragment.toASCIIString, into)}

		private def retrieveBranch(branch: String, from: File, into: File) =
		{
			creates(into) {
				clone(at = from.getAbsolutePath, into = into)
				checkout(branch, in = into)
			}
		}
	}

	private lazy val onWindows = {
		val os = System.getenv("OSTYPE")
		val isCygwin = (os != null) && os.toLowerCase.contains("cygwin")
		val isWindows = System.getProperty("os.name", "").toLowerCase.contains("windows")
		isWindows && !isCygwin
	}

	def run(cwd: Option[File], command: String*)
	{
		val result =
			Process(
				if (onWindows)
					"cmd" +: "/c" +: command
				else
					command,
				cwd
			) !;
		if (result != 0)
			error("Nonzero exit code (" + result + "): " + command.mkString(" "))
	}

	def creates(file: File)(f: => Unit) =
	{
		try {
			if (!file.exists)
				f
			file
		} catch {
			case e =>
				IO.delete(file)
				throw e
		}
	}

	def uniqueSubdirectoryFor(uri: URI, in: File) = new File(in, Hash.halfHashString(uri.toASCIIString))
}
