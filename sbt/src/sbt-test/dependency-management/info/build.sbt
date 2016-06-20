import scala.xml._

lazy val root = (project in file(".")).
  settings(
    ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache"))),
    ivyXML <<= (customInfo, organization, moduleName, version) apply inlineXML,
    scalaVersion := "2.9.1",
    projectID ~= (_ cross false),
    customInfo <<= baseDirectory{_ / "info" exists },
    TaskKey[Unit]("check-download") <<= checkDownload,
    delivered <<= deliverLocal map XML.loadFile,
    TaskKey[Unit]("check-info") <<= checkInfo
  )

lazy val delivered = taskKey[NodeSeq]("")
lazy val customInfo = settingKey[Boolean]("")

def inlineXML(addInfo: Boolean, organization: String, moduleID: String, version: String): NodeSeq =
  if(addInfo)
    (<info organisation={organization} module={moduleID} revision={version}>
      <license name="Two-clause BSD-style" url="http://github.com/szeiger/scala-query/blob/master/LICENSE.txt" />
      <description homepage="http://github.com/szeiger/scala-query/">
        ScalaQuery is a type-safe database query API for Scala.
      </description>
    </info>
    <dependency org="org.scala-tools.testing" name="scalacheck_2.9.1" rev="1.9"/>)
  else
    <dependency org="org.scala-tools.testing" name="scalacheck_2.9.1" rev="1.9"/>

def checkDownload = (dependencyClasspath in Compile) map { cp => if(cp.isEmpty) sys.error("Dependency not downloaded"); () }
def checkInfo = (customInfo, delivered) map { (addInfo, d) =>
  if((d \ "info").isEmpty)
    sys.error("No info tag generated")
  else if(addInfo) {
    if( !deliveredWithCustom(d) ) sys.error("Expected 'license' and 'description' tags in info tag, got: \n" + (d \ "info")) else ()
  } else
    if( deliveredWithCustom(d) ) sys.error("Expected empty 'info' tag, got: \n" + (d \ "info")) else ()
}
def deliveredWithCustom(d: NodeSeq) = (d \ "info" \ "license").nonEmpty && (d \ "info" \ "description").nonEmpty

