package sbt.librarymanagement

import java.net.URI

import hedgehog.*
import hedgehog.runner.*

object ResolverTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "Resolver uri should propagate pattern descriptorOptional and skipConsistencyCheck",
      testUriPropagatesPatterns
    ),
    example(
      "Patterns should default to isMavenCompatible = false (literal [organisation], see #535)",
      Patterns().isMavenCompatible ==== false
    ),
    example(
      "Patterns should default the varargs shorthand to isMavenCompatible = false, like the builder forms",
      Patterns("[organisation]/[module]/[artifact]-[revision].[ext]").isMavenCompatible ==== false
    ),
    example(
      "Resolver.mavenStylePatterns should stay Maven-compatible (isMavenCompatible = true)",
      Resolver.mavenStylePatterns.isMavenCompatible ==== true
    ),
    example(
      "Resolver.ivyStylePatterns should be Ivy-style (isMavenCompatible = false)",
      Resolver.ivyStylePatterns.isMavenCompatible ==== false
    ),
    example(
      "Resolver.sftp should keep an Ivy SFTP resolver's custom [organisation] patterns literal by default (#535)",
      testSftpCustomPatterns
    ),
    example(
      "Resolver.sftp should stay Maven-compatible when constructed with the default mavenStylePatterns",
      Resolver.sftp("repo", "example.org", 22).patterns.isMavenCompatible ==== true
    ),
    example(
      "Resolver.sftp should keep the issue #535 patterns literal when set via withPatterns (both org spellings)",
      testSftpWithPatterns
    ),
  )

  def testUriPropagatesPatterns: Result =
    val pats = Vector("[orgPath]")
    val patsExpected = Vector("http://foo.com/test/[orgPath]")
    val patterns = Resolver
      .uri("test", new URI("http://foo.com/test"))(using
        Patterns(
          pats,
          pats,
          isMavenCompatible = false,
          descriptorOptional = true,
          skipConsistencyCheck = true
        )
      )
      .patterns
    Result.all(
      List(
        patterns.ivyPatterns ==== patsExpected,
        patterns.artifactPatterns ==== patsExpected,
        patterns.isMavenCompatible ==== false,
        Result.assert(patterns.skipConsistencyCheck),
        Result.assert(patterns.descriptorOptional),
      )
    )
  end testUriPropagatesPatterns

  def testSftpCustomPatterns: Result =
    val ivy = "/var/ivy/repo/[organisation]/[module]/ivys/ivy-[revision].xml"
    val artifact = "/var/ivy/repo/[organisation]/[module]/[type]s/[artifact]-[revision].[ext]"
    val patterns = Resolver
      .sftp("repo", "example.org", 22)(using
        Patterns().withIvyPatterns(Vector(ivy)).withArtifactPatterns(Vector(artifact))
      )
      .patterns
    Result.all(
      List(
        patterns.ivyPatterns ==== Vector(ivy),
        patterns.artifactPatterns ==== Vector(artifact),
        patterns.isMavenCompatible ==== false,
      )
    )

  def testSftpWithPatterns: Result =
    val ivys = "/var/ivy/cirque-repo/[organisation]/[module]/ivys/ivy-[revision].xml"
    val artifacts =
      "/var/ivy/cirque-repo/[organization]/[module]/[type]s/[artifact]-[revision].[ext]"
    val repo = Resolver
      .sftp("Cirque-ivy-repo", "daisyduck.cirque.dk", 22)
      .withPatterns(
        Patterns().withIvyPatterns(Vector(ivys)).withArtifactPatterns(Vector(artifacts))
      )
    Result.all(
      List(
        repo.patterns.ivyPatterns ==== Vector(ivys),
        repo.patterns.artifactPatterns ==== Vector(artifacts),
        repo.patterns.isMavenCompatible ==== false,
      )
    )
end ResolverTest
