package sbt.librarymanagement

import hedgehog.*
import hedgehog.runner.*

object ArtifactTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "Artifact.artifactName should include the platform suffix before the Scala suffix",
      testPlatformBeforeScala
    ),
    example("Artifact.artifactName should omit the platform suffix for jvm", testOmitJvm),
    example(
      "Artifact.artifactName should omit platform and cross suffix when crossVersion is disabled",
      testOmitWhenDisabled
    ),
    example(
      "Artifact.artifactName should produce a filename whose base matches the Maven coordinate (#9117)",
      testMatchesMavenCoordinate
    ),
  )

  def testPlatformBeforeScala: Result =
    val m = ModuleID("com.example", "root-178", "0.1.0-SNAPSHOT")
      .cross(CrossVersion.binary)
      .platform("native0.5")
    val a = Artifact("root-178", "pom", "pom")
    val sv = ScalaVersion("3.8.3", "3")
    Artifact.artifactName(sv, m, a) ==== "root-178_native0.5_3-0.1.0-SNAPSHOT.pom"

  def testOmitJvm: Result =
    val m = ModuleID("com.example", "foo", "1.0.0")
      .cross(CrossVersion.binary)
      .platform(Platform.jvm)
    val a = Artifact("foo", "jar", "jar")
    Artifact.artifactName(ScalaVersion("3.8.3", "3"), m, a) ==== "foo_3-1.0.0.jar"

  def testOmitWhenDisabled: Result =
    val m = ModuleID("com.example", "foo", "1.0.0").platform("native0.5")
    val a = Artifact("foo", "jar", "jar")
    Artifact.artifactName(ScalaVersion("3.8.3", "3"), m, a) ==== "foo-1.0.0.jar"

  def testMatchesMavenCoordinate: Result =
    val m = ModuleID("com.indoorvivants", "sniper", "0.0.9-SNAPSHOT")
      .cross(CrossVersion.binary)
      .platform("native0.5")
    val a = Artifact("sniper", "pom", "pom")
    val sv = ScalaVersion("3.8.3", "3")
    val expectedMavenArtifactId = "sniper_native0.5_3"
    Artifact.artifactName(sv, m, a) ==== s"$expectedMavenArtifactId-0.0.9-SNAPSHOT.pom"
end ArtifactTest
