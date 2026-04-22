package sbt.librarymanagement

import sbt.internal.librarymanagement.UnitSpec

class ArtifactTest extends UnitSpec {

  "Artifact.artifactName" should "include the platform suffix before the Scala suffix" in {
    val m = ModuleID("com.example", "root-178", "0.1.0-SNAPSHOT")
      .cross(CrossVersion.binary)
      .platform("native0.5")
    val a = Artifact("root-178", "pom", "pom")
    val sv = ScalaVersion("3.8.3", "3")
    Artifact.artifactName(sv, m, a) shouldBe "root-178_native0.5_3-0.1.0-SNAPSHOT.pom"
  }

  it should "omit the platform suffix for jvm" in {
    val m = ModuleID("com.example", "foo", "1.0.0")
      .cross(CrossVersion.binary)
      .platform(Platform.jvm)
    val a = Artifact("foo", "jar", "jar")
    Artifact.artifactName(ScalaVersion("3.8.3", "3"), m, a) shouldBe "foo_3-1.0.0.jar"
  }

  it should "omit platform and cross suffix when crossVersion is disabled" in {
    val m = ModuleID("com.example", "foo", "1.0.0").platform("native0.5")
    val a = Artifact("foo", "jar", "jar")
    Artifact.artifactName(ScalaVersion("3.8.3", "3"), m, a) shouldBe "foo-1.0.0.jar"
  }

  it should "produce a filename whose base matches the Maven coordinate (#9117)" in {
    val m = ModuleID("com.indoorvivants", "sniper", "0.0.9-SNAPSHOT")
      .cross(CrossVersion.binary)
      .platform("native0.5")
    val a = Artifact("sniper", "pom", "pom")
    val sv = ScalaVersion("3.8.3", "3")

    val expectedMavenArtifactId = "sniper_native0.5_3"
    Artifact.artifactName(sv, m, a) shouldBe
      s"$expectedMavenArtifactId-0.0.9-SNAPSHOT.pom"
  }
}
