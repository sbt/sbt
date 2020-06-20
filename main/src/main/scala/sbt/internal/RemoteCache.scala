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
  final val cachedTestClassifier = "cached-test"
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
    pushRemoteCacheTo :== None
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
    pushRemoteCacheConfiguration / packagedArtifacts := Def.taskDyn {
      val artifacts = (pushRemoteCacheConfiguration / remoteCacheArtifacts).value

      artifacts
        .map(a => a.packaged.map(file => (a.artifact, file)))
        .join
        .apply(_.join.map(_.toMap))
    }.value,
    pushRemoteCacheConfiguration / remoteCacheArtifacts := {
      enabledOnly(remoteCacheArtifact.toSettingKey, defaultArtifactTasks).apply(_.join).value
    },
    Compile / packageCache / pushRemoteCacheArtifact := true,
    Test / packageCache / pushRemoteCacheArtifact := true,
    Compile / packageCache / artifact := Artifact(moduleName.value, cachedCompileClassifier),
    Test / packageCache / artifact := Artifact(moduleName.value, cachedTestClassifier),
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
      val log = streams.value.log
      val smi = scalaModuleInfo.value
      val dr = (pullRemoteCache / dependencyResolution).value
      val is = (pushRemoteCache / ivySbt).value
      val t = crossTarget.value / "cache-download"
      val p = remoteCacheProjectId.value
      val ids = remoteCacheIdCandidates.value
      val artifacts = (pushRemoteCacheConfiguration / remoteCacheArtifacts).value
      val applicable = artifacts.filterNot(RemoteCacheArtifact.isPomArtifact)
      val classifiers = applicable.flatMap(_.artifact.classifier).toVector

      var found = false
      ids foreach {
        id: String =>
          val v = toVersion(id)
          val modId = p.withRevision(v)
          if (found) ()
          else
            pullFromMavenRepo0(modId, classifiers, smi, is, dr, t, log) match {
              case Right(xs0) =>
                val jars = xs0.distinct

                applicable.foreach { art =>
                  val classifier = art.artifact.classifier

                  findJar(classifier, v, jars) match {
                    case Some(jar) =>
                      extractJar(art, jar)
                      log.info(s"remote cache artifact extracted for $classifier")

                    case None =>
                      log.info(s"remote cache artifact not found for $classifier")
                  }
                }
                found = true
              case Left(unresolvedWarning) =>
                log.info(s"remote cache not found for ${v}")
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
    remoteCachePom / remoteCacheArtifact := {
      RemoteCacheArtifact.Pom((makePom / artifact).value, remoteCachePom)
    }
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
      } tag (Tags.Publish, Tags.Network)).value
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
  ) ++ inConfig(Compile)(packageCacheSettings(compileArtifact(Compile, cachedCompileClassifier)))
    ++ inConfig(Test)(packageCacheSettings(testArtifact(Test, cachedTestClassifier))))

  private def packageCacheSettings[A <: RemoteCacheArtifact](
      cacheArtifact: Def.Initialize[Task[A]]
  ): Seq[Def.Setting[_]] =
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
        remoteCacheArtifact := cacheArtifact.value,
        packagedArtifact := (artifact.value -> packageCache.value),
        artifactPath := Defaults.artifactPathSetting(artifact).value
      )
    )

  def compileArtifact(
      configuration: Configuration,
      classifier: String
  ): Def.Initialize[Task[RemoteCacheArtifact.Compile]] = Def.task {
    RemoteCacheArtifact.Compile(
      Artifact(moduleName.value, classifier),
      configuration / packageCache,
      (configuration / classDirectory).value,
      (configuration / compileAnalysisFile).value
    )
  }

  def testArtifact(
      configuration: Configuration,
      classifier: String
  ): Def.Initialize[Task[RemoteCacheArtifact.Test]] = Def.task {
    RemoteCacheArtifact.Test(
      Artifact(moduleName.value, classifier),
      configuration / packageCache,
      (configuration / classDirectory).value,
      (configuration / compileAnalysisFile).value,
      Defaults.succeededFile((configuration / test / streams).value.cacheDirectory)
    )
  }

  private def toVersion(v: String): String = s"0.0.0-$v"

  private def pullFromMavenRepo0(
      modId: ModuleID,
      classifiers: Vector[String],
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
    val deps = classifiers.map(modId.classifier)
    val mconfig = dummyModule(deps)
    val m = new is.Module(mconfig)
    dr.retrieve(m, cacheDir, log)
  }

  private def findJar(classifier: Option[String], ver: String, jars: Vector[File]): Option[File] = {
    val suffix = classifier.fold(ver)(c => s"$ver-$c.jar")
    jars.find(_.toString.endsWith(suffix))
  }

  private def extractJar(cacheArtifact: RemoteCacheArtifact, jar: File): Unit =
    cacheArtifact match {
      case RemoteCacheArtifact.Compile(_, _, extractDirectory, analysisFile) =>
        extractCache(jar, extractDirectory, preserveLastModified = true) { output =>
          extractAnalysis(output, analysisFile)
        }

      case RemoteCacheArtifact.Test(_, _, extractDirectory, analysisFile, testResult) =>
        extractCache(jar, extractDirectory, preserveLastModified = true) { output =>
          extractAnalysis(output, analysisFile)
          extractTestResult(output, testResult)
        }

      case RemoteCacheArtifact.Custom(_, _, extractDirectory, preserveLastModified) =>
        extractCache(jar, extractDirectory, preserveLastModified)(_ => ())

      case RemoteCacheArtifact.Pom(_, _) =>
        ()
    }

  private def extractCache(jar: File, output: File, preserveLastModified: Boolean)(
      processOutput: File => Unit
  ): Unit = {
    IO.delete(output)
    IO.unzip(jar, output, preserveLastModified = preserveLastModified)
    processOutput(output)
    IO.delete(output / "META-INF")
  }

  private def extractAnalysis(output: File, analysisFile: File): Unit = {
    val metaDir = output / "META-INF"
    val expandedAnalysis = metaDir / "inc_compile.zip"
    if (expandedAnalysis.exists) {
      IO.move(expandedAnalysis, analysisFile)
    }
    IO.delete(metaDir)
  }

  private def extractTestResult(output: File, testResult: File): Unit = {
    //val expandedTestResult = output / "META-INF" / "succeeded_tests"
    //if (expandedTestResult.exists) {
    //  IO.move(expandedTestResult, testResult)
    //}
  }

  private def defaultArtifactTasks: Seq[TaskKey[File]] =
    Seq(
      remoteCachePom,
      Compile / packageCache,
      Test / packageCache
    )

  private def enabledOnly[A](
      key: SettingKey[A],
      pkgTasks: Seq[TaskKey[File]]
  ): Def.Initialize[Seq[A]] =
    (Classpaths.forallIn(key, pkgTasks) zipWith
      Classpaths.forallIn(pushRemoteCacheArtifact, pkgTasks))(_ zip _ collect {
      case (a, true) => a
    })
}

sealed trait RemoteCacheArtifact {
  def artifact: Artifact
  def packaged: TaskKey[File]
}

object RemoteCacheArtifact {

  final case class Pom(
      artifact: Artifact,
      packaged: TaskKey[File]
  ) extends RemoteCacheArtifact

  final case class Compile(
      artifact: Artifact,
      packaged: TaskKey[File],
      extractDirectory: File,
      analysisFile: File
  ) extends RemoteCacheArtifact

  final case class Test(
      artifact: Artifact,
      packaged: TaskKey[File],
      extractDirectory: File,
      analysisFile: File,
      testResult: File
  ) extends RemoteCacheArtifact

  final case class Custom(
      artifact: Artifact,
      packaged: TaskKey[File],
      extractDirectory: File,
      preserveLastModified: Boolean
  ) extends RemoteCacheArtifact

  def isPomArtifact(artifact: RemoteCacheArtifact): Boolean =
    artifact match {
      case _: RemoteCacheArtifact.Pom => true
      case _                          => false
    }

}
