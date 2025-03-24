package sbt.librarymanagement

import java.net.URI

import sbt.internal.librarymanagement.UnitSpec

object ResolverTest extends UnitSpec {

  "Resolver url" should "propagate pattern descriptorOptional and skipConsistencyCheck." in {
    val pats = Vector("[orgPath]")
    val patsExpected = Vector("http://foo.com/test/[orgPath]")
    val patterns = Resolver
      .url("test", new URI("http://foo.com/test").toURL)(
        Patterns(
          pats,
          pats,
          isMavenCompatible = false,
          descriptorOptional = true,
          skipConsistencyCheck = true
        )
      )
      .patterns

    patterns.ivyPatterns shouldBe patsExpected
    patterns.artifactPatterns shouldBe patsExpected
    patterns.isMavenCompatible shouldBe false
    assert(patterns.skipConsistencyCheck)
    assert(patterns.descriptorOptional)
  }
}
