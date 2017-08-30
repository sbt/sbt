package coursier

import utest._

object IvyXmlTests extends TestSuite {

  val tests = TestSuite {
    "no truncation" - {

      val project = Project(
        Module("org", "name"),
        "ver",
        Nil,
        Map(
          "foo" -> (1 to 80).map("bar" + _) // long list of configurations -> no truncation any way
        ),
        None,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        None,
        Nil,
        Info.empty
      )

      val content = IvyXml.rawContent(project, None)

      assert(!content.contains("</conf>"))
    }
  }

}
