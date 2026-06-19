package sbt.librarymanagement

import java.net.URI

import sbt.internal.librarymanagement.UnitSpec

class ResolverTest extends UnitSpec {

  "Resolver url" should "propagate pattern descriptorOptional and skipConsistencyCheck." in {
    val pats = Vector("[orgPath]")
    val patsExpected = Vector("http://foo.com/test/[orgPath]")
    val patterns = Resolver
      .url("test", new URI("http://foo.com/test").toURL)(using
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

  "Patterns" should "default to isMavenCompatible = false (literal [organisation], see #535)." in {
    Patterns().isMavenCompatible shouldBe false
  }

  it should "default the varargs shorthand to isMavenCompatible = false, like the builder forms." in {
    // Patterns("[organisation]/...") must agree with Patterns().withArtifactPatterns(...) so that the
    // organization token is treated consistently regardless of how the Patterns is constructed (#535).
    Patterns("[organisation]/[module]/[artifact]-[revision].[ext]").isMavenCompatible shouldBe false
  }

  "Resolver.mavenStylePatterns" should "stay Maven-compatible (isMavenCompatible = true)." in {
    Resolver.mavenStylePatterns.isMavenCompatible shouldBe true
  }

  "Resolver.ivyStylePatterns" should "be Ivy-style (isMavenCompatible = false)." in {
    Resolver.ivyStylePatterns.isMavenCompatible shouldBe false
  }

  "Resolver.sftp" should "keep an Ivy SFTP resolver's custom [organisation] patterns literal by default." in {
    // Reproduces the configuration from issue #535: a custom Ivy pattern using [organisation].
    // With the isMavenCompatible = false default, sbt stores the token verbatim and instructs the
    // Ivy engine (via setM2compatible(false)) not to rewrite the organization to slash form.
    val ivy = "/var/ivy/repo/[organisation]/[module]/ivys/ivy-[revision].xml"
    val artifact = "/var/ivy/repo/[organisation]/[module]/[type]s/[artifact]-[revision].[ext]"
    val patterns = Resolver
      .sftp("repo", "example.org", 22)(using
        Patterns().withIvyPatterns(Vector(ivy)).withArtifactPatterns(Vector(artifact))
      )
      .patterns
    patterns.ivyPatterns shouldBe Vector(ivy)
    patterns.artifactPatterns shouldBe Vector(artifact)
    patterns.isMavenCompatible shouldBe false
  }

  it should "stay Maven-compatible when constructed with the default mavenStylePatterns." in {
    Resolver.sftp("repo", "example.org", 22).patterns.isMavenCompatible shouldBe true
  }

  // The historical fluent syntax from issue #535, `Resolver.sftp(...).artifacts(...).ivys(...)`, was
  // removed in sbt 2.x; `withPatterns` is the supported replacement. These cases mirror the original
  // report's exact patterns (both the `[organization]` and `[organisation]` spellings the reporter
  // tried) to lock the behavior down from the client side.
  it should "keep the issue #535 patterns literal when set via withPatterns (both org spellings)." in {
    val ivys = "/var/ivy/cirque-repo/[organisation]/[module]/ivys/ivy-[revision].xml"
    val artifacts =
      "/var/ivy/cirque-repo/[organization]/[module]/[type]s/[artifact]-[revision].[ext]"
    val repo = Resolver
      .sftp("Cirque-ivy-repo", "daisyduck.cirque.dk", 22)
      .withPatterns(
        Patterns().withIvyPatterns(Vector(ivys)).withArtifactPatterns(Vector(artifacts))
      )
    repo.patterns.ivyPatterns shouldBe Vector(ivys)
    repo.patterns.artifactPatterns shouldBe Vector(artifacts)
    repo.patterns.isMavenCompatible shouldBe false
  }
}
