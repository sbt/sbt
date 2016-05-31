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
    val coursierSourceRepositories = Keys.coursierSourceRepositories
    val coursierResolvers = Keys.coursierResolvers
    val coursierSbtResolvers = Keys.coursierSbtResolvers
    val coursierUseSbtCredentials = Keys.coursierUseSbtCredentials
    val coursierCredentials = Keys.coursierCredentials
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

    val coursierExport = Keys.coursierExport
    val coursierExportDirectory = Keys.coursierExportDirectory
    val coursierExportJavadoc = Keys.coursierExportJavadoc
    val coursierExportSources = Keys.coursierExportSources
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
    coursierCachePolicies := CachePolicy.default,
    coursierTtl := Cache.defaultTtl,
    coursierVerbosity := Settings.defaultVerbosityLevel,
    coursierSourceRepositories := Nil,
    coursierResolvers <<= Tasks.coursierResolversTask,
    coursierSbtResolvers <<= externalResolvers in updateSbtClassifiers,
    coursierUseSbtCredentials := true,
    coursierCredentials := Map.empty,
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
    ),
    coursierExport <<= Tasks.coursierExportTask,
    coursierExportDirectory := baseDirectory.in(ThisBuild).value / "target" / "repository",
    coursierExportJavadoc := false,
    coursierExportSources := false
  ) ++
  inConfig(Compile)(treeSettings) ++
  inConfig(Test)(treeSettings)

}
