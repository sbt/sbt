package coursier.sbtcoursier

import java.io.OutputStreamWriter

import coursier.{Cache, CachePolicy, TermDisplay}
import coursier.core.{Configuration, ResolutionProcess}
import coursier.sbtcoursiershared.SbtCoursierShared
import sbt.{Cache => _, Configuration => _, _}
import sbt.Keys._

object CoursierPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = SbtCoursierShared

  object autoImport {
    val coursierParallelDownloads = Keys.coursierParallelDownloads
    val coursierMaxIterations = Keys.coursierMaxIterations
    val coursierChecksums = Keys.coursierChecksums
    val coursierArtifactsChecksums = Keys.coursierArtifactsChecksums
    val coursierCachePolicies = Keys.coursierCachePolicies
    val coursierTtl = Keys.coursierTtl
    val coursierVerbosity = Keys.coursierVerbosity
    val coursierCache = Keys.coursierCache
    val coursierConfigGraphs = Keys.coursierConfigGraphs
    val coursierSbtClassifiersModule = Keys.coursierSbtClassifiersModule

    val coursierConfigurations = Keys.coursierConfigurations

    val coursierParentProjectCache = Keys.coursierParentProjectCache
    val coursierResolutions = Keys.coursierResolutions

    val coursierSbtClassifiersResolution = Keys.coursierSbtClassifiersResolution

    val coursierDependencyTree = Keys.coursierDependencyTree
    val coursierDependencyInverseTree = Keys.coursierDependencyInverseTree
    val coursierWhatDependsOn = Keys.coursierWhatDependsOn

    val coursierArtifacts = Keys.coursierArtifacts
    val coursierSignedArtifacts = Keys.coursierSignedArtifacts
    val coursierClassifiersArtifacts = Keys.coursierClassifiersArtifacts
    val coursierSbtClassifiersArtifacts = Keys.coursierSbtClassifiersArtifacts

    val coursierCreateLogger = Keys.coursierCreateLogger

    val coursierVersion = coursier.util.Properties.version
    val addSbtCoursier = {
      addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursierVersion)
    }
  }

  import autoImport._

  lazy val treeSettings = Seq(
    coursierDependencyTree := DisplayTasks.coursierDependencyTreeTask(
      inverse = false
    ).value,
    coursierDependencyInverseTree := DisplayTasks.coursierDependencyTreeTask(
      inverse = true
    ).value,
    coursierWhatDependsOn := Def.inputTaskDyn {
      import sbt.complete.DefaultParsers._
      val input = token(SpaceClass ~ NotQuoted, "<arg>").parsed._2
      DisplayTasks.coursierWhatDependsOnTask(input)
    }.evaluated
  )

  // allows to get the actual repo list when sbt starts up
  private val hackHack = Seq(
    // TODO Add docker-based non reg test for that, with sbt-assembly 0.14.5 in ~/.sbt/1.0/plugins/plugins.sbt
    // along with the required extra repo https://repository.jboss.org/nexus/content/repositories/public
    // (required for coursier, because of bad checksums on central)
    appConfiguration.in(updateSbtClassifiers) := {
      val app = appConfiguration.in(updateSbtClassifiers).value

      // hack to trigger https://github.com/sbt/sbt/blob/v1.0.1/main/src/main/scala/sbt/Defaults.scala#L2856,
      // to have the third case be used instead of the second one, at https://github.com/sbt/sbt/blob/v1.0.1/main/src/main/scala/sbt/Defaults.scala#L2069
      new xsbti.AppConfiguration {
        def provider() = {
          import scala.language.reflectiveCalls
          val prov = app.provider()
          val noWarningForDeprecatedStuffProv = prov.asInstanceOf[{
            def mainClass(): Class[_ <: xsbti.AppMain]
          }]
          new xsbti.AppProvider {
            def newMain() = prov.newMain()
            def components() = prov.components()
            def mainClass() = noWarningForDeprecatedStuffProv.mainClass()
            def mainClasspath() = prov.mainClasspath()
            def loader() = prov.loader()
            def scalaProvider() = {
              val scalaProv = prov.scalaProvider()
              val noWarningForDeprecatedStuffScalaProv = scalaProv.asInstanceOf[{
                def libraryJar(): File
                def compilerJar(): File
              }]

              new xsbti.ScalaProvider {
                def app(id: xsbti.ApplicationID) = scalaProv.app(id)
                def loader() = scalaProv.loader()
                def jars() = scalaProv.jars()
                def libraryJar() = noWarningForDeprecatedStuffScalaProv.libraryJar()
                def version() = scalaProv.version()
                def compilerJar() = noWarningForDeprecatedStuffScalaProv.compilerJar()
                def launcher() = {
                  val launch = scalaProv.launcher()
                  new xsbti.Launcher {
                    def app(id: xsbti.ApplicationID, version: String) = launch.app(id, version)
                    def checksums() = launch.checksums()
                    def globalLock() = launch.globalLock()
                    def bootDirectory() = launch.bootDirectory()
                    def appRepositories() = launch.appRepositories()
                    def topLoader() = launch.topLoader()
                    def getScala(version: String) = launch.getScala(version)
                    def getScala(version: String, reason: String) = launch.getScala(version, reason)
                    def getScala(version: String, reason: String, scalaOrg: String) = launch.getScala(version, reason, scalaOrg)
                    def isOverrideRepositories = launch.isOverrideRepositories
                    def ivyRepositories() =
                      throw new NoSuchMethodError("nope")
                    def ivyHome() = launch.ivyHome()
                  }
                }
              }
            }
            def entryPoint() = prov.entryPoint()
            def id() = prov.id()
          }
        }
        def arguments() = app.arguments()
        def baseDirectory() = app.baseDirectory()
      }
    }
  )

  def coursierSettings(
    shadedConfigOpt: Option[(String, Configuration)] = None
  ): Seq[Setting[_]] = hackHack ++ Seq(
    coursierArtifacts := ArtifactsTasks.artifactsTask(withClassifiers = false).value,
    coursierSignedArtifacts := ArtifactsTasks.artifactsTask(withClassifiers = false, includeSignatures = true).value,
    coursierClassifiersArtifacts := ArtifactsTasks.artifactsTask(
      withClassifiers = true
    ).value,
    coursierSbtClassifiersArtifacts := ArtifactsTasks.artifactsTask(
      withClassifiers = true,
      sbtClassifiers = true
    ).value,
    update := UpdateTasks.updateTask(
      shadedConfigOpt,
      withClassifiers = false
    ).value,
    updateClassifiers := UpdateTasks.updateTask(
      shadedConfigOpt,
      withClassifiers = true,
      ignoreArtifactErrors = true
    ).value,
    updateSbtClassifiers.in(Defaults.TaskGlobal) := UpdateTasks.updateTask(
      shadedConfigOpt,
      withClassifiers = true,
      sbtClassifiers = true,
      ignoreArtifactErrors = true
    ).value,
    coursierConfigGraphs := InputsTasks.ivyGraphsTask.value,
    coursierSbtClassifiersModule := classifiersModule.in(updateSbtClassifiers).value,
    coursierConfigurations := InputsTasks.coursierConfigurationsTask(None).value,
    coursierParentProjectCache := InputsTasks.parentProjectCacheTask.value,
    coursierResolutions := ResolutionTasks.resolutionsTask().value,
    Keys.actualCoursierResolution := {

      val config = Configuration(Compile.name)

      coursierResolutions
        .value
        .collectFirst {
          case (configs, res) if configs(config) =>
            res
        }
        .getOrElse {
          sys.error(s"Resolution for configuration $config not found")
        }
    },
    coursierSbtClassifiersResolution := ResolutionTasks.resolutionsTask(
      sbtClassifiers = true
    ).value.head._2
  )

  override lazy val buildSettings = super.buildSettings ++ Seq(
    coursierParallelDownloads := 6,
    coursierMaxIterations := ResolutionProcess.defaultMaxIterations,
    coursierChecksums := Seq(Some("SHA-1"), None),
    coursierArtifactsChecksums := Seq(None),
    coursierCachePolicies := CachePolicy.default,
    coursierTtl := Cache.defaultTtl,
    coursierVerbosity := Settings.defaultVerbosityLevel(sLog.value),
    coursierCache := Cache.default,
    coursierCreateLogger := { () => new TermDisplay(new OutputStreamWriter(System.err)) }
  )

  override lazy val projectSettings = coursierSettings() ++
    inConfig(Compile)(treeSettings) ++
    inConfig(Test)(treeSettings)

}
