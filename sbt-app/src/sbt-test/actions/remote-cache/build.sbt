import sbt.internal.remotecache.CustomRemoteCacheArtifact
import sbt.internal.inc.Analysis
import complete.DefaultParsers._

lazy val CustomArtifact = config("custom-artifact")

// Reset compiler iterations, necessary because tests run in batch mode
val recordPreviousIterations = taskKey[Unit]("Record previous iterations.")
val checkIterations = inputKey[Unit]("Verifies the accumulated number of iterations of incremental compilation.")

ThisBuild / scalaVersion := "2.12.20"
ThisBuild / pushRemoteCacheTo := Some(
  MavenCache("local-cache", (ThisBuild / baseDirectory).value / "r")
)

lazy val root = (project in file("."))
  .configs(CustomArtifact)
  .settings(
    name := "my-project",

    customArtifactSettings,
    pushRemoteCacheConfiguration / remoteCacheArtifacts += (CustomArtifact / packageCache / remoteCacheArtifact).value,

    Compile / pushRemoteCacheConfiguration := (Compile / pushRemoteCacheConfiguration).value.withOverwrite(true),
    Test / pushRemoteCacheConfiguration := (Test / pushRemoteCacheConfiguration).value.withOverwrite(true),

    Compile / sourceGenerators += Def.task {
      val extractDirectory = (CustomArtifact / sourceManaged).value
      val output = extractDirectory / "HelloWorld.scala"
      IO.write(output, "class HelloWorld")
      Seq(output)
    }.taskValue,
    // bring back fixed-id because JDK 8/11 would be different intentionally
    Compile / remoteCacheId := "fixed-id",
    Compile / remoteCacheIdCandidates := Seq((Compile / remoteCacheId).value),
    Test / remoteCacheId := "fixed-id",
    Test / remoteCacheIdCandidates := Seq((Test / remoteCacheId).value),
    CustomArtifact / remoteCacheId := "fixed-id",
    CustomArtifact / remoteCacheIdCandidates := Seq((CustomArtifact / remoteCacheId).value),

    // test tasks
    recordPreviousIterations := {
      val log = streams.value.log
      CompileState.previousIterations = {
        val previousAnalysis = (previousCompile in Compile).value.analysis.asScala
        previousAnalysis match {
          case None =>
            log.info("No previous analysis detected")
            0
          case Some(a: Analysis) => a.compilations.allCompilations.size
        }
      }
    },
    checkIterations := {
      val expected: Int = (Space ~> NatBasic).parsed
      val actual: Int = ((compile in Compile).value match { case a: Analysis => a.compilations.allCompilations.size }) - CompileState.previousIterations
      assert(expected == actual, s"Expected $expected compilations, got $actual")
    }
  )

def customArtifactSettings: Seq[Def.Setting[_]] = {
  val classifier = "custom-artifact"

  def cachedArtifactTask = Def.task {
    val art = (CustomArtifact / artifact).value
    val packaged = CustomArtifact / packageCache
    val extractDirectory = (CustomArtifact / sourceManaged).value
    CustomRemoteCacheArtifact(art, packaged, extractDirectory, preserveLastModified = false)
  }
  inConfig(CustomArtifact)(
    sbt.internal.RemoteCache.configCacheSettings(cachedArtifactTask) ++
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