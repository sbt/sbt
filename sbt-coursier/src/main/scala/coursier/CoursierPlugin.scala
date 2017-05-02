package coursier

import sbt._
import sbt.Keys._

object CoursierPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = sbt.plugins.IvyPlugin

  object autoImport {
    val coursierParallelDownloads = Keys.coursierParallelDownloads
    val coursierMaxIterations = Keys.coursierMaxIterations
    val coursierChecksums = Keys.coursierChecksums
    val coursierArtifactsChecksums = Keys.coursierArtifactsChecksums
    val coursierCachePolicies = Keys.coursierCachePolicies
    val coursierTtl = Keys.coursierTtl
    val coursierVerbosity = Keys.coursierVerbosity
    val mavenProfiles = Keys.mavenProfiles
    val coursierResolvers = Keys.coursierResolvers
    val coursierRecursiveResolvers = Keys.coursierRecursiveResolvers
    val coursierSbtResolvers = Keys.coursierSbtResolvers
    val coursierUseSbtCredentials = Keys.coursierUseSbtCredentials
    val coursierCredentials = Keys.coursierCredentials
    val coursierFallbackDependencies = Keys.coursierFallbackDependencies
    val coursierCache = Keys.coursierCache
    val coursierProject = Keys.coursierProject
    val coursierInterProjectDependencies = Keys.coursierInterProjectDependencies
    val coursierPublications = Keys.coursierPublications
    val coursierSbtClassifiersModule = Keys.coursierSbtClassifiersModule

    val coursierConfigurations = Keys.coursierConfigurations

    val coursierParentProjectCache = Keys.coursierParentProjectCache
    val coursierResolution = Keys.coursierResolution
    val coursierSbtClassifiersResolution = Keys.coursierSbtClassifiersResolution

    val coursierDependencyTree = Keys.coursierDependencyTree
    val coursierDependencyInverseTree = Keys.coursierDependencyInverseTree

    val coursierArtifacts = Keys.coursierArtifacts
    val coursierClassifiersArtifacts = Keys.coursierClassifiersArtifacts
    val coursierSbtClassifiersArtifacts = Keys.coursierSbtClassifiersArtifacts
  }

  import autoImport._

  lazy val treeSettings = Seq(
    coursierDependencyTree := Tasks.coursierDependencyTreeTask(
      inverse = false
    ).value,
    coursierDependencyInverseTree := Tasks.coursierDependencyTreeTask(
      inverse = true
    ).value
  )

  def makeIvyXmlBefore[T](
    task: TaskKey[T],
    shadedConfigOpt: Option[(String, String)]
  ): Setting[Task[T]] =
    // not 100% sure that make writeFiles below happen before the actions triggered by task.value...
    task := {
      val currentProject = {
        val proj = coursierProject.value
        val publications = coursierPublications.value
        proj.copy(publications = publications)
      }
      IvyXml.writeFiles(currentProject, shadedConfigOpt, ivySbt.value, streams.value.log)
      task.value
    }

  def coursierSettings(
    shadedConfigOpt: Option[(String, String)],
    packageConfigs: Seq[(Configuration, String)]
  ) = Seq(
    coursierParallelDownloads := 6,
    coursierMaxIterations := 50,
    coursierChecksums := Seq(Some("SHA-1"), None),
    coursierArtifactsChecksums := Seq(None),
    coursierCachePolicies := CachePolicy.default,
    coursierTtl := Cache.defaultTtl,
    coursierVerbosity := Settings.defaultVerbosityLevel(sLog.value),
    mavenProfiles := Set.empty,
    coursierResolvers := Tasks.coursierResolversTask.value,
    coursierRecursiveResolvers := Tasks.coursierRecursiveResolversTask.value,
    coursierSbtResolvers := externalResolvers.in(updateSbtClassifiers).value,
    coursierUseSbtCredentials := true,
    coursierCredentials := Map.empty,
    coursierFallbackDependencies := Tasks.coursierFallbackDependenciesTask.value,
    coursierCache := Cache.default,
    coursierArtifacts := Tasks.artifactFilesOrErrors(withClassifiers = false).value,
    coursierClassifiersArtifacts := Tasks.artifactFilesOrErrors(
      withClassifiers = true
    ).value,
    coursierSbtClassifiersArtifacts := Tasks.artifactFilesOrErrors(
      withClassifiers = true,
      sbtClassifiers = true
    ).value,
    makeIvyXmlBefore(deliverLocalConfiguration, shadedConfigOpt),
    makeIvyXmlBefore(deliverConfiguration, shadedConfigOpt),
    update := Tasks.updateTask(
      shadedConfigOpt,
      withClassifiers = false
    ).value,
    updateClassifiers := Tasks.updateTask(
      shadedConfigOpt,
      withClassifiers = true,
      ignoreArtifactErrors = true
    ).value,
    updateSbtClassifiers.in(Defaults.TaskGlobal) := Tasks.updateTask(
      shadedConfigOpt,
      withClassifiers = true,
      sbtClassifiers = true,
      ignoreArtifactErrors = true
    ).value,
    coursierProject := Tasks.coursierProjectTask.value,
    coursierInterProjectDependencies := Tasks.coursierInterProjectDependenciesTask.value,
    coursierPublications := Tasks.coursierPublicationsTask(packageConfigs: _*).value,
    coursierSbtClassifiersModule := classifiersModule.in(updateSbtClassifiers).value,
    coursierConfigurations := Tasks.coursierConfigurationsTask(None).value,
    coursierParentProjectCache := Tasks.parentProjectCacheTask.value,
    coursierResolution := Tasks.resolutionTask().value,
    coursierSbtClassifiersResolution := Tasks.resolutionTask(
      sbtClassifiers = true
    ).value
  )

  override lazy val projectSettings = coursierSettings(None, Seq(Compile, Test).map(c => c -> c.name)) ++
    inConfig(Compile)(treeSettings) ++
    inConfig(Test)(treeSettings)

}
