package coursier.sbtcoursiershared

import lmcoursier.definitions.{Configuration, Info, Module, ModuleName, Organization, Project}
import utest._

object IvyXmlTests extends TestSuite {

  val tests = Tests {
    "no truncation" - {

      val project = Project(
        Module(Organization("org"), ModuleName("name"), Map()),
        "ver",
        Nil,
        Map(
          Configuration("foo") -> (1 to 80).map(n => Configuration("bar" + n)) // long list of configurations -> no truncation any way
        ),
        Nil,
        None,
        Nil,
        Info("", "", Nil, Nil, None)
      )

      val content = IvyXml.rawContent(project, Nil, None)

      assert(!content.contains("</conf>"))
    }
  }

}
