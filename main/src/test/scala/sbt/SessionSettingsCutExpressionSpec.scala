package sbt

class SessionSettingsCutExpressionSpec extends AbstractSpec {

  "Cut expression " should {

    "Cut only statement which we are interesting " in {
      val name = "scalaVersion"
      val expression = s"""$name := "2.9.2""""
      val line = s"""name := "newName";$expression; organization := "jozwikr""""
      SessionSettingsNoBlankies.cutExpression(List(line), name) must_== List(expression)
    }

    "Do not cut not valid expression " in {
      val name = "k4"
      val line = s"$name := { val x = $name.value; () }"
      SessionSettingsNoBlankies.cutExpression(List(line), name) must_== List(line)

    }
  }
}
