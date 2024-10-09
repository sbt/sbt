package sbt.librarymanagement

import verify.BasicTestSuite
import scala.annotation.nowarn

@nowarn // Necessary because our test cases look like interpolated strings.
object ResolverExtraTest extends BasicTestSuite {
  test("expandMavenSettings should expand existing environment variables") {
    assertExpansion(
      input = "User home: ${env.HOME}",
      expected = s"User home: ${env("HOME")}"
    )
  }

  test("expandMavenSettings should expand existing system properties") {
    assertExpansion(
      input = "User dir: ${user.dir}",
      expected = s"User dir: ${prop("user.dir")}"
    )
  }

  test("expandMavenSettings should expand unknown system properties to the empty string") {
    assertExpansion(
      input = "Unknown system property: ${IF_THIS_EXISTS_WE_NEED_TO_HAVE_A_CHAT}",
      expected = s"Unknown system property: "
    )
  }

  test("expandMavenSettings should expand unknown environment variables to the empty string") {
    assertExpansion(
      input = "Unknown environment variable: ${IF_THIS_EXISTS_I_WORRY_ABOUT_YOU}",
      expected = s"Unknown environment variable: "
    )
  }

  test("expandMavenSettings should preserve backslashes in environment variable values") {
    val path = """C:\foo\bar\baz"""
    val env = Map("SOME_PATH" -> path)

    assert(Resolver.expandMavenSettings("${env.SOME_PATH}", env) == path)
  }

  // - Helper functions ----------------------------------------------------------------------------
  // -----------------------------------------------------------------------------------------------
  def assertExpansion(input: String, expected: String) =
    Predef.assert(Resolver.expandMavenSettings(input) == s"$expected")

  def env(name: String) = sys.env.getOrElse(name, "")
  def prop(name: String) = sys.props.getOrElse(name, "")
}
