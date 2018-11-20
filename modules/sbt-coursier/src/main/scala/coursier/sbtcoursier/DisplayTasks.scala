package coursier.sbtcoursier

import coursier.core._
import coursier.sbtcoursier.Keys._
import coursier.util.Print.Colors
import coursier.util.{Parse, Print}
import sbt.Def
import sbt.Keys._

import scala.collection.mutable

object DisplayTasks {

  private case class ResolutionResult(configs: Set[Configuration], resolution: Resolution, dependencies: Seq[Dependency])

  private def coursierResolutionTask(
    sbtClassifiers: Boolean = false,
    ignoreArtifactErrors: Boolean = false
  ): Def.Initialize[sbt.Task[Seq[ResolutionResult]]] = {

    val currentProjectTask =
      if (sbtClassifiers)
        Def.task {
          val sv = scalaVersion.value
          val sbv = scalaBinaryVersion.value
          val cm = coursierSbtClassifiersModule.value
          FromSbt.sbtClassifiersProject(cm, sv, sbv)
        }
      else
        Def.task {
          val proj = coursierProject.value
          val publications = coursierPublications.value
          proj.copy(publications = publications)
        }

    Def.taskDyn {

      val config = Configuration(configuration.value.name)
      val configs = coursierConfigurations.value

      val includedConfigs = configs.getOrElse(config, Set.empty) + config

      Def.taskDyn {
        val currentProject = currentProjectTask.value

        val resolutionsTask =
          if (sbtClassifiers)
            Def.task {
              val classifiersRes = coursierSbtClassifiersResolution.value
              Map(currentProject.configurations.keySet -> classifiersRes)
            }
          else
            Def.task(coursierResolutions.value)

        Def.task {
          val resolutions = resolutionsTask.value

          for {
            (subGraphConfigs, res) <- resolutions.toSeq
            if subGraphConfigs.exists(includedConfigs)
          } yield {

            val dependencies0 = currentProject.dependencies.collect {
              case (cfg, dep) if includedConfigs(cfg) && subGraphConfigs(cfg) => dep
            }.sortBy { dep =>
              (dep.module.organization, dep.module.name, dep.version)
            }

            val subRes = res.subset(dependencies0.toSet)

            ResolutionResult(subGraphConfigs, subRes, dependencies0)
          }
        }
      }
    }
  }

  def coursierDependencyTreeTask(
    inverse: Boolean,
    sbtClassifiers: Boolean = false,
    ignoreArtifactErrors: Boolean = false
  ) = Def.task {
    val projectName = thisProjectRef.value.project

    val resolutions = coursierResolutionTask(sbtClassifiers, ignoreArtifactErrors).value
    for (ResolutionResult(subGraphConfigs, resolution, dependencies) <- resolutions) {
      // use sbt logging?
      println(
        s"$projectName (configurations ${subGraphConfigs.toVector.sorted.mkString(", ")})" + "\n" +
          Print.dependencyTree(
            dependencies,
            resolution,
            printExclusions = true,
            inverse,
            colors = !sys.props.get("sbt.log.noformat").toSeq.contains("true")
          )
      )
    }
  }


  def coursierWhatDependsOnTask(
    moduleName: String,
    sbtClassifiers: Boolean = false,
    ignoreArtifactErrors: Boolean = false
  ) = Def.task {
    val module = Parse.module(moduleName, scalaVersion.value)
      .right
      .getOrElse(throw new RuntimeException(s"Could not parse module `$moduleName`"))

    val projectName = thisProjectRef.value.project

    val resolutions = coursierResolutionTask(sbtClassifiers, ignoreArtifactErrors).value
    val result = new mutable.StringBuilder
    for (ResolutionResult(subGraphConfigs, resolution, _) <- resolutions) {
      val roots: Seq[Dependency] = resolution.transitiveDependencies.filter(f => f.module == module)
      val strToPrint = s"$projectName (configurations ${subGraphConfigs.toVector.sorted.map(_.value).mkString(", ")})" + "\n" +
        Print.reverseTree(roots, resolution, withExclusions = true)
          .render(_.repr(Colors.get(!sys.props.get("sbt.log.noformat").toSeq.contains("true"))))
      println(strToPrint)
      result.append(strToPrint)
      result.append("\n")
    }

    result.toString
  }

}
