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

	val git: Resolver = (info: ResolveInfo) => {
		def clone(at: String, into: File)
		{
			run(None, "git", "clone", at, into.getAbsolutePath)
		}

		def checkout(branch: String, in: File)
		{
			run(Some(in), "git", "checkout", "-q", branch)
		}

		def retrieveLocalCopy(at: URI, into: File) =
		{
			creates(into) {
				clone(at.withoutFragment.toASCIIString, into)
				if (at.hasFragment)
					checkout(branch = at.getFragment, in = into)
			}
		}

		val uri = info.uri
		Some {
			() =>
				retrieveLocalCopy(at = uri, into = uniqueSubdirectoryFor(uri, in = info.staging))
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
