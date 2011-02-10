/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

final case class ArtifactName(base: String, version: String, config: String, tpe: String, ext: String)
object ArtifactName
{
	def show(name: ArtifactName) =
	{
		import name._
		val confStr = if(config.isEmpty || config == "compile") "" else "-" + config
		val tpeStr = if(tpe.isEmpty) "" else "-" + tpe
		base + "-" + version + confStr + tpeStr + "." + ext
	}
}