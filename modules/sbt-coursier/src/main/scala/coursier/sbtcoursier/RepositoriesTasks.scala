package coursier.sbtcoursier

import coursier.sbtcoursier.Keys._
import coursier.sbtcoursier.Structure._
import sbt.{Classpaths, Def}
import sbt.Keys._
import sbt.librarymanagement.Resolver

object RepositoriesTasks {

  private def resultTask(bootResOpt: Option[Seq[Resolver]], overrideFlag: Boolean): Def.Initialize[sbt.Task[Seq[Resolver]]] =
    bootResOpt.filter(_ => overrideFlag) match {
      case Some(r) => Def.task(r)
      case None =>
        Def.taskDyn {
          val extRes = externalResolvers.value
          val isSbtPlugin = sbtPlugin.value
          if (isSbtPlugin)
            Def.task {
              Seq(
                sbtResolver.value,
                Classpaths.sbtPluginReleases
              ) ++ extRes
            }
          else
            Def.task(extRes)
        }
    }

  def coursierResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] =
    Def.taskDyn {

      val bootResOpt = bootResolvers.value
      val overrideFlag = overrideBuildResolvers.value

      Def.task {
        val result = resultTask(bootResOpt, overrideFlag).value
        val reorderResolvers = coursierReorderResolvers.value
        val keepPreloaded = coursierKeepPreloaded.value

        val result0 =
          if (reorderResolvers)
            ResolutionParams.reorderResolvers(result)
          else
            result

        if (keepPreloaded)
          result0
        else
          result0.filter { r =>
            !r.name.startsWith("local-preloaded")
          }
      }
    }

  def coursierRecursiveResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] =
    Def.taskDyn {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val projects = Structure.allRecursiveInterDependencies(state, projectRef)

      val t = coursierResolvers
        .forAllProjects(state, projectRef +: projects)
        .map(_.values.toVector.flatten)

      Def.task(t.value)
    }

}
