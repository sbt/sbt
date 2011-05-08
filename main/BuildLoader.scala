/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import Load.{BuildUnit, LoadBuildConfiguration}
	import BuildLoader._

final class ResolveInfo(val build: URI, val staging: File)
final class BuildLoader(val load: (URI, File) => BuildUnit, val builtIn: BuildResolver, val root: Option[BuildResolver], val nonRoots: List[(URI, BuildResolver)], val fail: URI => Nothing, val config: LoadBuildConfiguration)
{
		import Alternatives._
		import config.{log, stagingDirectory => dir}

	def apply(uri: URI): BuildUnit  =  load(uri, resolve(new ResolveInfo(uri, dir)))
	def resolve(info: ResolveInfo): File =
		(baseLoader(info), applyNonRoots(info)) match
		{
			case (None, Nil) => fail(info.build)
			case (None, xs @ (_, nr) :: ignored ) =>
				if(!ignored.isEmpty) warn("Using first of multiple matching non-root build resolver for " + info.build, log, xs)
				nr()
			case (Some(b), xs) =>
				if(!xs.isEmpty) warn("Ignoring shadowed non-root build resolver(s) for " + info.build, log, xs)
				b()
		}

	def baseLoader: BuildResolver = root match { case Some(rl) => rl | builtIn; case None => builtIn }

	def addNonRoot(uri: URI, loader: BuildResolver) = new BuildLoader(load, builtIn, root, (uri, loader) :: nonRoots, fail, config)
	def setRoot(resolver: BuildResolver) = new BuildLoader(load, builtIn, Some(resolver), nonRoots, fail, config)
	def applyNonRoots(info: ResolveInfo): List[(URI, () => File)] =
		nonRoots flatMap { case (definingURI, loader) => loader(info) map { unit => (definingURI, unit) } }

	private[this] def warn(baseMessage: String, log: Logger, matching: Seq[(URI, () => File)])
	{
		log.warn(baseMessage)
		log.debug("Non-root build resolvers defined in:")
		log.debug(matching.map(_._1).mkString("\n\t"))
	}
}
object BuildLoader
{
	/** in: Build URI and staging directory
	* out: None if unhandled or Some containing the retrieve function, which returns the directory retrieved to (can be the same as the staging directory) */
	type BuildResolver = ResolveInfo => Option[() => File]
	def apply(load: (URI, File) => BuildUnit, builtIn: BuildResolver, fail: URI => Nothing, config: LoadBuildConfiguration): BuildLoader =
		new BuildLoader(load, builtIn, None, Nil, fail, config)
}