package sbt

class EmbeddedXmlWithBracketsSpec extends AbstractSpec {

  "File with xml content " should {

    "Do not change xml " in {
      val xml =
        """
          | val pom = ( <scm/><a/><b/> )
        """.
          stripMargin
      XmlContent.handleXmlContent(xml) must_== xml
    }
  }
}
