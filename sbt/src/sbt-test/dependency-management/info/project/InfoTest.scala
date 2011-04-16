	import sbt._
	import Keys._
	import scala.xml._

object InfoTest extends Build
{
	lazy val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
		ivyXML <<= (customInfo, organization, moduleID, version) apply inlineXML,
		projectID ~= (_ cross false),
		customInfo <<= baseDirectory{_ / "info" exists },
		TaskKey("check-download") <<= checkDownload,
		delivered <<= deliverLocal map XML.loadFile,
		TaskKey("check-info") <<= checkInfo
	)
	lazy val delivered = TaskKey[NodeSeq]("delivered")
	lazy val customInfo = SettingKey[Boolean]("custom-info")

	def inlineXML(addInfo: Boolean, organization: String, moduleID: String, version: String): NodeSeq =
		if(addInfo)
			(<info organisation={organization} module={moduleID} revision={version}>
				<license name="Two-clause BSD-style" url="http://github.com/szeiger/scala-query/blob/master/LICENSE.txt" />
				<description homepage="http://github.com/szeiger/scala-query/">
					ScalaQuery is a type-safe database query API for Scala.
				</description>
			</info>
			<dependency org="org.scalacheck" name="scalacheck" rev="1.5"/>)
		else
			<dependency org="org.scalacheck" name="scalacheck" rev="1.5"/>

	def checkDownload = (dependencyClasspath in Compile) map { cp => if(cp.isEmpty) error("Dependency not downloaded") }
	def checkInfo = (customInfo, delivered) map { (addInfo, d) =>
		if((d \ "info").isEmpty)
			error("No info tag generated")
		else if(addInfo) {
			if( !deliveredWithCustom(d) ) error("Expected 'license' and 'description' tags in info tag, got: \n" + (d \ "info"))
		} else
			if( deliveredWithCustom(d) ) error("Expected empty 'info' tag, got: \n" + (d \ "info"))
	}
	def deliveredWithCustom(d: NodeSeq) = !(d \ "info" \ "license").isEmpty && !(d \ "info" \ "description").isEmpty
}