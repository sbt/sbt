/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah, Indrajit Raychaudhuri
 */
package sbt

/** Options for well-known tasks. */
object Opts {
	object compile {
		val deprecation = "-deprecation"
		def encoding(enc: String) = Seq("-encoding", enc)
		val explaintypes = "-explaintypes"
		val nowarn = "-nowarn"
		val optimise = "-optimise"
		val unchecked = "-unchecked"
		val verbose = "-verbose"
	}
	object doc {
		def generator(g: String) = Seq("-doc-generator", g)
		def sourceUrl(u: String) = Seq("-doc-source-url", u)
		def title(t: String) = Seq("-doc-title", t)
		def version(v: String) = Seq("-doc-version", v)
	}
}

object DefaultOptions {
	import Opts._
	import Path._
	def javac: Seq[String] = compile.encoding("UTF-8")
	def scalac: Seq[String] = compile.encoding("UTF-8")
	def javadoc(name: String, version: String): Seq[String] = Seq("-doctitle", "%s %s API".format(name, version))
	def scaladoc(name: String, version: String): Seq[String] = doc.title(name) ++ doc.version(version)
	def credentials: Credentials = Credentials(userHome / ".sbt" / ".credentials")
}
