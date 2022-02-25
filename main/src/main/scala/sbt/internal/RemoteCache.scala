/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.nio.file.Path

import org.apache.ivy.core.module.descriptor.{ DefaultArtifact, Artifact => IArtifact }
import org.apache.ivy.core.report.DownloadStatus
import org.apache.ivy.core.resolve.DownloadOptions
import org.apache.ivy.plugins.resolver.DependencyResolver
import sbt.Defaults.prefix
import sbt.Keys._
import sbt.Project._
import sbt.ScopeFilter.Make._
import sbt.SlashSyntax0._
import sbt.coursierint.LMCoursier
import sbt.internal.inc.{ HashUtil, JarUtils }
import sbt.internal.librarymanagement._
import sbt.internal.remotecache._
import sbt.io.IO
import sbt.io.syntax._
import sbt.librarymanagement._
import sbt.librarymanagement.ivy.{ Credentials, IvyPaths, UpdateOptions }
import sbt.librarymanagement.syntax._
import sbt.nio.FileStamp
import sbt.nio.Keys.{ inputFileStamps, outputFileStamps }
import sbt.std.TaskExtra._
import sbt.util.InterfaceUtil.toOption
import sbt.util.Logger

import scala.annotation.nowarn

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

  lazy val defaultCacheLocation: File = SysProp.globalLocalCache

  lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    remoteCacheId := "",
    remoteCacheIdCandidates := Nil,
    pushRemoteCacheTo :== None,
    localCacheDirectory :== defaultCacheLocation,
    pushRemoteCache / ivyPaths := {
      val app = appConfiguration.value
      val base = app.baseDirectory.getCanonicalFile
      // base is used only to resolve relative paths, which should never happen
      IvyPaths(base, localCacheDirectory.value)
    },
  )

  lazy val projectSettings: Seq[Def.Setting[_]] = (Seq(
    pushRemoteCache := (Def.taskDyn {
      val arts = (pushRemoteCacheConfiguration / remoteCacheArtifacts).value
      val configs = arts flatMap { art =>
        art.packaged.scopedKey.scope match {
          case Scope(_, Select(c), _, _) => Some(c)
          case _                         => None
        }
      }
      val filter = ScopeFilter(configurations = inConfigurationsByKeys(configs: _*))
      Def.task {
        val _ = pushRemoteCache.all(filter).value
        ()
      }
    }).value,
    pullRemoteCache := (Def.taskDyn {
      val arts = (pushRemoteCacheConfiguration / remoteCacheArtifacts).value
      val configs = arts flatMap { art =>
        art.packaged.scopedKey.scope match {
          case Scope(_, Select(c), _, _) => Some(c)
          case _                         => None
        }
      }
      val filter = ScopeFilter(configurations = inConfigurationsByKeys(configs: _*))
      Def.task {
        val _ = pullRemoteCache.all(filter).value
        ()
      }
    }).value,
    pushRemoteCacheConfiguration / remoteCacheArtifacts := {
      enabledOnly(remoteCacheArtifact.toSettingKey, defaultArtifactTasks).apply(_.join).value
    },
    pushRemoteCacheConfiguration / publishMavenStyle := true,
    Compile / packageCache / pushRemoteCacheArtifact := true,
    Test / packageCache / pushRemoteCacheArtifact := true,
    Compile / packageCache / artifact := Artifact(moduleName.value, cachedCompileClassifier),
    Test / packageCache / artifact := Artifact(moduleName.value, cachedTestClassifier),
    remoteCachePom / pushRemoteCacheArtifact := true,
    remoteCachePom := {
      val s = streams.value
      val config = (remoteCachePom / makePomConfiguration).value
      val publisher = Keys.publisher.value
      publisher.makePomFile((pushRemoteCache / ivyModule).value, config, s.log)
      config.file.get
    },
    remoteCachePom / artifactPath := {
      Defaults.prefixArtifactPathSetting(makePom / artifact, "remote-cache").value
    },
    remoteCachePom / makePomConfiguration := {
      val config = makePomConfiguration.value
      config.withFile((remoteCachePom / artifactPath).value)
    },
    remoteCachePom / remoteCacheArtifact := {
      PomRemoteCacheArtifact((makePom / artifact).value, remoteCachePom)
    },
    remoteCacheResolvers := pushRemoteCacheTo.value.toVector,
  ) ++ inTask(pushRemoteCache)(
    Seq(
      ivyPaths := (Scope.Global / pushRemoteCache / ivyPaths).value,
      ivyConfiguration := {
        val config0 = Classpaths.mkIvyConfiguration.value
        config0
          .withResolvers(remoteCacheResolvers.value.toVector)
          .withOtherResolvers(pushRemoteCacheTo.value.toVector)
          .withResolutionCacheDir(crossTarget.value / "alt-resolution")
          .withPaths(ivyPaths.value)
          .withUpdateOptions(UpdateOptions().withGigahorse(true))
      },
      ivySbt := {
        Credentials.register(credentials.value, streams.value.log)
        val config0 = ivyConfiguration.value
        new IvySbt(config0)
      },
    )
  ) ++ inTask(pullRemoteCache)(
    Seq(
      dependencyResolution := Defaults.dependencyResolutionTask.value,
      csrConfiguration := {
        val rs = pushRemoteCacheTo.value.toVector ++ remoteCacheResolvers.value.toVector
        LMCoursier.scalaCompilerBridgeConfigurationTask.value
          .withResolvers(rs)
      }
    )
  ) ++ inConfig(Compile)(configCacheSettings(compileArtifact(Compile, cachedCompileClassifier)))
    ++ inConfig(Test)(configCacheSettings(testArtifact(Test, cachedTestClassifier))))

  def getResourceFilePaths() = Def.task {
    val syncDir = crossTarget.value / (prefix(configuration.value.name) + "sync")
    val file = syncDir / "copy-resource"
    file
  }

  @nowarn
  def configCacheSettings[A <: RemoteCacheArtifact](
      cacheArtifactTask: Def.Initialize[Task[A]]
  ): Seq[Def.Setting[_]] =
    inTask(packageCache)(
      Seq(
        packageCache.in(Defaults.TaskZero) := {
          val original = packageBin.in(Defaults.TaskZero).value
          val artp = artifactPath.value
          val af = compileAnalysisFile.value
          IO.copyFile(original, artp)
          // skip zip manipulation if the artp is a blank file
          if (af.exists && artp.length() > 0) {
            JarUtils.includeInJar(artp, Vector(af -> s"META-INF/inc_compile.zip"))
          }
          val rf = getResourceFilePaths.value
          if (rf.exists) {
            JarUtils.includeInJar(artp, Vector(rf -> s"META-INF/copy-resources.txt"))
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
        pushRemoteCacheArtifact := true,
        remoteCacheArtifact := cacheArtifactTask.value,
        packagedArtifact := (artifact.value -> packageCache.value),
        artifactPath := Defaults.artifactPathSetting(artifact).value
      )
    ) ++ inTask(pushRemoteCache)(
      Seq(
        moduleSettings := {
          val smi = scalaModuleInfo.value
          ModuleDescriptorConfiguration(remoteCacheProjectId.value, projectInfo.value)
            .withScalaModuleInfo(smi)
        },
        pushRemoteCache.in(Defaults.TaskZero) := (Def.task {
          val s = streams.value
          val config = pushRemoteCacheConfiguration.value
          val is = (pushRemoteCache / ivySbt).value
          val m = new is.Module(moduleSettings.value)
          IvyActions.publish(m, config, s.log)
        } tag (Tags.Publish, Tags.Network)).value,
      )
    ) ++ Seq(
      remoteCacheIdCandidates := List(remoteCacheId.value),
      remoteCacheProjectId := {
        val o = organization.value
        val m = moduleName.value
        val id = remoteCacheId.value
        val c = (projectID / crossVersion).value
        val v = toVersion(id)
        ModuleID(o, m, v).cross(c)
      },
      remoteCacheId := {
        val inputs = (unmanagedSources / inputFileStamps).value
        val cp = (externalDependencyClasspath / outputFileStamps).?.value.getOrElse(Nil)
        val extraInc = (extraIncOptions.value) flatMap {
          case (k, v) =>
            Vector(k, v)
        }
        combineHash(extractHash(inputs) ++ extractHash(cp) ++ extraInc)
      },
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
      pushRemoteCacheConfiguration / packagedArtifacts := Def.taskDyn {
        val artifacts = (pushRemoteCacheConfiguration / remoteCacheArtifacts).value
        artifacts
          .map(a => a.packaged.map(file => (a.artifact, file)))
          .join
          .apply(_.join.map(_.toMap))
      }.value,
      pushRemoteCacheConfiguration / remoteCacheArtifacts := {
        List((packageCache / remoteCacheArtifact).value)
      },
      pullRemoteCache := {
        import scala.collection.JavaConverters._
        val log = streams.value.log
        val r = remoteCacheResolvers.value.head
        val p = remoteCacheProjectId.value
        val ids = remoteCacheIdCandidates.value
        val is = (pushRemoteCache / ivySbt).value
        val m = new is.Module((pushRemoteCache / moduleSettings).value)
        val smi = scalaModuleInfo.value
        val artifacts = (pushRemoteCacheConfiguration / remoteCacheArtifacts).value
        val nonPom = artifacts.filterNot(isPomArtifact).toVector
        val copyResources = getResourceFilePaths.value
        m.withModule(log) {
          case (ivy, md, _) =>
            val resolver = ivy.getSettings.getResolver(r.name)
            if (resolver eq null) sys.error(s"undefined resolver '${r.name}'")
            val cross = CrossVersion(p, smi)
            val crossf: String => String = cross.getOrElse(identity _)
            var found = false
            ids foreach {
              id: String =>
                val v = toVersion(id)
                val modId = p.withRevision(v).withName(crossf(p.name))
                val ivyId = IvySbt.toID(modId)
                if (found) ()
                else {
                  val rawa = nonPom map { _.artifact }
                  val seqa = CrossVersion.substituteCross(rawa, cross)
                  val as = seqa map { a =>
                    val extra = a.classifier match {
                      case Some(c) => Map("e:classifier" -> c)
                      case None    => Map.empty
                    }
                    new DefaultArtifact(ivyId, null, a.name, a.`type`, a.extension, extra.asJava)
                  }
                  pullFromMavenRepo0(as, resolver, log) match {
                    case Right(xs0) =>
                      val jars = xs0.distinct

                      nonPom.foreach { art =>
                        val classifier = art.artifact.classifier

                        findJar(classifier, v, jars) match {
                          case Some(jar) =>
                            extractJar(art, jar, copyResources)
                            log.info(s"remote cache artifact extracted for $p $classifier")

                          case None =>
                            log.info(s"remote cache artifact not found for $p $classifier")
                        }
                      }
                      found = true
                    case Left(e) =>
                      val classifier = seqa.map(_.classifier).mkString(" ")
                      log.info(s"remote cache artifact not found for $p $classifier")
                      log.debug(e.getMessage)
                  }
                }
            }
            ()
        }
      },
    )

  def isPomArtifact(artifact: RemoteCacheArtifact): Boolean =
    artifact match {
      case _: PomRemoteCacheArtifact => true
      case _                         => false
    }

  def compileArtifact(
      configuration: Configuration,
      classifier: String
  ): Def.Initialize[Task[CompileRemoteCacheArtifact]] = Def.task {
    CompileRemoteCacheArtifact(
      Artifact(moduleName.value, classifier),
      configuration / packageCache,
      (configuration / classDirectory).value,
      (configuration / compileAnalysisFile).value
    )
  }

  def testArtifact(
      configuration: Configuration,
      classifier: String
  ): Def.Initialize[Task[TestRemoteCacheArtifact]] = Def.task {
    TestRemoteCacheArtifact(
      Artifact(moduleName.value, classifier),
      configuration / packageCache,
      (configuration / classDirectory).value,
      (configuration / compileAnalysisFile).value,
      Defaults.succeededFile((configuration / test / streams).value.cacheDirectory)
    )
  }

  private def toVersion(v: String): String = s"0.0.0-$v"

  private lazy val doption = new DownloadOptions
  private def pullFromMavenRepo0(
      artifacts: Vector[IArtifact],
      r: DependencyResolver,
      log: Logger
  ): Either[Throwable, Vector[File]] = {
    try {
      val files = r.download(artifacts.toArray, doption).getArtifactsReports.toVector map {
        report =>
          if (report == null) sys.error(s"failed to download $artifacts: " + r.toString)
          else
            report.getDownloadStatus match {
              case DownloadStatus.NO =>
                val o = report.getArtifactOrigin
                if (o.isLocal) {
                  val localFile = new File(o.getLocation)
                  if (!localFile.exists) sys.error(s"$localFile doesn't exist")
                  else localFile
                } else report.getLocalFile
              case DownloadStatus.SUCCESSFUL =>
                report.getLocalFile
              case DownloadStatus.FAILED =>
                sys.error(s"failed to download $artifacts: " + r.toString)
            }
      }
      Right(files)
    } catch {
      case e: Throwable => Left(e)
    }
  }

  private def findJar(classifier: Option[String], ver: String, jars: Vector[File]): Option[File] = {
    val suffix = classifier.fold(ver)(c => s"$ver-$c.jar")
    jars.find(_.toString.endsWith(suffix))
  }

  private def extractJar(
      cacheArtifact: RemoteCacheArtifact,
      jar: File,
      copyResources: File
  ): Unit =
    cacheArtifact match {
      case a: CompileRemoteCacheArtifact =>
        extractCache(jar, a.extractDirectory, preserveLastModified = true) { output =>
          extractAnalysis(output, a.analysisFile)
          extractResourceList(output, copyResources)
        }

      case a: TestRemoteCacheArtifact =>
        extractCache(jar, a.extractDirectory, preserveLastModified = true) { output =>
          extractAnalysis(output, a.analysisFile)
          extractTestResult(output, a.testResult)
        }

      case a: CustomRemoteCacheArtifact =>
        extractCache(jar, a.extractDirectory, a.preserveLastModified)(_ => ())

      case _ =>
        ()
    }

  private def extractCache(jar: File, output: File, preserveLastModified: Boolean)(
      processOutput: File => Unit
  ): Unit = {
    IO.delete(output)
    IO.unzip(jar, output, preserveLastModified = preserveLastModified)
    processOutput(output)

    // preserve semanticdb dir
    // https://github.com/scalameta/scalameta/blob/a7927ee8e012cfff/semanticdb/scalac/library/src/main/scala/scala/meta/internal/semanticdb/scalac/SemanticdbPaths.scala#L9
    Option((output / "META-INF").listFiles).foreach(
      _.iterator.filterNot(_.getName == "semanticdb").foreach(IO.delete)
    )
  }

  private def extractAnalysis(output: File, analysisFile: File): Unit = {
    val metaDir = output / "META-INF"
    val expandedAnalysis = metaDir / "inc_compile.zip"
    if (expandedAnalysis.exists) {
      IO.move(expandedAnalysis, analysisFile)
    }
  }

  private def extractResourceList(output: File, copyResources: File): Unit = {
    val metaDir = output / "META-INF"
    val extractedCopyResources = metaDir / "copy-resources.txt"
    if (extractedCopyResources.exists) {
      IO.move(extractedCopyResources, copyResources)
    }
  }

  private def extractTestResult(output: File, testResult: File): Unit = {
    //val expandedTestResult = output / "META-INF" / "succeeded_tests"
    //if (expandedTestResult.exists) {
    //  IO.move(expandedTestResult, testResult)
    //}
  }

  private def defaultArtifactTasks: Seq[TaskKey[File]] =
    Seq(Compile / packageCache, Test / packageCache)

  private def enabledOnly[A](
      key: SettingKey[A],
      pkgTasks: Seq[TaskKey[File]]
  ): Def.Initialize[Seq[A]] =
    (Classpaths.forallIn(key, pkgTasks) zipWith
      Classpaths.forallIn(pushRemoteCacheArtifact, pkgTasks))(_ zip _ collect {
      case (a, true) => a
    })

  private def extractHash(inputs: Seq[(Path, FileStamp)]): Vector[String] =
    inputs.toVector map {
      case (_, stamp0) => toOption(stamp0.stamp.getHash).getOrElse("cafe")
    }

  private def combineHash(vs: Vector[String]): String = {
    val hashValue = HashUtil.farmHash(vs.sorted.mkString("").getBytes("UTF-8"))
    java.lang.Long.toHexString(hashValue)
  }
}
