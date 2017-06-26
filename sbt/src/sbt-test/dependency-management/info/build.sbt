import scala.xml._

lazy val root = (project in file(".")).
  settings(
    ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache")),
    ivyXML := inlineXML(customInfo.value, organization.value, moduleName.value, version.value),
    scalaVersion := "2.9.1",
    projectID ~= (_ cross false),
    customInfo := (baseDirectory.value / "info").exists,
    TaskKey[Unit]("check-download") := checkDownload.value,
    delivered := (XML loadFile deliverLocal.value),
    TaskKey[Unit]("check-info") := checkInfo.value
  )

lazy val delivered = taskKey[NodeSeq]("")
lazy val customInfo = settingKey[Boolean]("")

def inlineXML(addInfo: Boolean, organization: String, moduleID: String, version: String): NodeSeq =
  if (addInfo)
    (<info organisation={organization} module={moduleID} revision={version}>
      <license name="Two-clause BSD-style" url="http://github.com/szeiger/scala-query/blob/master/LICENSE.txt" />
      <description homepage="http://github.com/szeiger/scala-query/">
        ScalaQuery is a type-safe database query API for Scala.
      </description>
    </info>
    <dependency org="org.scala-tools.testing" name="scalacheck_2.9.1" rev="1.9"/>)
  else
    <dependency org="org.scala-tools.testing" name="scalacheck_2.9.1" rev="1.9"/>

def checkDownload = Def task {
  if ((dependencyClasspath in Compile).value.isEmpty) sys.error("Dependency not downloaded"); ()
}

def checkInfo = Def task {
  val d = delivered.value
  val addInfo = customInfo.value
  if ((d \ "info").isEmpty)
    sys.error("No info tag generated")
  else if (addInfo) {
    if (!deliveredWithCustom(d)) sys.error("Expected 'license' and 'description' tags in info tag, got: \n" + (d \ "info")) else ()
  } else
    if (deliveredWithCustom(d)) sys.error("Expected empty 'info' tag, got: \n" + (d \ "info")) else ()
}

def deliveredWithCustom(d: NodeSeq) = (d \ "info" \ "license").nonEmpty && (d \ "info" \ "description").nonEmpty
