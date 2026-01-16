package sbt.librarymanagement

import hedgehog.*
import hedgehog.runner.*
import scala.annotation.nowarn

@nowarn // Necessary because our test cases look like interpolated strings.
object ResolverExtraTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "expandMavenSettings should expand existing environment variables",
      assertExpansion(
        input = "User home: ${env.HOME}",
        expected = s"User home: ${env("HOME")}"
      )
    ),
    example(
      "expandMavenSettings should expand existing system properties",
      assertExpansion(
        input = "User dir: ${user.dir}",
        expected = s"User dir: ${prop("user.dir")}"
      )
    ),
    example(
      "expandMavenSettings should expand unknown system properties to the empty string",
      assertExpansion(
        input = "Unknown system property: ${IF_THIS_EXISTS_WE_NEED_TO_HAVE_A_CHAT}",
        expected = s"Unknown system property: "
      )
    ),
    example(
      "expandMavenSettings should expand unknown environment variables to the empty string",
      assertExpansion(
        input = "Unknown environment variable: ${IF_THIS_EXISTS_I_WORRY_ABOUT_YOU}",
        expected = s"Unknown environment variable: "
      )
    ),
    example(
      "expandMavenSettings should preserve backslashes in environment variable values", {
        val path = """C:\foo\bar\baz"""
        val env = Map("SOME_PATH" -> path)

        Result.assert(Resolver.expandMavenSettings("${env.SOME_PATH}", env) == path)
      }
    ),
    property("combineDefaultResolvers preserves the input resolvers", propPreserve)
  )

  def gen[A1: Gen]: Gen[A1] = summon[Gen[A1]]

  given Gen[Resolver] = Gen.frequency1(
    1 -> Gen.constant(Resolver.file("/tmp/foo")),
    1 -> Gen.constant(Resolver.mavenLocal),
  )

  def propPreserve: Property =
    for r <- gen[Resolver].forAll
    yield Result.assert(
      Resolver.combineDefaultResolvers(Vector(r)).contains(r)
    )

  // - Helper functions ----------------------------------------------------------------------------
  // -----------------------------------------------------------------------------------------------
  inline def assertExpansion(input: String, expected: String) =
    Result.assert(Resolver.expandMavenSettings(input) == s"$expected")

  def env(name: String) = sys.env.getOrElse(name, "")
  def prop(name: String) = sys.props.getOrElse(name, "")
end ResolverExtraTest
