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
    val coursierVerbosity = Keys.coursierVerbosity
    val coursierResolvers = Keys.coursierResolvers
    val coursierSbtResolvers = Keys.coursierSbtResolvers
    val coursierFallbackDependencies = Keys.coursierFallbackDependencies
    val coursierCache = Keys.coursierCache
    val coursierProject = Keys.coursierProject
    val coursierProjects = Keys.coursierProjects
    val coursierPublications = Keys.coursierPublications
    val coursierSbtClassifiersModule = Keys.coursierSbtClassifiersModule

    val coursierConfigurations = Keys.coursierConfigurations

    val coursierResolution = Keys.coursierResolution
    val coursierSbtClassifiersResolution = Keys.coursierSbtClassifiersResolution

    val coursierDependencyTree = Keys.coursierDependencyTree
    val coursierDependencyInverseTree = Keys.coursierDependencyInverseTree
  }

  import autoImport._

  lazy val treeSettings = Seq(
    coursierDependencyTree <<= Tasks.coursierDependencyTreeTask(
      inverse = false
    ),
    coursierDependencyInverseTree <<= Tasks.coursierDependencyTreeTask(
      inverse = true
    )
  )

  override lazy val projectSettings = Seq(
    coursierParallelDownloads := 6,
    coursierMaxIterations := 50,
    coursierChecksums := Seq(Some("SHA-1"), None),
    coursierArtifactsChecksums := Seq(None),
    coursierCachePolicies := Settings.defaultCachePolicies,
    coursierVerbosity := Settings.defaultVerbosityLevel,
    coursierResolvers <<= Tasks.coursierResolversTask,
    coursierSbtResolvers <<= externalResolvers in updateSbtClassifiers,
    coursierFallbackDependencies <<= Tasks.coursierFallbackDependenciesTask,
    coursierCache := Cache.default,
    update <<= Tasks.updateTask(withClassifiers = false),
    updateClassifiers <<= Tasks.updateTask(
      withClassifiers = true,
      ignoreArtifactErrors = true
    ),
    updateSbtClassifiers in Defaults.TaskGlobal <<= Tasks.updateTask(
      withClassifiers = true,
      sbtClassifiers = true,
      ignoreArtifactErrors = true
    ),
    coursierProject <<= Tasks.coursierProjectTask,
    coursierProjects <<= Tasks.coursierProjectsTask,
    coursierPublications <<= Tasks.coursierPublicationsTask,
    coursierSbtClassifiersModule <<= classifiersModule in updateSbtClassifiers,
    coursierConfigurations <<= Tasks.coursierConfigurationsTask,
    coursierResolution <<= Tasks.resolutionTask(),
    coursierSbtClassifiersResolution <<= Tasks.resolutionTask(
      sbtClassifiers = true
    )
  ) ++
  inConfig(Compile)(treeSettings) ++
  inConfig(Test)(treeSettings)

}
