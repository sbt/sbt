package coursier.sbtcoursiershared

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import lmcoursier.{Inputs, IvyXml}
import lmcoursier.definitions.{Configuration, Project}
import org.apache.ivy.core.module.id.ModuleRevisionId
import sbt.{Def, Setting, Task, TaskKey}
import sbt.internal.librarymanagement.IvySbt
import sbt.librarymanagement.{CrossVersion, PublishConfiguration}

import scala.collection.JavaConverters._

object IvyXmlGeneration {

  // These are required for publish to be fine, later on.
  private def writeFiles(
    currentProject: Project,
    exclusions: Seq[(String, String)],
    overrides: Seq[(String, String, String)],
    ivySbt: IvySbt,
    log: sbt.util.Logger
  ): File = {

    val ivyCacheManager = ivySbt.withIvy(log)(ivy =>
      ivy.getResolutionCacheManager
    )

    val ivyModule = ModuleRevisionId.newInstance(
      currentProject.module.organization.value,
      currentProject.module.name.value,
      currentProject.version,
      currentProject.module.attributes.asJava
    )

    val cacheIvyFile = ivyCacheManager.getResolvedIvyFileInCache(ivyModule)
    val cacheIvyPropertiesFile = ivyCacheManager.getResolvedIvyPropertiesInCache(ivyModule)

    val content0 = IvyXml(currentProject, exclusions, overrides)
    cacheIvyFile.getParentFile.mkdirs()
    log.info(s"Writing Ivy file $cacheIvyFile")
    Files.write(cacheIvyFile.toPath, content0.getBytes(UTF_8))

    // Just writing an empty file here... Are these only used?
    cacheIvyPropertiesFile.getParentFile.mkdirs()
    Files.write(cacheIvyPropertiesFile.toPath, Array.emptyByteArray)

    cacheIvyFile
  }

  def writeIvyXml: Def.Initialize[Task[File]] =
    Def.task {
      import SbtCoursierShared.autoImport._

      val sv = sbt.Keys.scalaVersion.value
      val sbv = sbt.Keys.scalaBinaryVersion.value
      val log = sbt.Keys.streams.value.log
      val currentProject = {
        val proj = coursierProject.value
        val publications = coursierPublications.value
        proj.withPublications(publications)
      }
      val overrides = Inputs.forceVersions(sbt.Keys.dependencyOverrides.value, sv, sbv).map {
        case (mod, ver) =>
          (mod.organization.value, mod.name.value, ver)
      }
      val excludeDeps = Inputs.exclusionsSeq(InputsTasks.actualExcludeDependencies.value, sv, sbv, log)
        .map {
          case (org, name) =>
            (org.value, name.value)
        }
      writeFiles(currentProject, excludeDeps, overrides, sbt.Keys.ivySbt.value, log)
    }

  private def makeIvyXmlBefore[T](task: TaskKey[T]): Setting[Task[T]] =
    task := task.dependsOn {
      Def.taskDyn {
        import SbtCoursierShared.autoImport._
        val doGen = coursierGenerateIvyXml.value
        if (doGen)
          Def.task {
            coursierWriteIvyXml.value
            ()
          }
        else
          Def.task(())
      }
    }.value

  private lazy val needsIvyXmlLocal = Seq(sbt.Keys.publishLocalConfiguration) ++ getPubConf("makeIvyXmlLocalConfiguration")
  private lazy val needsIvyXml = Seq(sbt.Keys.publishConfiguration) ++ getPubConf("makeIvyXmlConfiguration")

  private[this] def getPubConf(method: String): List[TaskKey[PublishConfiguration]] =
    try {
      val cls = sbt.Keys.getClass
      val m = cls.getMethod(method)
      val task = m.invoke(sbt.Keys).asInstanceOf[TaskKey[PublishConfiguration]]
      List(task)
    } catch {
      case _: Throwable => // FIXME Too wide
        Nil
    }

  def generateIvyXmlSettings: Seq[Setting[_]] =
    (needsIvyXml ++ needsIvyXmlLocal).map(makeIvyXmlBefore)

}
