package sbt.librarymanagement

import org.scalatest.{ FreeSpec, Matchers }

class SemanticSelectorSpec extends FreeSpec with Matchers {
  semsel("<=1.2.3") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.2.3-beta")
    assertMatches(sel, "1.2")
    assertMatches(sel, "1")
    assertNotMatches(sel, "1.2.4")
    assertNotMatches(sel, "1.3")
    assertNotMatches(sel, "1.3.0")
    assertNotMatches(sel, "2")
  }

  semsel("<=1.2") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.2.3-beta")
    assertMatches(sel, "1.2")
    assertMatches(sel, "1")
    assertNotMatches(sel, "1.3.0")
  }

  semsel("<=1") { sel =>
    assertMatches(sel, "1.12.12")
    assertMatches(sel, "1.12.12-alpha")
    assertMatches(sel, "1.2")
    assertNotMatches(sel, "2.0.0")
    assertNotMatches(sel, "2.0.0-alpha")
  }

  semsel("<1.2.3") { sel =>
    assertMatches(sel, "1.2.2")
    assertMatches(sel, "1.2")
    assertNotMatches(sel, "1.2.3-alpha")
    assertNotMatches(sel, "1.2.3")
  }

  semsel("<1.2") { sel =>
    assertMatches(sel, "1.1.23")
    assertMatches(sel, "1.1")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "1.2.0-alpha")
  }

  semsel("<1") { sel =>
    assertMatches(sel, "0.9.12")
    assertMatches(sel, "0.8")
    assertNotMatches(sel, "1")
    assertNotMatches(sel, "1.0")
    assertNotMatches(sel, "1.0.0")
  }

  semsel(">=1.2.3") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.2.3-beta")
    assertMatches(sel, "1.3")
    assertMatches(sel, "2")
    assertNotMatches(sel, "1.2.2")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "1")
  }

  semsel(">=1.2") { sel =>
    assertMatches(sel, "1.2.0")
    assertMatches(sel, "1.2.0-beta")
    assertMatches(sel, "1.2")
    assertMatches(sel, "2")
    assertNotMatches(sel, "1.1.23")
    assertNotMatches(sel, "1.1")
    assertNotMatches(sel, "1")
  }

  semsel(">=1") { sel =>
    assertMatches(sel, "1.0.0")
    assertMatches(sel, "1.0.0-beta")
    assertMatches(sel, "1.0")
    assertMatches(sel, "1")
    assertNotMatches(sel, "0.9.9")
    assertNotMatches(sel, "0.1")
    assertNotMatches(sel, "0")
  }

  semsel(">1.2.3") { sel =>
    assertMatches(sel, "1.2.4")
    assertMatches(sel, "1.2.4-alpha")
    assertMatches(sel, "1.3")
    assertMatches(sel, "2")
    assertNotMatches(sel, "1.2.3")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "1")
  }

  semsel(">1.2") { sel =>
    assertMatches(sel, "1.3.0")
    assertMatches(sel, "1.3.0-alpha")
    assertMatches(sel, "1.3")
    assertMatches(sel, "2")
    assertNotMatches(sel, "1.2.9")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "1")
  }

  semsel(">1") { sel =>
    assertMatches(sel, "2.0.0")
    assertMatches(sel, "2.0")
    assertMatches(sel, "2")
    assertNotMatches(sel, "1.2.3")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "1")
  }

  semsel("1.2.3") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.2.3-alpha")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "1.2.4")
  }

  Seq(".x", ".X", ".*", ".x.x", "").foreach { xrange =>
    semsel(s"1$xrange") { sel =>
      assertMatches(sel, "1.0.0")
      assertMatches(sel, "1.0.1")
      assertMatches(sel, "1.1.1")
      assertMatches(sel, "1.0.0-alpha")
      assertNotMatches(sel, "2.0.0")
      assertNotMatches(sel, "0.1.0")
    }
  }

  Seq(".x", ".X", ".*", "").foreach { xrange =>
    semsel(s"1.2$xrange") { sel =>
      assertMatches(sel, "1.2.0")
      assertMatches(sel, "1.2.0-beta")
      assertMatches(sel, "1.2.3")
      assertNotMatches(sel, "1.3.0")
      assertNotMatches(sel, "1.1.1")
    }
  }

  semsel("=1.2.3") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.2.3-alpha")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "1.2.4")
  }
  semsel("=1.2") { sel =>
    assertMatches(sel, "1.2.0")
    assertMatches(sel, "1.2.0-alpha")
    assertMatches(sel, "1.2")
    assertMatches(sel, "1.2.1")
    assertMatches(sel, "1.2.4")
  }
  semsel("=1") { sel =>
    assertMatches(sel, "1.0.0")
    assertMatches(sel, "1.0.0-alpha")
    assertMatches(sel, "1.0")
    assertMatches(sel, "1.0.1")
    assertMatches(sel, "1.2.3")
  }
  semsel("1.2.3 || 2.0.0") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "2.0.0")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "2.0.1")
  }
  semsel("<=1.2.3 || >=2.0.0 || 1.3.x") { sel =>
    assertMatches(sel, "1.0")
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "2.0.0")
    assertMatches(sel, "2.0")
    assertMatches(sel, "1.3.0")
    assertMatches(sel, "1.3.3")
    assertNotMatches(sel, "1.2.4")
    assertNotMatches(sel, "1.4.0")
  }

  semsel(">=1.2.3 <2.0.0") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.9.9")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "2.0.0")
  }

  semsel(">=1.2.3 <2.0.0 || >3.0.0 <=3.2.0") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.9.9")
    assertMatches(sel, "3.0.1")
    assertMatches(sel, "3.2.0")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "2.0.0")
    assertNotMatches(sel, "3.0.0")
    assertNotMatches(sel, "3.2.1")
  }

  semsel("1.2.3 - 2.0.0") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.9.9")
    assertMatches(sel, "2.0.0")
    assertNotMatches(sel, "1.2")
    assertNotMatches(sel, "2.0.1")
  }
  semsel("1.2 - 2") { sel =>
    assertMatches(sel, "1.2.0")
    assertMatches(sel, "1.9.9")
    assertMatches(sel, "2.0.0")
    assertMatches(sel, "2.0.1")
    assertNotMatches(sel, "1.1")
    assertNotMatches(sel, "3.0.0")
  }
  semsel("1.2.3 - 2.0.0 1.5.0 - 2.4.0") { sel =>
    assertMatches(sel, "1.5.0")
    assertMatches(sel, "1.9.9")
    assertMatches(sel, "2.0.0")
    assertNotMatches(sel, "1.2.3")
    assertNotMatches(sel, "1.4")
    assertNotMatches(sel, "2.0.1")
    assertNotMatches(sel, "2.4.0")
  }
  semsel("1.2.3 - 2.0 || 2.4.0 - 3") { sel =>
    assertMatches(sel, "1.2.3")
    assertMatches(sel, "1.5.0")
    assertMatches(sel, "2.0.0")
    assertMatches(sel, "2.4.0")
    assertMatches(sel, "2.9")
    assertMatches(sel, "3.0.0")
    assertMatches(sel, "2.0.1")
    assertMatches(sel, "3.0.1")
    assertMatches(sel, "3.1.0")
    assertNotMatches(sel, "2.1")
    assertNotMatches(sel, "2.3.9")
    assertNotMatches(sel, "4.0.0")
  }

  semsel(">=1.x") { sel =>
    assertMatches(sel, "1.0.0")
    assertMatches(sel, "1.0.0-beta")
    assertMatches(sel, "1.0")
    assertMatches(sel, "1")
    assertNotMatches(sel, "0.9.9")
    assertNotMatches(sel, "0.1")
    assertNotMatches(sel, "0")
  }

  Seq(
    // invalid operator
    "~1.2.3",
    "<~1.2.3",
    "+1.2.3",
    "!1.0.0",
    ">~1.2.3",
    // too much version fields
    "1.2.3.4",
    "1.2.3.4.5",
    "1.2.3.x",
    // invalid version specifier
    "string.!?",
    "1.y",
    "1.2x",
    "1.1.c",
    "-1",
    "x",
    "",
    // || without spaces
    "1.2.3|| 2.3.4",
    "1.2.3 ||2.3.4",
    "1.2.3||2.3.4",
    // invalid - operator
    "- 1.1.1",
    "2.0.0 -",
    "1.0.0 - 2.0.0 - 3.0.0",
    ">=1.0.0 - 2.0.0",
    "1.0.0 - =3.0.0",
    "=1.0.0 - =3.0.0",
    "1.0.0 - 2.0.0 || - 2.0.0",
    "1.0.0- 2.0.0",
    "1.0.0 -2.0.0",
    "1.0.0-2.0.0",
    "-",
    // cannot specify pre-release or metadata
    "1.2.3-alpha",
    "1.2-alpha",
    "1.2.3+meta"
  ).foreach { selectorStr =>
    semsel(selectorStr) { sel =>
      assertParsesToError(sel)
    }
  }

  private[this] final class SemanticSelectorString(val value: String)
  private[this] def semsel(s: String)(f: SemanticSelectorString => Unit): Unit =
    s"""SemanticSelector "$s"""" - {
      f(new SemanticSelectorString(s))
    }

  private[this] def assertMatches(
      s: SemanticSelectorString,
      v: String
  ): Unit = s"""should match "$v"""" in {
    SemanticSelector(s.value).matches(VersionNumber(v)) shouldBe true
  }

  private[this] def assertNotMatches(
      s: SemanticSelectorString,
      v: String
  ): Unit = s"""should not match "$v"""" in {
    SemanticSelector(s.value).matches(VersionNumber(v)) shouldBe false
  }

  private[this] def assertParsesToError(s: SemanticSelectorString): Unit =
    s"""should parse as an error""" in {
      an[IllegalArgumentException] should be thrownBy SemanticSelector(s.value)
    }
}
