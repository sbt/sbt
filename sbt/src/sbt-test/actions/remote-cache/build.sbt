import sbt.internal.remotecache.CustomRemoteCacheArtifact

lazy val root = (project in file("."))
  .configs(CustomArtifact)
  .settings(
    name := "my-project",
    scalaVersion := "2.12.11",
    pushRemoteCacheTo := Some(
      MavenCache("local-cache", (ThisBuild / baseDirectory).value / "remote-cache")
    ),
    remoteCacheId := "fixed-id",
    remoteCacheIdCandidates := Seq("fixed-id"),
    pushRemoteCacheConfiguration := pushRemoteCacheConfiguration.value.withOverwrite(true),
    pushRemoteCacheConfiguration / remoteCacheArtifacts += {
      val art = (CustomArtifact / artifact).value
      val packaged = CustomArtifact / packageCache
      val extractDirectory = (CustomArtifact / sourceManaged).value

      CustomRemoteCacheArtifact(art, packaged, extractDirectory, preserveLastModified = false)
    },
    Compile / sourceGenerators += Def.task {
      val extractDirectory = (CustomArtifact / sourceManaged).value
      val output = extractDirectory / "HelloWorld.scala"
      IO.write(output, "class HelloWorld")
      Seq(output)
    }.taskValue
  )
  .settings(customArtifactSettings)

lazy val CustomArtifact = config("custom-artifact")

def customArtifactSettings: Seq[Def.Setting[_]] = {
  val classifier = "custom-artifact"

  inConfig(CustomArtifact)(
    Seq(
      packageOptions := {
        val n = name.value + "-" + classifier
        val ver = version.value
        val orgName = organizationName.value

        List(Package.addSpecManifestAttributes(n, ver, orgName))
      },
      sourceManaged := (Compile / target).value / "custom-artifact-gen",
      mappings := {
        val sourcesDir = sourceManaged.value
        val sources = sourcesDir.allPaths.pair(Path.relativeTo(sourcesDir))

        sources
      },
      packageConfiguration := Defaults.packageConfigurationTask.value,
      packageCache := Defaults.packageTask.value,
      artifact := Artifact(moduleName.value, classifier),
      packagedArtifact := (artifact.value -> packageCache.value),
      artifactPath := Defaults.artifactPathSetting(artifact).value,
      artifactName := Artifact.artifactName
    )
  )
}