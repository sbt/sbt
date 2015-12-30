package coursier

import java.io.File

import sbt._
import sbt.Keys._

object CoursierPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = sbt.plugins.IvyPlugin

  object autoImport {
    val coursierParallelDownloads = Keys.coursierParallelDownloads
    val coursierMaxIterations = Keys.coursierMaxIterations
    val coursierChecksums = Keys.coursierChecksums
    val coursierCachePolicy = Keys.coursierCachePolicy
    val coursierVerbosity = Keys.coursierVerbosity
    val coursierResolvers = Keys.coursierResolvers
    val coursierSbtResolvers = Keys.coursierSbtResolvers
    val coursierCache = Keys.coursierCache
    val coursierProject = Keys.coursierProject
    val coursierProjects = Keys.coursierProjects
    val coursierSbtClassifiersModule = Keys.coursierSbtClassifiersModule
  }

  import autoImport._


  override lazy val projectSettings = Seq(
    coursierParallelDownloads := 6,
    coursierMaxIterations := 50,
    coursierChecksums := Seq(Some("SHA-1"), None),
    coursierCachePolicy := CachePolicy.FetchMissing,
    coursierVerbosity := 1,
    coursierResolvers <<= Tasks.coursierResolversTask,
    coursierSbtResolvers <<= externalResolvers in updateSbtClassifiers,
    coursierCache := new File(sys.props("user.home") + "/.coursier/sbt"),
    update <<= Tasks.updateTask(withClassifiers = false),
    updateClassifiers <<= Tasks.updateTask(withClassifiers = true),
    updateSbtClassifiers in Defaults.TaskGlobal <<= Tasks.updateTask(withClassifiers = true, sbtClassifiers = true),
    coursierProject <<= Tasks.coursierProjectTask,
    coursierProjects <<= Tasks.coursierProjectsTask,
    coursierSbtClassifiersModule <<= classifiersModule in updateSbtClassifiers
  )

}
