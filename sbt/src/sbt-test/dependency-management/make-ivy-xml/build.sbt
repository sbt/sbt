import scala.xml.XML

val descriptionValue = "This is just a test"
val homepageValue = "http://example.com"

lazy val root = (project in file(".")) settings(
  name := "ivy-xml-test",
  description := descriptionValue,
  homepage := Some(url(homepageValue)),

  TaskKey[Unit]("checkIvyXml") := {
    val ivyXml = XML.loadFile(makeIvyXml.value)
    val description = (ivyXml \ "info" \ "description").head
    val homepage = (description \ "@homepage").head

    if (description.text != descriptionValue)
      sys.error(s"Unexpected description: ${description.text}")

    if (homepage.text != homepageValue)
      sys.error(s"Unexpected homepage: ${homepage.text}")

    ()
  }
)
