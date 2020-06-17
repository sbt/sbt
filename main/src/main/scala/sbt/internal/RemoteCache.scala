/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import Keys._
import SlashSyntax0._
import Project._ // for tag and inTask()
import std.TaskExtra._ // for join
import sbt.coursierint.LMCoursier
import sbt.librarymanagement._
import sbt.librarymanagement.ivy.Credentials
import sbt.librarymanagement.syntax._
import sbt.internal.librarymanagement._
import sbt.io.IO
import sbt.io.syntax._
import sbt.internal.inc.JarUtils
import sbt.util.Logger

object RemoteCache {
  final val cachedCompileClassifier = "cached-compile"
  final val cachedTestClasifier = "cached-test"
  final val commitLength = 10

  def gitCommitId: String =
    scala.sys.process.Process("git rev-parse HEAD").!!.trim.take(commitLength)
  def gitCommitIds(n: Int): List[String] =
    scala.sys.process
      .Process("git log -n " + n.toString + " --format=%H")
      .!!
      .linesIterator
      .toList
      .map(_.take(commitLength))

  lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    remoteCacheId := gitCommitId,
    remoteCacheIdCandidates := gitCommitIds(5),
    pushRemoteCacheTo :== None,
  )

  lazy val projectSettings: Seq[Def.Setting[_]] = (Seq(
    remoteCacheProjectId := {
      val o = organization.value
      val m = moduleName.value
      val id = remoteCacheId.value
      val c = (projectID / crossVersion).value
      val v = toVersion(id)
      ModuleID(o, m, v).cross(c)
    },
    pushRemoteCacheConfiguration / publishMavenStyle := true,
    pushRemoteCacheConfiguration / artifacts := artifactDefs(defaultArtifactTasks).value,
    pushRemoteCacheConfiguration / packagedArtifacts := packaged(defaultArtifactTasks).value,
    Compile / packageCache / pushRemoteCacheArtifact := true,
    Test / packageCache / pushRemoteCacheArtifact := true,
    Compile / packageCache / artifact := Artifact(moduleName.value, cachedCompileClassifier),
    Test / packageCache / artifact := Artifact(moduleName.value, cachedTestClasifier),
    remoteCachePom / pushRemoteCacheArtifact := true,
    pushRemoteCacheConfiguration := {
      Classpaths.publishConfig(
        (pushRemoteCacheConfiguration / publishMavenStyle).value,
        Classpaths.deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        (pushRemoteCacheConfiguration / packagedArtifacts).value.toVector,
        (pushRemoteCacheConfiguration / checksums).value.toVector,
        Classpaths.getPublishTo(pushRemoteCacheTo.value).name,
        ivyLoggingLevel.value,
        isSnapshot.value
      )
    },
    pullRemoteCache := {
      val s = streams.value
      val smi = scalaModuleInfo.value
      val dr = (pullRemoteCache / dependencyResolution).value
      val is = (pushRemoteCache / ivySbt).value
      val t = crossTarget.value / "cache-download"
      val p = remoteCacheProjectId.value
      val ids = remoteCacheIdCandidates.value
      val compileAf = (Compile / compileAnalysisFile).value
      val compileOutput = (Compile / classDirectory).value
      val testAf = (Test / compileAnalysisFile).value
      val testOutput = (Test / classDirectory).value
      val testStreams = (Test / test / streams).value
      val testResult = Defaults.succeededFile(testStreams.cacheDirectory)
      var found = false
      ids foreach {
        id: String =>
          val v = toVersion(id)
          val modId = p.withRevision(v)
          if (found) ()
          else
            pullFromMavenRepo0(modId, smi, is, dr, t, s.log) match {
              case Right(xs0) =>
                val xs = xs0.distinct
                xs.find(_.toString.endsWith(s"$v-$cachedCompileClassifier.jar")) foreach {
                  jar: File =>
                    extractCache(jar, compileOutput, compileAf, None)
                }
                xs.find(_.toString.endsWith(s"$v-$cachedTestClasifier.jar")) foreach { jar: File =>
                  extractCache(jar, testOutput, testAf, Some(testResult))
                }
                found = true
              case Left(unresolvedWarning) =>
                s.log.info(s"remote cache not found for ${v}")
            }
      }
    },
    remoteCachePom := {
      val s = streams.value
      val config = makePomConfiguration.value
      val publisher = Keys.publisher.value
      publisher.makePomFile((pushRemoteCache / ivyModule).value, config, s.log)
      config.file.get
    },
    remoteCachePom / packagedArtifact := ((makePom / artifact).value -> remoteCachePom.value),
  ) ++ inTask(pushRemoteCache)(
    Seq(
      ivyConfiguration := {
        val other = pushRemoteCacheTo.value.toVector
        val config0 = Classpaths.mkIvyConfiguration.value
        config0
          .withOtherResolvers(other)
          .withResolutionCacheDir(crossTarget.value / "alt-resolution")
      },
      ivySbt := {
        val config0 = ivyConfiguration.value
        Credentials.register(credentials.value, streams.value.log)
        new IvySbt(config0, CustomHttp.okhttpClient.value)
      },
      ivyModule := {
        val is = ivySbt.value
        new is.Module(moduleSettings.value)
      },
      moduleSettings := {
        val smi = scalaModuleInfo.value
        ModuleDescriptorConfiguration(remoteCacheProjectId.value, projectInfo.value)
          .withScalaModuleInfo(smi)
      },
      pushRemoteCache.in(Defaults.TaskZero) := (Def.task {
        val s = streams.value
        val config = pushRemoteCacheConfiguration.value
        IvyActions.publish(ivyModule.value, config, s.log)
      } tag (Tags.Publish, Tags.Network)).value,
    )
  ) ++ inTask(pullRemoteCache)(
    Seq(
      dependencyResolution := Defaults.dependencyResolutionTask.value,
      csrConfiguration := {
        val rs = pushRemoteCacheTo.value.toVector
        LMCoursier.scalaCompilerBridgeConfigurationTask.value
          .withResolvers(rs)
      }
    )
  ) ++ inConfig(Compile)(packageCacheSettings)
    ++ inConfig(Test)(packageCacheSettings))

  def packageCacheSettings: Seq[Def.Setting[_]] =
    inTask(packageCache)(
      Seq(
        packageCache.in(Defaults.TaskZero) := {
          val original = packageBin.in(Defaults.TaskZero).value
          val artp = artifactPath.value
          val af = compileAnalysisFile.value
          IO.copyFile(original, artp)
          if (af.exists) {
            JarUtils.includeInJar(artp, Vector(af -> s"META-INF/inc_compile.zip"))
          }
          // val testStream = (test / streams).?.value
          // testStream foreach { s =>
          //   val sf = Defaults.succeededFile(s.cacheDirectory)
          //   if (sf.exists) {
          //     JarUtils.includeInJar(artp, Vector(sf -> s"META-INF/succeeded_tests"))
          //   }
          // }
          artp
        },
        packagedArtifact := (artifact.value -> packageCache.value),
        artifactPath := Defaults.artifactPathSetting(artifact).value,
      )
    )

  private def toVersion(v: String): String = s"0.0.0-$v"

  private def pullFromMavenRepo0(
      modId: ModuleID,
      smi: Option[ScalaModuleInfo],
      is: IvySbt,
      dr: DependencyResolution,
      cacheDir: File,
      log: Logger
  ): Either[UnresolvedWarning, Vector[File]] = {
    def dummyModule(deps: Vector[ModuleID]): ModuleDescriptorConfiguration = {
      val module = ModuleID("com.example.temp", "fake", "0.1.0-SNAPSHOT")
      val info = ModuleInfo("fake", "", None, None, Vector(), "", None, None, Vector())
      ModuleDescriptorConfiguration(module, info)
        .withScalaModuleInfo(smi)
        .withDependencies(deps)
    }
    val deps =
      Vector(modId.classifier(cachedCompileClassifier), modId.classifier(cachedTestClasifier))
    val mconfig = dummyModule(deps)
    val m = new is.Module(mconfig)
    dr.retrieve(m, cacheDir, log)
  }

  private def extractCache(
      jar: File,
      output: File,
      analysisFile: File,
      testResult: Option[File]
  ): Unit = {
    IO.delete(output)
    IO.unzip(jar, output)
    val metaDir = output / "META-INF"
    val expandedAnalysis = metaDir / "inc_compile.zip"
    if (expandedAnalysis.exists) {
      IO.move(expandedAnalysis, analysisFile)
    }
    IO.delete(metaDir)
    // testResult match {
    //   case Some(r) =>
    //     val expandedTestResult = output / "META-INF" / "succeeded_tests"
    //     if (expandedTestResult.exists) {
    //       IO.move(expandedTestResult, r)
    //     }
    //   case _ => ()
    // }
    ()
  }

  private def defaultArtifactTasks: Seq[TaskKey[File]] =
    Seq(remoteCachePom, Compile / packageCache, Test / packageCache)

  private def packaged(pkgTasks: Seq[TaskKey[File]]): Def.Initialize[Task[Map[Artifact, File]]] =
    enabledOnly(packagedArtifact.toSettingKey, pkgTasks) apply (_.join.map(_.toMap))

  private def artifactDefs(pkgTasks: Seq[TaskKey[File]]): Def.Initialize[Seq[Artifact]] =
    enabledOnly(artifact, pkgTasks)

  private def enabledOnly[A](
      key: SettingKey[A],
      pkgTasks: Seq[TaskKey[File]]
  ): Def.Initialize[Seq[A]] =
    (Classpaths.forallIn(key, pkgTasks) zipWith
      Classpaths.forallIn(pushRemoteCacheArtifact, pkgTasks))(_ zip _ collect {
      case (a, true) => a
    })
}
