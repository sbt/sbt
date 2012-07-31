/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import BuildLoader.ResolveInfo
	import Def.{ScopedKey, Setting}

object RetrieveUnit
{
	def apply(info: ResolveInfo): Option[() => File] =
	{
		info.uri match {
			case Scheme("svn") | Scheme("svn+ssh") => Resolvers.subversion(info)
			case Scheme("hg") => Resolvers.mercurial(info)
			case Scheme("git") => Resolvers.git(info)
			case Path(path) if path.endsWith(".git") => Resolvers.git(info)
			case Scheme("http") | Scheme("https") | Scheme("ftp") => Resolvers.remote(info)
			case Scheme("file") => Resolvers.local(info)
			case _ => None
		}
	}

	object Scheme
	{
		def unapply(uri: URI) = Option(uri.getScheme)
	}

	object Path
	{
		import RichURI.fromURI

		def unapply(uri: URI) = Option(uri.withoutMarkerScheme.getPath)
	}
}
