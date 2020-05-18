package lmcoursier

import lmcoursier.definitions.{Configuration, Info, Module, ModuleName, Organization, Project}
import org.scalatest.{Matchers, PropSpec}

object IvyXmlTests extends PropSpec with Matchers {

  property("no truncation") {
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

    val content = IvyXml(project, Nil, None)

    assert(!content.contains("</conf>"))
  }

}
