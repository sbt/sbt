package coursier.sbtcoursier

import coursier.core.Configuration
import coursier.{Info, Module, Project, moduleNameString, organizationString}
import utest._

object IvyXmlTests extends TestSuite {

  val tests = Tests {
    "no truncation" - {

      val project = Project(
        Module(org"org", name"name"),
        "ver",
        Nil,
        Map(
          Configuration("foo") -> (1 to 80).map(n => Configuration("bar" + n)) // long list of configurations -> no truncation any way
        ),
        None,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        relocated = false,
        None,
        Nil,
        Info.empty
      )

      val content = IvyXml.rawContent(project, None)

      assert(!content.contains("</conf>"))
    }
  }

}
