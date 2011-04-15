	import sbt._
	import Keys._
	import scala.xml._

object InfoTest extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
		ivyXML <<= (baseDirectory, organization, moduleID) apply inlineXML,
		TaskKey("check-download") <<= checkDownload,
		delivered <<= deliverLocal map XML.loadFile,
		TaskKey("check-info") <<= checkInfo
	)
	lazy val delivered = TaskKey[NodeSeq]("delivered")

	def inlineXML(baseDirectory: File, organization: String, moduleID: String): NodeSeq =
		if(baseDirectory / "info" exists)
			(<info organisation={organization} module={moduleID} revision="1.0">
				<license name="Two-clause BSD-style" url="http://github.com/szeiger/scala-query/blob/master/LICENSE.txt" />
				<description homepage="http://github.com/szeiger/scala-query/">
					ScalaQuery is a type-safe database query API for Scala.
				</description>
			</info>
			<dependency org="org.scalacheck" name="scalacheck" rev="1.5"/>)
		else
			<dependency org="org.scalacheck" name="scalacheck" rev="1.5"/>

	def customInfo = file("info").exists
	def checkDownload = (dependencyClasspath in Compile) map { cp => if(cp.isEmpty) error("Dependency not downloaded") }
	def checkInfo = delivered map { d =>
		if((d \ "info").isEmpty)
			error("No info tag generated")
		else if(customInfo)
			if( !deliveredWithCustom(d) ) error("Expected 'license' and 'description' tags in info tag, got: \n" + (d \ "info"))
		else
			if( deliveredWithCustom(d) ) error("Expected empty 'info' tag, got: \n" + (d \ "info"))
	}
	def deliveredWithCustom(d: NodeSeq) = !(d \ "info" \ "license").isEmpty && !(d \ "info" \ "description").isEmpty
}