/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package librarymanagement

import sbt.librarymanagement._
import sbt.Keys._
import sbt.ScopeFilter.Make._

private[sbt] object CoursierRepositoriesTasks {
  private object CResolvers {
    private val slowReposBase = Seq(
      "https://repo.typesafe.com/",
      "https://repo.scala-sbt.org/",
      "http://repo.typesafe.com/",
      "http://repo.scala-sbt.org/"
    )

    private val fastReposBase = Seq(
      "http://repo1.maven.org/",
      "https://repo1.maven.org/"
    )

    private def url(res: Resolver): Option[String] =
      res match {
        case m: sbt.librarymanagement.MavenRepository =>
          Some(m.root)
        case u: URLRepository =>
          u.patterns.artifactPatterns.headOption
            .orElse(u.patterns.ivyPatterns.headOption)
        case _ =>
          None
      }

    private def fastRepo(res: Resolver): Boolean =
      url(res).exists(u => fastReposBase.exists(u.startsWith))

    private def slowRepo(res: Resolver): Boolean =
      url(res).exists(u => slowReposBase.exists(u.startsWith))

    def reorderResolvers(resolvers: Seq[Resolver]): Seq[Resolver] =
      if (resolvers.exists(fastRepo) && resolvers.exists(slowRepo)) {
        val (slow, other) = resolvers.partition(slowRepo)
        other ++ slow
      } else
        resolvers
  }

  private def resultTask(
      bootResOpt: Option[Seq[Resolver]],
      overrideFlag: Boolean
  ): Def.Initialize[sbt.Task[Seq[Resolver]]] =
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
            } else
            Def.task(extRes)
        }
    }

  def coursierResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] =
    Def.taskDyn {

      val bootResOpt = bootResolvers.value
      val overrideFlag = overrideBuildResolvers.value

      Def.task {
        val result = resultTask(bootResOpt, overrideFlag).value
        val reorderResolvers = true // coursierReorderResolvers.value
        val keepPreloaded = true // coursierKeepPreloaded.value

        val result0 =
          if (reorderResolvers)
            CResolvers.reorderResolvers(result)
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
      val projects = Project.transitiveInterDependencies(state, projectRef)
      Def.task {
        csrResolvers.all(ScopeFilter(inProjects(projectRef +: projects: _*))).value.flatten
      }
    }
}
