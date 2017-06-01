package coursier

import sbt._
import sbt.Keys._

import SbtCompatibility._

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
    val coursierConfigGraphs = Keys.coursierConfigGraphs
    val coursierInterProjectDependencies = Keys.coursierInterProjectDependencies
    val coursierPublications = Keys.coursierPublications
    val coursierSbtClassifiersModule = Keys.coursierSbtClassifiersModule

    val coursierConfigurations = Keys.coursierConfigurations

    val coursierParentProjectCache = Keys.coursierParentProjectCache
    val coursierResolutions = Keys.coursierResolutions

    @deprecated("Use coursierResolutions instead", "1.0.0-RC4")
    val coursierResolution = Keys.actualCoursierResolution

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
    coursierConfigGraphs := Tasks.ivyGraphsTask.value,
    coursierInterProjectDependencies := Tasks.coursierInterProjectDependenciesTask.value,
    coursierPublications := Tasks.coursierPublicationsTask(packageConfigs: _*).value,
    coursierSbtClassifiersModule := classifiersModule.in(updateSbtClassifiers).value,
    coursierConfigurations := Tasks.coursierConfigurationsTask(None).value,
    coursierParentProjectCache := Tasks.parentProjectCacheTask.value,
    coursierResolutions := Tasks.resolutionsTask().value,
    Keys.actualCoursierResolution := {

      val config = Compile.name

      coursierResolutions
        .value
        .collectFirst {
          case (configs, res) if (configs(config)) =>
            res
        }
        .getOrElse {
          sys.error(s"Resolution for configuration $config not found")
        }
    },
    coursierSbtClassifiersResolution := Tasks.resolutionsTask(
      sbtClassifiers = true
    ).value.head._2,
    ivyConfigurations := {
      val confs = ivyConfigurations.value
      val names = confs.map(_.name).toSet

      // Yes, adding those back in sbt 1.0. Can't distinguish between config test (whose jars with classifier tests ought to
      // be added), and sources / docs else (if their JARs are in compile, they would get added too then).

      val extraSources =
        if (names("sources"))
          None
        else
          Some(Configuration("sources", "", isPublic = true, extendsConfigs = Vector.empty, transitive = false))

      val extraDocs =
        if (names("docs"))
          None
        else
          Some(Configuration("docs", "", isPublic = true, extendsConfigs = Vector.empty, transitive = false))

      confs ++ extraSources.toSeq ++ extraDocs.toSeq
    }
  )

  override lazy val projectSettings = coursierSettings(None, Seq(Compile, Test).map(c => c -> c.name)) ++
    inConfig(Compile)(treeSettings) ++
    inConfig(Test)(treeSettings)

}
