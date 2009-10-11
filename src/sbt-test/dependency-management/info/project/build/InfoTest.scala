import sbt._

class InfoTest(info: ProjectInfo) extends DefaultProject(info)
{
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList
	
	override def ivyXML =
		if(customInfo)
			(<info organisation="test" module="test" revision="1.0">
				<license name="Two-clause BSD-style" url="http://github.com/szeiger/scala-query/blob/master/LICENSE.txt" />
				<description homepage="http://github.com/szeiger/scala-query/">
					ScalaQuery is a type-safe database query API for Scala.
				</description>
			</info>
			<dependency org="org.scalacheck" name="scalacheck" rev="1.5"/>)
		else
			<dependency org="org.scalacheck" name="scalacheck" rev="1.5"/>

	def customInfo = "info".asFile.exists

	lazy val checkDownload = task { if(compileClasspath.get.isEmpty) Some("Dependency not downloaded") else None }
	lazy val checkInfo = task {
		if((delivered \ "info").isEmpty)
			Some("No info tag generated")
		else if(customInfo)
			if( deliveredWithCustom ) None else Some("Expected 'license' and 'description' tags in info tag, got: \n" + (delivered \ "info"))
		else
			if( deliveredWithCustom ) Some("Expected empty 'info' tag, got: \n" + (delivered \ "info")) else None
	}
	def deliveredWithCustom = !(delivered \ "info" \ "license").isEmpty && !(delivered \ "info" \ "description").isEmpty
	def delivered = scala.xml.XML.loadFile(ivyFile)

	def ivyFile = (outputPath * "ivy*.xml").get.toList.head.asFile
}