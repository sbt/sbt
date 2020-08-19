/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.util.concurrent.TimeUnit

import lmcoursier.definitions.{ Configuration => CConfiguration }
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import sbt.Def.{ Initialize, Setting }
import sbt.Project.{ inTask, richInitialize, richInitializeTask, sbtRichTaskPromise }
import sbt.Scope.GlobalScope
import sbt.coursierint._
import sbt.internal.CommandStrings.ExportStream
import sbt.internal._
import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.internal.librarymanagement.{ CustomHttp => _, _ }
import sbt.internal.util.Attributed.data
import sbt.internal.util.Types._
import sbt.internal.util._
import sbt.io._
import sbt.io.syntax._
import sbt.librarymanagement.Artifact.{ DocClassifier, SourceClassifier }
import sbt.librarymanagement.Configurations.{ Compile, CompilerPlugin, Provided, Test }
import sbt.librarymanagement.CrossVersion.{ binaryScalaVersion, partialVersion }
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import sbt.librarymanagement.syntax._
import sbt.nio.FileStamp
import sbt.nio.Keys._
import sbt.nio.file.{ Glob, RecursiveGlob }
import sbt.std.TaskExtra._
import sbt.util._
import sjsonnew._
import sjsonnew.support.scalajson.unsafe.Converter

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.xml.NodeSeq

// incremental compiler
import sbt.SlashSyntax0._
import sbt.internal.inc.ScalaInstance
import xsbti.{ CrossValue, VirtualFile }
import xsbti.compile.CompileAnalysis

object Classpaths {
  import Defaults._
  import Keys._

  def concatDistinct[T](a: Taskable[Seq[T]], b: Taskable[Seq[T]]): Initialize[Task[Seq[T]]] =
    Def.task((a.toTask.value ++ b.toTask.value).distinct)

  def concat[T](a: Taskable[Seq[T]], b: Taskable[Seq[T]]): Initialize[Task[Seq[T]]] =
    Def.task(a.toTask.value ++ b.toTask.value)

  def concatSettings[T](a: Initialize[Seq[T]], b: Initialize[Seq[T]]): Initialize[Seq[T]] =
    Def.setting { a.value ++ b.value }

  def concatDistinct[T]( // forward to widened variant
      a: ScopedTaskable[Seq[T]],
      b: ScopedTaskable[Seq[T]]
  ): Initialize[Task[Seq[T]]] = concatDistinct(a: Taskable[Seq[T]], b)

  def concat[T](a: ScopedTaskable[Seq[T]], b: ScopedTaskable[Seq[T]]): Initialize[Task[Seq[T]]] =
    concat(a: Taskable[Seq[T]], b) // forward to widened variant

  def concatSettings[T](a: SettingKey[Seq[T]], b: SettingKey[Seq[T]]): Initialize[Seq[T]] =
    concatSettings(a: Initialize[Seq[T]], b) // forward to widened variant

  // Included as part of JvmPlugin#projectSettings.
  lazy val configSettings: Seq[Setting[_]] = classpaths ++ Seq(
    products := makeProducts.value,
    pickleProducts := makePickleProducts.value,
    productDirectories := classDirectory.value :: Nil,
    classpathConfiguration := findClasspathConfig(
      internalConfigurationMap.value,
      configuration.value,
      classpathConfiguration.?.value,
      update.value
    )
  )
  private[this] def classpaths: Seq[Setting[_]] =
    Seq(
      externalDependencyClasspath := concat(unmanagedClasspath, managedClasspath).value,
      dependencyClasspath := concat(internalDependencyClasspath, externalDependencyClasspath).value,
      fullClasspath := concatDistinct(exportedProducts, dependencyClasspath).value,
      internalDependencyClasspath := ClasspathImpl.internalDependencyClasspathTask.value,
      unmanagedClasspath := unmanagedDependencies.value,
      managedClasspath := {
        val isMeta = isMetaBuild.value
        val force = reresolveSbtArtifacts.value
        val csr = useCoursier.value
        val app = appConfiguration.value
        val sbtCp0 = app.provider.mainClasspath.toList
        val sbtCp = sbtCp0 map { Attributed.blank(_) }
        val mjars = managedJars(
          classpathConfiguration.value,
          classpathTypes.value,
          update.value
        )
        if (isMeta && !force && !csr) mjars ++ sbtCp
        else mjars
      },
      exportedProducts := ClasspathImpl.trackedExportedProducts(TrackLevel.TrackAlways).value,
      exportedProductsIfMissing := ClasspathImpl
        .trackedExportedProducts(TrackLevel.TrackIfMissing)
        .value,
      exportedProductsNoTracking := ClasspathImpl
        .trackedExportedProducts(TrackLevel.NoTracking)
        .value,
      exportedProductJars := ClasspathImpl.trackedExportedJarProducts(TrackLevel.TrackAlways).value,
      exportedProductJarsIfMissing := ClasspathImpl
        .trackedExportedJarProducts(TrackLevel.TrackIfMissing)
        .value,
      exportedProductJarsNoTracking := ClasspathImpl
        .trackedExportedJarProducts(TrackLevel.NoTracking)
        .value,
      internalDependencyAsJars := internalDependencyJarsTask.value,
      dependencyClasspathAsJars := concat(internalDependencyAsJars, externalDependencyClasspath).value,
      fullClasspathAsJars := concatDistinct(exportedProductJars, dependencyClasspathAsJars).value,
      unmanagedJars := findUnmanagedJars(
        configuration.value,
        unmanagedBase.value,
        includeFilter in unmanagedJars value,
        excludeFilter in unmanagedJars value
      )
    ).map(exportClasspath) ++ Seq(
      dependencyClasspathFiles := data(dependencyClasspath.value).map(_.toPath),
      dependencyClasspathFiles / outputFileStamps := {
        val stamper = timeWrappedStamper.value
        val converter = fileConverter.value
        dependencyClasspathFiles.value.flatMap(
          p => FileStamp(stamper.library(converter.toVirtualFile(p))).map(p -> _)
        )
      },
      dependencyVirtualClasspath := {
        val converter = fileConverter.value
        val cp0 = dependencyClasspath.value
        cp0 map { attr: Attributed[File] =>
          attr map { file =>
            converter.toVirtualFile(file.toPath)
          }
        }
      },
      // Note: invoking this task from shell would block indefinately because it will
      // wait for the upstream compilation to start.
      dependencyPicklePath := {
        // This is a conditional task. Do not refactor.
        if (incOptions.value.pipelining) {
          concat(
            internalDependencyPicklePath,
            Def.task {
              externalDependencyClasspath.value map { attr: Attributed[File] =>
                attr map { file =>
                  val converter = fileConverter.value
                  converter.toVirtualFile(file.toPath)
                }
              }
            }
          ).value
        } else {
          dependencyVirtualClasspath.value
        }
      },
      internalDependencyPicklePath := ClasspathImpl.internalDependencyPicklePathTask.value,
      exportedPickles := ClasspathImpl.exportedPicklesTask.value,
    )

  private[this] def exportClasspath(s: Setting[Task[Classpath]]): Setting[Task[Classpath]] =
    s.mapInitialize(init => Def.task { exportClasspath(streams.value, init.value) })
  private[this] def exportClasspath(s: TaskStreams, cp: Classpath): Classpath = {
    val w = s.text(ExportStream)
    try w.println(Path.makeString(data(cp)))
    finally w.close() // workaround for #937
    cp
  }

  def defaultPackageKeys = Seq(packageBin, packageSrc, packageDoc)
  lazy val defaultPackages: Seq[TaskKey[File]] =
    for (task <- defaultPackageKeys; conf <- Seq(Compile, Test)) yield (task in conf)
  lazy val defaultArtifactTasks: Seq[TaskKey[File]] = makePom +: defaultPackages

  def findClasspathConfig(
      map: Configuration => Configuration,
      thisConfig: Configuration,
      delegated: Option[Configuration],
      report: UpdateReport
  ): Configuration = {
    val defined = report.allConfigurations.toSet
    val search = map(thisConfig) +: (delegated.toList ++ Seq(Compile, Configurations.Default))
    def notFound =
      sys.error(
        "Configuration to use for managed classpath must be explicitly defined when default configurations are not present."
      )
    search find { c =>
      defined contains ConfigRef(c.name)
    } getOrElse notFound
  }

  def packaged(pkgTasks: Seq[TaskKey[File]]): Initialize[Task[Map[Artifact, File]]] =
    enabledOnly(packagedArtifact.toSettingKey, pkgTasks) apply (_.join.map(_.toMap))

  def artifactDefs(pkgTasks: Seq[TaskKey[File]]): Initialize[Seq[Artifact]] =
    enabledOnly(artifact, pkgTasks)

  def enabledOnly[T](key: SettingKey[T], pkgTasks: Seq[TaskKey[File]]): Initialize[Seq[T]] =
    (forallIn(key, pkgTasks) zipWith forallIn(publishArtifact, pkgTasks))(_ zip _ collect {
      case (a, true) => a
    })

  def forallIn[T](
      key: Scoped.ScopingSetting[SettingKey[T]], // should be just SettingKey[T] (mea culpa)
      pkgTasks: Seq[TaskKey[_]],
  ): Initialize[Seq[T]] =
    pkgTasks.map(pkg => key in pkg.scope in pkg).join

  private[this] def publishGlobalDefaults =
    Defaults.globalDefaults(
      Seq(
        publishMavenStyle :== true,
        publishArtifact :== true,
        publishArtifact in Test :== false
      )
    )

  val jvmPublishSettings: Seq[Setting[_]] = Seq(
    artifacts := artifactDefs(defaultArtifactTasks).value,
    packagedArtifacts := packaged(defaultArtifactTasks).value
  ) ++ RemoteCache.projectSettings

  val ivyPublishSettings: Seq[Setting[_]] = publishGlobalDefaults ++ Seq(
    artifacts :== Nil,
    packagedArtifacts :== Map.empty,
    crossTarget := target.value,
    makePom := {
      val config = makePomConfiguration.value
      val publisher = Keys.publisher.value
      publisher.makePomFile(ivyModule.value, config, streams.value.log)
      config.file.get
    },
    packagedArtifact in makePom := ((artifact in makePom).value -> makePom.value),
    deliver := deliverTask(makeIvyXmlConfiguration).value,
    deliverLocal := deliverTask(makeIvyXmlLocalConfiguration).value,
    makeIvyXml := deliverTask(makeIvyXmlConfiguration).value,
    publish := publishTask(publishConfiguration).value,
    publishLocal := publishTask(publishLocalConfiguration).value,
    publishM2 := publishTask(publishM2Configuration).value
  )

  private[this] def baseGlobalDefaults =
    Defaults.globalDefaults(
      Seq(
        conflictWarning :== ConflictWarning.default("global"),
        evictionWarningOptions := EvictionWarningOptions.default,
        compatibilityWarningOptions :== CompatibilityWarningOptions.default,
        homepage :== None,
        startYear :== None,
        licenses :== Nil,
        developers :== Nil,
        scmInfo :== None,
        offline :== SysProp.offline,
        defaultConfiguration :== Some(Configurations.Compile),
        dependencyOverrides :== Vector.empty,
        libraryDependencies :== Nil,
        excludeDependencies :== Nil,
        ivyLoggingLevel := (// This will suppress "Resolving..." logs on Jenkins and Travis.
        if (insideCI.value)
          UpdateLogging.Quiet
        else UpdateLogging.Default),
        ivyXML :== NodeSeq.Empty,
        ivyValidate :== false,
        moduleConfigurations :== Nil,
        publishTo :== None,
        resolvers :== Vector.empty,
        includePluginResolvers :== false,
        useJCenter :== false,
        retrievePattern :== Resolver.defaultRetrievePattern,
        transitiveClassifiers :== Seq(SourceClassifier, DocClassifier),
        sourceArtifactTypes :== Artifact.DefaultSourceTypes.toVector,
        docArtifactTypes :== Artifact.DefaultDocTypes.toVector,
        cleanKeepFiles :== Nil,
        cleanKeepGlobs := {
          val base = appConfiguration.value.baseDirectory.getCanonicalFile
          val dirs = BuildPaths
            .globalLoggingStandard(base) :: BuildPaths.globalTaskDirectoryStandard(base) :: Nil
          dirs.flatMap(d => Glob(d) :: Glob(d, RecursiveGlob) :: Nil)
        },
        fileOutputs :== Nil,
        sbtDependency := {
          val app = appConfiguration.value
          val id = app.provider.id
          val scalaVersion = app.provider.scalaProvider.version
          val binVersion = binaryScalaVersion(scalaVersion)
          val cross = id.crossVersionedValue match {
            case CrossValue.Disabled => Disabled()
            case CrossValue.Full     => CrossVersion.binary
            case CrossValue.Binary   => CrossVersion.full
          }
          val base = ModuleID(id.groupID, id.name, sbtVersion.value).withCrossVersion(cross)
          CrossVersion(scalaVersion, binVersion)(base).withCrossVersion(Disabled())
        },
        shellPrompt := shellPromptFromState,
        terminalShellPrompt := { (t, s) =>
          shellPromptFromState(t)(s)
        },
        dynamicDependency := { (): Unit },
        transitiveClasspathDependency := { (): Unit },
        transitiveDynamicInputs :== Nil,
      )
    )

  val ivyBaseSettings: Seq[Setting[_]] = baseGlobalDefaults ++ sbtClassifiersTasks ++ Seq(
    conflictWarning := conflictWarning.value.copy(label = Reference.display(thisProjectRef.value)),
    unmanagedBase := baseDirectory.value / "lib",
    normalizedName := Project.normalizeModuleID(name.value),
    isSnapshot := (isSnapshot or version(_ endsWith "-SNAPSHOT")).value,
    description := (description or name).value,
    organization := (organization or normalizedName).value,
    organizationName := (organizationName or organization).value,
    organizationHomepage := (organizationHomepage or homepage).value,
    projectInfo := ModuleInfo(
      name.value,
      description.value,
      homepage.value,
      startYear.value,
      licenses.value.toVector,
      organizationName.value,
      organizationHomepage.value,
      scmInfo.value,
      developers.value.toVector
    ),
    overrideBuildResolvers := appConfiguration(isOverrideRepositories).value,
    externalResolvers := ((
      externalResolvers.?.value,
      resolvers.value,
      appResolvers.value,
      useJCenter.value
    ) match {
      case (Some(delegated), Seq(), _, _) => delegated
      case (_, rs, Some(ars), _)          => ars ++ rs
      case (_, rs, _, uj)                 => Resolver.combineDefaultResolvers(rs.toVector, uj, mavenCentral = true)
    }),
    appResolvers := {
      val ac = appConfiguration.value
      val uj = useJCenter.value
      appRepositories(ac) map { ars =>
        val useMavenCentral = ars contains Resolver.DefaultMavenRepository
        Resolver.reorganizeAppResolvers(ars, uj, useMavenCentral)
      }
    },
    bootResolvers := (appConfiguration map bootRepositories).value,
    fullResolvers :=
      (Def.task {
        val proj = projectResolver.value
        val rs = externalResolvers.value
        def pluginResolvers: Vector[Resolver] =
          buildStructure.value
            .units(thisProjectRef.value.build)
            .unit
            .plugins
            .pluginData
            .resolvers
            .getOrElse(Vector.empty)
        val pr =
          if (includePluginResolvers.value) pluginResolvers
          else Vector.empty
        bootResolvers.value match {
          case Some(repos) if overrideBuildResolvers.value => proj +: repos
          case _ =>
            val base = if (sbtPlugin.value) sbtResolvers.value ++ rs ++ pr else rs ++ pr
            (proj +: base).distinct
        }
      }).value,
    moduleName := normalizedName.value,
    ivyPaths := IvyPaths(baseDirectory.value, bootIvyHome(appConfiguration.value)),
    csrCacheDirectory := {
      val old = csrCacheDirectory.value
      val ac = appConfiguration.value
      val ip = ivyPaths.value
      // if ivyPaths is customized, create coursier-cache directory in it
      if (useCoursier.value) {
        val defaultIvyCache = bootIvyHome(ac)
        if (old != LMCoursier.defaultCacheLocation) old
        else if (ip.ivyHome == defaultIvyCache) old
        else
          ip.ivyHome match {
            case Some(home) => home / "coursier-cache"
            case _          => old
          }
      } else Classpaths.dummyCoursierDirectory(ac)
    },
    dependencyCacheDirectory := {
      val st = state.value
      BuildPaths.getDependencyDirectory(st, BuildPaths.getGlobalBase(st))
    },
    otherResolvers := Resolver.publishMavenLocal +: publishTo.value.toVector,
    projectResolver := projectResolverTask.value,
    projectDependencies := projectDependenciesTask.value,
    // TODO - Is this the appropriate split?  Ivy defines this simply as
    //        just project + library, while the JVM plugin will define it as
    //        having the additional sbtPlugin + autoScala magikz.
    allDependencies := {
      projectDependencies.value ++ libraryDependencies.value
    },
    allExcludeDependencies := excludeDependencies.value,
    scalaModuleInfo := (scalaModuleInfo or (
      Def.setting {
        Option(
          ScalaModuleInfo(
            (scalaVersion in update).value,
            (scalaBinaryVersion in update).value,
            Vector.empty,
            filterImplicit = false,
            checkExplicit = true,
            overrideScalaVersion = true
          ).withScalaOrganization(scalaOrganization.value)
            .withScalaArtifacts(scalaArtifacts.value.toVector)
        )
      }
    )).value,
    artifactPath in makePom := artifactPathSetting(artifact in makePom).value,
    publishArtifact in makePom := publishMavenStyle.value && publishArtifact.value,
    artifact in makePom := Artifact.pom(moduleName.value),
    projectID := defaultProjectID.value,
    projectID := pluginProjectID.value,
    projectDescriptors := depMap.value,
    updateConfiguration := {
      // Tell the UpdateConfiguration which artifact types are special (for sources and javadocs)
      val specialArtifactTypes = sourceArtifactTypes.value.toSet union docArtifactTypes.value.toSet
      // By default, to retrieve all types *but* these (it's assumed that everything else is binary/resource)
      UpdateConfiguration()
        .withRetrieveManaged(retrieveConfiguration.value)
        .withLogging(ivyLoggingLevel.value)
        .withArtifactFilter(ArtifactTypeFilter.forbid(specialArtifactTypes))
        .withOffline(offline.value)
    },
    retrieveConfiguration := {
      if (retrieveManaged.value)
        Some(
          RetrieveConfiguration()
            .withRetrieveDirectory(managedDirectory.value)
            .withOutputPattern(retrievePattern.value)
            .withSync(retrieveManagedSync.value)
            .withConfigurationsToRetrieve(configurationsToRetrieve.value map { _.toVector })
        )
      else None
    },
    dependencyResolution := dependencyResolutionTask.value,
    publisher := IvyPublisher(ivyConfiguration.value, CustomHttp.okhttpClient.value),
    ivyConfiguration := mkIvyConfiguration.value,
    ivyConfigurations := {
      val confs = thisProject.value.configurations
      (confs ++ confs.map(internalConfigurationMap.value) ++ (if (autoCompilerPlugins.value)
                                                                CompilerPlugin :: Nil
                                                              else Nil)).distinct
    },
    ivyConfigurations ++= Configurations.auxiliary,
    ivyConfigurations ++= {
      if (managedScalaInstance.value && scalaHome.value.isEmpty) Configurations.ScalaTool :: Nil
      else Nil
    },
    // Coursier needs these
    ivyConfigurations := {
      val confs = ivyConfigurations.value
      val names = confs.map(_.name).toSet
      val extraSources =
        if (names("sources"))
          None
        else
          Some(
            Configuration.of(
              id = "Sources",
              name = "sources",
              description = "",
              isPublic = true,
              extendsConfigs = Vector.empty,
              transitive = false
            )
          )

      val extraDocs =
        if (names("docs"))
          None
        else
          Some(
            Configuration.of(
              id = "Docs",
              name = "docs",
              description = "",
              isPublic = true,
              extendsConfigs = Vector.empty,
              transitive = false
            )
          )

      val use = useCoursier.value
      if (use) confs ++ extraSources.toSeq ++ extraDocs.toSeq
      else confs
    },
    moduleSettings := moduleSettings0.value,
    makePomConfiguration := MakePomConfiguration()
      .withFile((artifactPath in makePom).value)
      .withModuleInfo(projectInfo.value)
      .withExtra(pomExtra.value)
      .withProcess(pomPostProcess.value)
      .withFilterRepositories(pomIncludeRepository.value)
      .withAllRepositories(pomAllRepositories.value)
      .withConfigurations(Configurations.defaultMavenConfigurations),
    makeIvyXmlConfiguration := {
      makeIvyXmlConfig(
        publishMavenStyle.value,
        sbt.Classpaths.deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        checksums.in(publish).value.toVector,
        ivyLoggingLevel.value,
        isSnapshot.value
      )
    },
    publishConfiguration := {
      publishConfig(
        publishMavenStyle.value,
        deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        packagedArtifacts.in(publish).value.toVector,
        checksums.in(publish).value.toVector,
        getPublishTo(publishTo.value).name,
        ivyLoggingLevel.value,
        isSnapshot.value
      )
    },
    makeIvyXmlLocalConfiguration := {
      makeIvyXmlConfig(
        false, //publishMavenStyle.value,
        sbt.Classpaths.deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        checksums.in(publish).value.toVector,
        ivyLoggingLevel.value,
        isSnapshot.value,
        optResolverName = Some("local")
      )
    },
    publishLocalConfiguration := publishConfig(
      false, //publishMavenStyle.value,
      deliverPattern(crossTarget.value),
      if (isSnapshot.value) "integration" else "release",
      ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
      packagedArtifacts.in(publishLocal).value.toVector,
      checksums.in(publishLocal).value.toVector,
      logging = ivyLoggingLevel.value,
      overwrite = isSnapshot.value
    ),
    publishM2Configuration := publishConfig(
      true,
      deliverPattern(crossTarget.value),
      if (isSnapshot.value) "integration" else "release",
      ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
      packagedArtifacts.in(publishM2).value.toVector,
      checksums = checksums.in(publishM2).value.toVector,
      resolverName = Resolver.publishMavenLocal.name,
      logging = ivyLoggingLevel.value,
      overwrite = isSnapshot.value
    ),
    ivySbt := ivySbt0.value,
    ivyModule := { val is = ivySbt.value; new is.Module(moduleSettings.value) },
    allCredentials := LMCoursier.allCredentialsTask.value,
    transitiveUpdate := transitiveUpdateTask.value,
    updateCacheName := {
      val binVersion = scalaBinaryVersion.value
      val suffix = if (crossPaths.value) s"_$binVersion" else ""
      s"update_cache$suffix"
    },
    dependencyPositions := dependencyPositionsTask.value,
    unresolvedWarningConfiguration in update := UnresolvedWarningConfiguration(
      dependencyPositions.value
    ),
    updateFull := (updateTask tag (Tags.Update, Tags.Network)).value,
    update := (updateWithoutDetails("update") tag (Tags.Update, Tags.Network)).value,
    update := {
      val report = update.value
      val log = streams.value.log
      ConflictWarning(conflictWarning.value, report, log)
      report
    },
    evictionWarningOptions in update := evictionWarningOptions.value,
    evictionWarningOptions in evicted := EvictionWarningOptions.full,
    evicted := {
      import ShowLines._
      val report = (updateTask tag (Tags.Update, Tags.Network)).value
      val log = streams.value.log
      val ew =
        EvictionWarning(ivyModule.value, (evicted / evictionWarningOptions).value, report)
      ew.lines foreach { log.warn(_) }
      ew.infoAllTheThings foreach { log.info(_) }
      ew
    },
  ) ++
    inTask(updateClassifiers)(
      Seq(
        classifiersModule := {
          implicit val key = (m: ModuleID) => (m.organization, m.name, m.revision)
          val projectDeps = projectDependencies.value.iterator.map(key).toSet
          val externalModules = update.value.allModules.filterNot(m => projectDeps contains key(m))
          GetClassifiersModule(
            projectID.value,
            None,
            externalModules,
            ivyConfigurations.value.toVector,
            transitiveClassifiers.value.toVector
          )
        },
        dependencyResolution := dependencyResolutionTask.value,
        csrConfiguration := LMCoursier.updateClassifierConfigurationTask.value,
        updateClassifiers in TaskGlobal := LibraryManagement.updateClassifiersTask.value,
      )
    ) ++ Seq(
    csrProject := CoursierInputsTasks.coursierProjectTask.value,
    csrConfiguration := LMCoursier.coursierConfigurationTask.value,
    csrResolvers := CoursierRepositoriesTasks.coursierResolversTask.value,
    csrRecursiveResolvers := CoursierRepositoriesTasks.coursierRecursiveResolversTask.value,
    csrSbtResolvers := CoursierRepositoriesTasks.coursierSbtResolversTask.value,
    csrInterProjectDependencies := CoursierInputsTasks.coursierInterProjectDependenciesTask.value,
    csrExtraProjects := CoursierInputsTasks.coursierExtraProjectsTask.value,
    csrFallbackDependencies := CoursierInputsTasks.coursierFallbackDependenciesTask.value,
  ) ++
    IvyXml.generateIvyXmlSettings() ++
    LMCoursier.publicationsSetting(Seq(Compile, Test).map(c => c -> CConfiguration(c.name)))

  val jvmBaseSettings: Seq[Setting[_]] = Seq(
    libraryDependencies ++= autoLibraryDependency(
      autoScalaLibrary.value && scalaHome.value.isEmpty && managedScalaInstance.value,
      sbtPlugin.value,
      scalaOrganization.value,
      scalaVersion.value
    ),
    // Override the default to handle mixing in the sbtPlugin + scala dependencies.
    allDependencies := {
      val base = projectDependencies.value ++ libraryDependencies.value
      val isPlugin = sbtPlugin.value
      val sbtdeps =
        (sbtDependency in pluginCrossBuild).value.withConfigurations(Some(Provided.name))
      val pluginAdjust =
        if (isPlugin) sbtdeps +: base
        else base
      val sbtOrg = scalaOrganization.value
      val version = scalaVersion.value
      if (scalaHome.value.isDefined || scalaModuleInfo.value.isEmpty || !managedScalaInstance.value)
        pluginAdjust
      else {
        val isDotty = ScalaInstance.isDotty(version)
        ScalaArtifacts.toolDependencies(sbtOrg, version, isDotty) ++ pluginAdjust
      }
    },
    // in case of meta build, exclude all sbt modules from the dependency graph, so we can use the sbt resolved by the launcher
    allExcludeDependencies := {
      val sbtdeps = sbtDependency.value
      val isMeta = isMetaBuild.value
      val force = reresolveSbtArtifacts.value
      val excludes = excludeDependencies.value
      val csr = useCoursier.value
      val o = sbtdeps.organization
      val sbtModulesExcludes = Vector[ExclusionRule](
        o % "sbt",
        o %% "scripted-plugin",
        o %% "librarymanagement-core",
        o %% "librarymanagement-ivy",
        o %% "util-logging",
        o %% "util-position",
        o %% "io"
      )
      if (isMeta && !force && !csr) excludes.toVector ++ sbtModulesExcludes
      else excludes
    },
    dependencyOverrides ++= {
      val isPlugin = sbtPlugin.value
      val app = appConfiguration.value
      val id = app.provider.id
      val sv = (sbtVersion in pluginCrossBuild).value
      val base = ModuleID(id.groupID, "scripted-plugin", sv).withCrossVersion(CrossVersion.binary)
      if (isPlugin) Seq(base)
      else Seq()
    }
  )

  def warnResolversConflict(ress: Seq[Resolver], log: Logger): Unit = {
    val resset = ress.toSet
    for ((name, r) <- resset groupBy (_.name) if r.size > 1) {
      log.warn(
        "Multiple resolvers having different access mechanism configured with same name '" + name + "'. To avoid conflict, Remove duplicate project resolvers (`resolvers`) or rename publishing resolver (`publishTo`)."
      )
    }
  }

  private[sbt] def errorInsecureProtocol(ress: Seq[Resolver], log: Logger): Unit = {
    val bad = !ress.forall(!_.validateProtocol(log))
    if (bad) {
      sys.error("insecure protocol is unsupported")
    }
  }
  // this warns about .from("http:/...") in ModuleID
  private[sbt] def errorInsecureProtocolInModules(mods: Seq[ModuleID], log: Logger): Unit = {
    val artifacts = mods.flatMap(_.explicitArtifacts.toSeq)
    val bad = !artifacts.forall(!_.validateProtocol(log))
    if (bad) {
      sys.error("insecure protocol is unsupported")
    }
  }

  private[sbt] def defaultProjectID: Initialize[ModuleID] = Def.setting {
    val p0 = ModuleID(organization.value, moduleName.value, version.value)
      .cross(crossVersion in projectID value)
      .artifacts(artifacts.value: _*)
    val p1 = apiURL.value match {
      case Some(u) => p0.extra(SbtPomExtraProperties.POM_API_KEY -> u.toExternalForm)
      case _       => p0
    }
    val p2 = versionScheme.value match {
      case Some(x) =>
        VersionSchemes.validateScheme(x)
        p1.extra(SbtPomExtraProperties.VERSION_SCHEME_KEY -> x)
      case _ => p1
    }
    p2
  }
  def pluginProjectID: Initialize[ModuleID] =
    Def.setting {
      if (sbtPlugin.value)
        sbtPluginExtra(
          projectID.value,
          (sbtBinaryVersion in pluginCrossBuild).value,
          (scalaBinaryVersion in pluginCrossBuild).value
        )
      else projectID.value
    }
  private[sbt] def ivySbt0: Initialize[Task[IvySbt]] =
    Def.task {
      Credentials.register(credentials.value, streams.value.log)
      new IvySbt(ivyConfiguration.value, CustomHttp.okhttpClient.value)
    }
  def moduleSettings0: Initialize[Task[ModuleSettings]] = Def.task {
    val deps = allDependencies.value.toVector
    errorInsecureProtocolInModules(deps, streams.value.log)
    ModuleDescriptorConfiguration(projectID.value, projectInfo.value)
      .withValidate(ivyValidate.value)
      .withScalaModuleInfo(scalaModuleInfo.value)
      .withDependencies(deps)
      .withOverrides(dependencyOverrides.value.toVector)
      .withExcludes(allExcludeDependencies.value.toVector)
      .withIvyXML(ivyXML.value)
      .withConfigurations(ivyConfigurations.value.toVector)
      .withDefaultConfiguration(defaultConfiguration.value)
      .withConflictManager(conflictManager.value)
  }

  private[this] def sbtClassifiersGlobalDefaults =
    Defaults.globalDefaults(
      Seq(
        transitiveClassifiers in updateSbtClassifiers ~= (_.filter(_ != DocClassifier))
      )
    )
  def sbtClassifiersTasks =
    sbtClassifiersGlobalDefaults ++
      inTask(updateSbtClassifiers)(
        Seq(
          externalResolvers := {
            val boot = bootResolvers.value
            val explicit = buildStructure.value
              .units(thisProjectRef.value.build)
              .unit
              .plugins
              .pluginData
              .resolvers
            explicit orElse boot getOrElse externalResolvers.value
          },
          ivyConfiguration := InlineIvyConfiguration(
            lock = Option(lock(appConfiguration.value)),
            log = Option(streams.value.log),
            updateOptions = UpdateOptions(),
            paths = Option(ivyPaths.value),
            resolvers = externalResolvers.value.toVector,
            otherResolvers = Vector.empty,
            moduleConfigurations = Vector.empty,
            checksums = checksums.value.toVector,
            managedChecksums = false,
            resolutionCacheDir = Some(crossTarget.value / "resolution-cache"),
          ),
          ivySbt := ivySbt0.value,
          classifiersModule := classifiersModuleTask.value,
          // Redefine scalaVersion and scalaBinaryVersion specifically for the dependency graph used for updateSbtClassifiers task.
          // to fix https://github.com/sbt/sbt/issues/2686
          scalaVersion := appConfiguration.value.provider.scalaProvider.version,
          scalaBinaryVersion := binaryScalaVersion(scalaVersion.value),
          scalaOrganization := ScalaArtifacts.Organization,
          scalaModuleInfo := {
            Some(
              ScalaModuleInfo(
                scalaVersion.value,
                scalaBinaryVersion.value,
                Vector(),
                checkExplicit = false,
                filterImplicit = false,
                overrideScalaVersion = true
              ).withScalaOrganization(scalaOrganization.value)
            )
          },
          dependencyResolution := dependencyResolutionTask.value,
          csrConfiguration := LMCoursier.updateSbtClassifierConfigurationTask.value,
          updateSbtClassifiers in TaskGlobal := (Def.task {
            val lm = dependencyResolution.value
            val s = streams.value
            val is = ivySbt.value
            val mod = classifiersModule.value
            val updateConfig0 = updateConfiguration.value
            val updateConfig = updateConfig0
              .withMetadataDirectory(dependencyCacheDirectory.value)
              .withArtifactFilter(
                updateConfig0.artifactFilter.map(af => af.withInverted(!af.inverted))
              )
            val app = appConfiguration.value
            val srcTypes = sourceArtifactTypes.value
            val docTypes = docArtifactTypes.value
            val log = s.log
            val out = is.withIvy(log)(_.getSettings.getDefaultIvyUserDir)
            val uwConfig = (unresolvedWarningConfiguration in update).value
            withExcludes(out, mod.classifiers, lock(app)) { excludes =>
              // val noExplicitCheck = ivy.map(_.withCheckExplicit(false))
              LibraryManagement.transitiveScratch(
                lm,
                "sbt",
                GetClassifiersConfiguration(
                  mod,
                  excludes.toVector,
                  updateConfig,
                  srcTypes.toVector,
                  docTypes.toVector
                ),
                uwConfig,
                log
              ) match {
                case Left(_)   => ???
                case Right(ur) => ur
              }
            }
          } tag (Tags.Update, Tags.Network)).value
        )
      ) ++
      inTask(scalaCompilerBridgeScope)(
        Seq(
          dependencyResolution := dependencyResolutionTask.value,
          csrConfiguration := LMCoursier.scalaCompilerBridgeConfigurationTask.value,
          csrResolvers := CoursierRepositoriesTasks.coursierResolversTask.value,
          externalResolvers := scalaCompilerBridgeResolvers.value,
          ivyConfiguration := InlineIvyConfiguration(
            lock = Option(lock(appConfiguration.value)),
            log = Option(streams.value.log),
            updateOptions = UpdateOptions(),
            paths = Option(ivyPaths.value),
            resolvers = scalaCompilerBridgeResolvers.value.toVector,
            otherResolvers = Vector.empty,
            moduleConfigurations = Vector.empty,
            checksums = checksums.value.toVector,
            managedChecksums = false,
            resolutionCacheDir = Some(crossTarget.value / "bridge-resolution-cache"),
          )
        )
      ) ++ Seq(
      bootIvyConfiguration := (updateSbtClassifiers / ivyConfiguration).value,
      bootDependencyResolution := (updateSbtClassifiers / dependencyResolution).value,
      scalaCompilerBridgeResolvers := {
        val boot = bootResolvers.value
        val explicit = buildStructure.value
          .units(thisProjectRef.value.build)
          .unit
          .plugins
          .pluginData
          .resolvers
        val ext = externalResolvers.value.toVector
        // https://github.com/sbt/sbt/issues/4408
        val xs = (explicit, boot) match {
          case (Some(ex), Some(b)) => (ex.toVector ++ b.toVector).distinct
          case (Some(ex), None)    => ex.toVector
          case (None, Some(b))     => b.toVector
          case _                   => Vector()
        }
        (xs ++ ext).distinct
      },
      scalaCompilerBridgeDependencyResolution := (scalaCompilerBridgeScope / dependencyResolution).value
    )

  def classifiersModuleTask: Initialize[Task[GetClassifiersModule]] =
    Def.task {
      val classifiers = transitiveClassifiers.value
      val ref = thisProjectRef.value
      val pluginClasspath = loadedBuild.value.units(ref.build).unit.plugins.fullClasspath.toVector
      val pluginJars = pluginClasspath.filter(_.data.isFile) // exclude directories: an approximation to whether they've been published
      val pluginIDs: Vector[ModuleID] = pluginJars.flatMap(_ get moduleID.key)
      GetClassifiersModule(
        projectID.value,
        // TODO: Should it be sbt's scalaModuleInfo?
        scalaModuleInfo.value,
        sbtDependency.value +: pluginIDs,
        // sbt is now on Maven Central, so this has changed from sbt 0.13.
        Vector(Configurations.Default) ++ Configurations.default,
        classifiers.toVector
      )
    }

  def deliverTask(config: TaskKey[PublishConfiguration]): Initialize[Task[File]] =
    Def.task {
      Def.unit(update.value)
      IvyActions.deliver(ivyModule.value, config.value, streams.value.log)
    }

  @deprecated("Use variant without delivery key", "1.1.1")
  def publishTask(
      config: TaskKey[PublishConfiguration],
      deliverKey: TaskKey[_],
  ): Initialize[Task[Unit]] =
    publishTask(config)

  def publishTask(config: TaskKey[PublishConfiguration]): Initialize[Task[Unit]] =
    Def.taskIf {
      if ((publish / skip).value) {
        val s = streams.value
        val ref = thisProjectRef.value
        s.log.debug(s"Skipping publish* for ${ref.project}")
      } else {
        val s = streams.value
        IvyActions.publish(ivyModule.value, config.value, s.log)
      }
    } tag (Tags.Publish, Tags.Network)

  val moduleIdJsonKeyFormat: sjsonnew.JsonKeyFormat[ModuleID] =
    new sjsonnew.JsonKeyFormat[ModuleID] {
      import LibraryManagementCodec._
      import sjsonnew.support.scalajson.unsafe._
      val moduleIdFormat: JsonFormat[ModuleID] = implicitly[JsonFormat[ModuleID]]
      def write(key: ModuleID): String =
        CompactPrinter(Converter.toJsonUnsafe(key)(moduleIdFormat))
      def read(key: String): ModuleID =
        Converter.fromJsonUnsafe[ModuleID](Parser.parseUnsafe(key))(moduleIdFormat)
    }

  def withExcludes(out: File, classifiers: Seq[String], lock: xsbti.GlobalLock)(
      f: Map[ModuleID, Vector[ConfigRef]] => UpdateReport
  ): UpdateReport = LibraryManagement.withExcludes(out, classifiers, lock)(f)

  /**
   * Substitute unmanaged jars for managed jars when the major.minor parts of
   * the version are the same for:
   *   1. The Scala version and the `scalaHome` (unmanaged) version are equal.
   *   2. The Scala version and the `declared` (managed) version are equal.
   *
   * Equality is weak, that is, no version qualifier is checked.
   */
  private def unmanagedJarsTask(scalaVersion: String, unmanagedVersion: String, jars: Seq[File]) = {
    (subVersion0: String) =>
      val scalaV = partialVersion(scalaVersion)
      val managedV = partialVersion(subVersion0)
      val unmanagedV = partialVersion(unmanagedVersion)
      (managedV, unmanagedV, scalaV) match {
        case (Some(mv), Some(uv), _) if mv == uv => jars
        case (Some(mv), _, Some(sv)) if mv == sv => jars
        case _                                   => Nil
      }
  }

  def updateTask: Initialize[Task[UpdateReport]] = updateTask0("updateFull", true, true)
  def updateWithoutDetails(label: String): Initialize[Task[UpdateReport]] =
    updateTask0(label, false, false)

  /**
   * cacheLabel - label to identify an update cache
   * includeCallers - include the caller information
   * includeDetails - include module reports for the evicted modules
   */
  private def updateTask0(
      cacheLabel: String,
      includeCallers: Boolean,
      includeDetails: Boolean
  ): Initialize[Task[UpdateReport]] = Def.task {
    val s = streams.value
    val cacheDirectory = crossTarget.value / cacheLabel / updateCacheName.value

    import CacheStoreFactory.jvalueIsoString
    val cacheStoreFactory: CacheStoreFactory = {
      val factory =
        state.value.get(Keys.cacheStoreFactoryFactory).getOrElse(InMemoryCacheStore.factory(0))
      factory(cacheDirectory.toPath, Converter)
    }

    val isRoot = executionRoots.value contains resolvedScoped.value
    val shouldForce = isRoot || {
      forceUpdatePeriod.value match {
        case None => false
        case Some(period) =>
          val fullUpdateOutput = cacheDirectory / "out"
          val now = System.currentTimeMillis
          val diff = now - IO.getModifiedTimeOrZero(fullUpdateOutput)
          val elapsedDuration = new FiniteDuration(diff, TimeUnit.MILLISECONDS)
          fullUpdateOutput.exists() && elapsedDuration > period
      }
    }

    val providedScalaJars: String => Seq[File] = {
      val scalaProvider = appConfiguration.value.provider.scalaProvider
      Defaults.unmanagedScalaInstanceOnly.value match {
        case Some(instance) =>
          unmanagedJarsTask(scalaVersion.value, instance.version, instance.allJars)
        case None =>
          (subVersion: String) =>
            if (scalaProvider.version == subVersion) scalaProvider.jars else Nil
      }
    }

    val state0 = state.value
    val updateConf = {
      // Log captures log messages at all levels, except ivy logs.
      // Use full level when debug is enabled so that ivy logs are shown.
      import UpdateLogging.{ Default, DownloadOnly, Full }
      val conf = updateConfiguration.value
      val maybeUpdateLevel = (logLevel in update).?.value
      val conf1 = maybeUpdateLevel.orElse(state0.get(logLevel.key)) match {
        case Some(Level.Debug) if conf.logging == Default => conf.withLogging(logging = Full)
        case Some(_) if conf.logging == Default           => conf.withLogging(logging = DownloadOnly)
        case _                                            => conf
      }

      // logical clock is folded into UpdateConfiguration
      conf1
        .withLogicalClock(LogicalClock(state0.hashCode))
        .withMetadataDirectory(dependencyCacheDirectory.value)
    }

    val evictionOptions = Def.taskDyn {
      if (executionRoots.value.exists(_.key == evicted.key))
        Def.task(EvictionWarningOptions.empty)
      else Def.task((evictionWarningOptions in update).value)
    }.value

    val extracted = (Project extract state0)
    val isPlugin = sbtPlugin.value
    val thisRef = thisProjectRef.value
    val label =
      if (isPlugin) Reference.display(thisRef)
      else Def.displayRelativeReference(extracted.currentRef, thisRef)

    LibraryManagement.cachedUpdate(
      // LM API
      lm = dependencyResolution.value,
      // Ivy-free ModuleDescriptor
      module = ivyModule.value,
      cacheStoreFactory = cacheStoreFactory,
      label = label,
      updateConf,
      substituteScalaFiles(scalaOrganization.value, _)(providedScalaJars),
      skip = (skip in update).value,
      force = shouldForce,
      depsUpdated = transitiveUpdate.value.exists(!_.stats.cached),
      uwConfig = (unresolvedWarningConfiguration in update).value,
      ewo = evictionOptions,
      mavenStyle = publishMavenStyle.value,
      compatWarning = compatibilityWarningOptions.value,
      includeCallers = includeCallers,
      includeDetails = includeDetails,
      log = s.log
    )
  }

  private[sbt] def dependencyPositionsTask: Initialize[Task[Map[ModuleID, SourcePosition]]] =
    Def.task {
      val projRef = thisProjectRef.value
      val st = state.value
      val s = streams.value
      val cacheStoreFactory = s.cacheStoreFactory sub updateCacheName.value
      import sbt.librarymanagement.LibraryManagementCodec._
      def modulePositions: Map[ModuleID, SourcePosition] =
        try {
          val extracted = (Project extract st)
          val sk = (libraryDependencies in (GlobalScope in projRef)).scopedKey
          val empty = extracted.structure.data set (sk.scope, sk.key, Nil)
          val settings = extracted.structure.settings filter { s: Setting[_] =>
            (s.key.key == libraryDependencies.key) &&
            (s.key.scope.project == Select(projRef))
          }
          Map(settings flatMap {
            case s: Setting[Seq[ModuleID]] @unchecked =>
              s.init.evaluate(empty) map { _ -> s.pos }
          }: _*)
        } catch {
          case NonFatal(_) => Map()
        }

      val outCacheStore = cacheStoreFactory make "output_dsp"
      val f = Tracked.inputChanged(cacheStoreFactory make "input_dsp") {
        (inChanged: Boolean, in: Seq[ModuleID]) =>
          implicit val NoPositionFormat: JsonFormat[NoPosition.type] = asSingleton(NoPosition)
          implicit val LinePositionFormat: IsoLList.Aux[LinePosition, String :*: Int :*: LNil] =
            LList.iso(
              { l: LinePosition =>
                ("path", l.path) :*: ("startLine", l.startLine) :*: LNil
              }, { in: String :*: Int :*: LNil =>
                LinePosition(in.head, in.tail.head)
              }
            )
          implicit val LineRangeFormat: IsoLList.Aux[LineRange, Int :*: Int :*: LNil] = LList.iso(
            { l: LineRange =>
              ("start", l.start) :*: ("end", l.end) :*: LNil
            }, { in: Int :*: Int :*: LNil =>
              LineRange(in.head, in.tail.head)
            }
          )
          implicit val RangePositionFormat
              : IsoLList.Aux[RangePosition, String :*: LineRange :*: LNil] = LList.iso(
            { r: RangePosition =>
              ("path", r.path) :*: ("range", r.range) :*: LNil
            }, { in: String :*: LineRange :*: LNil =>
              RangePosition(in.head, in.tail.head)
            }
          )
          implicit val SourcePositionFormat: JsonFormat[SourcePosition] =
            unionFormat3[SourcePosition, NoPosition.type, LinePosition, RangePosition]

          implicit val midJsonKeyFmt: sjsonnew.JsonKeyFormat[ModuleID] = moduleIdJsonKeyFormat
          val outCache =
            Tracked.lastOutput[Seq[ModuleID], Map[ModuleID, SourcePosition]](outCacheStore) {
              case (_, Some(out)) if !inChanged => out
              case _                            => modulePositions
            }
          outCache(in)
      }
      f(libraryDependencies.value)
    }

  /*
    // can't cache deliver/publish easily since files involved are hidden behind patterns.  publish will be difficult to verify target-side anyway
    def cachedPublish(cacheFile: File)(g: (IvySbt#Module, PublishConfiguration) => Unit, module: IvySbt#Module, config: PublishConfiguration) => Unit =
    { case module :+: config :+: HNil =>
    /*	implicit val publishCache = publishIC
      val f = cached(cacheFile) { (conf: IvyConfiguration, settings: ModuleSettings, config: PublishConfiguration) =>*/
          g(module, config)
      /*}
      f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)*/
    }*/

  def defaultRepositoryFilter: MavenRepository => Boolean = repo => !repo.root.startsWith("file:")

  def getPublishTo(repo: Option[Resolver]): Resolver =
    repo getOrElse sys.error("Repository for publishing is not specified.")

  def publishConfig(
      publishMavenStyle: Boolean,
      deliverIvyPattern: String,
      status: String,
      configurations: Vector[ConfigRef],
      artifacts: Vector[(Artifact, File)],
      checksums: Vector[String],
      resolverName: String = "local",
      logging: UpdateLogging = UpdateLogging.DownloadOnly,
      overwrite: Boolean = false
  ) =
    PublishConfiguration(
      publishMavenStyle,
      deliverIvyPattern,
      status,
      configurations,
      resolverName,
      artifacts,
      checksums,
      logging,
      overwrite
    )

  def makeIvyXmlConfig(
      publishMavenStyle: Boolean,
      deliverIvyPattern: String,
      status: String,
      configurations: Vector[ConfigRef],
      checksums: Vector[String],
      logging: sbt.librarymanagement.UpdateLogging = UpdateLogging.DownloadOnly,
      overwrite: Boolean = false,
      optResolverName: Option[String] = None
  ) =
    PublishConfiguration(
      publishMavenStyle,
      Some(deliverIvyPattern),
      Some(status),
      Some(configurations),
      optResolverName,
      Vector.empty,
      checksums,
      Some(logging),
      overwrite
    )

  def deliverPattern(outputPath: File): String =
    (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath

  private[sbt] def isScala2Scala3Sandwich(sbv1: String, sbv2: String): Boolean = {
    def compare(a: String, b: String): Boolean =
      a == "2.13" && (b.startsWith("0.") || b.startsWith("3.0"))
    compare(sbv1, sbv2) || compare(sbv2, sbv1)
  }

  def projectDependenciesTask: Initialize[Task[Seq[ModuleID]]] =
    Def.task {
      val sbv = scalaBinaryVersion.value
      val ref = thisProjectRef.value
      val data = settingsData.value
      val deps = buildDependencies.value
      deps.classpath(ref) flatMap { dep =>
        val depProjIdOpt = (dep.project / projectID).get(data)
        val depSVOpt = (dep.project / scalaVersion).get(data)
        val depSBVOpt = (dep.project / scalaBinaryVersion).get(data)
        val depCrossOpt = (dep.project / crossVersion).get(data)
        (depProjIdOpt, depSVOpt, depSBVOpt, depCrossOpt) match {
          case (Some(depProjId), Some(depSV), Some(depSBV), Some(depCross)) =>
            if (sbv == depSBV || depCross != CrossVersion.binary)
              Some(
                depProjId.withConfigurations(dep.configuration).withExplicitArtifacts(Vector.empty)
              )
            else if (isScala2Scala3Sandwich(sbv, depSBV) && depCross == CrossVersion.binary)
              Some(
                depProjId
                  .withCrossVersion(CrossVersion.constant(depSBV))
                  .withConfigurations(dep.configuration)
                  .withExplicitArtifacts(Vector.empty)
              )
            else sys.error(s"scalaBinaryVersion mismatch: expected $sbv but found ${depSBV}")
          case _ => None
        }
      }
    }

  private[sbt] def depMap: Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
    Def.taskDyn {
      depMap(
        buildDependencies.value classpathTransitiveRefs thisProjectRef.value,
        settingsData.value,
        streams.value.log
      )
    }

  private[sbt] def depMap(
      projects: Seq[ProjectRef],
      data: Settings[Scope],
      log: Logger
  ): Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
    Def.value {
      projects.flatMap(ivyModule in _ get data).join.map { mod =>
        mod map { _.dependencyMapping(log) } toMap;
      }
    }

  def projectResolverTask: Initialize[Task[Resolver]] =
    projectDescriptors map { m =>
      val resolver = new ProjectResolver(ProjectResolver.InterProject, m)
      new RawRepository(resolver, resolver.getName)
    }

  def analyzed[T](data: T, analysis: CompileAnalysis) = ClasspathImpl.analyzed[T](data, analysis)
  def makeProducts: Initialize[Task[Seq[File]]] = Def.task {
    val c = fileConverter.value
    Def.unit(copyResources.value)
    Def.unit(compile.value)

    c.toPath(backendOutput.value).toFile :: Nil
  }

  private[sbt] def makePickleProducts: Initialize[Task[Seq[VirtualFile]]] = Def.task {
    // This is a conditional task.
    if (earlyOutputPing.await.value) {
      // TODO: copyResources.value
      earlyOutput.value :: Nil
    } else {
      val c = fileConverter.value
      products.value map { x: File =>
        c.toVirtualFile(x.toPath)
      }
    }
  }

  def constructBuildDependencies: Initialize[BuildDependencies] =
    loadedBuild(lb => BuildUtil.dependencies(lb.units))

  @deprecated("not used", "1.4.0")
  def internalDependencies: Initialize[Task[Classpath]] =
    ClasspathImpl.internalDependencyClasspathTask

  def internalDependencyJarsTask: Initialize[Task[Classpath]] =
    ClasspathImpl.internalDependencyJarsTask
  def unmanagedDependencies: Initialize[Task[Classpath]] = ClasspathImpl.unmanagedDependenciesTask
  def mkIvyConfiguration: Initialize[Task[InlineIvyConfiguration]] =
    Def.task {
      val (rs, other) = (fullResolvers.value.toVector, otherResolvers.value.toVector)
      val s = streams.value
      warnResolversConflict(rs ++: other, s.log)
      errorInsecureProtocol(rs ++: other, s.log)
      InlineIvyConfiguration()
        .withPaths(ivyPaths.value)
        .withResolvers(rs)
        .withOtherResolvers(other)
        .withModuleConfigurations(moduleConfigurations.value.toVector)
        .withLock(lock(appConfiguration.value))
        .withChecksums((checksums in update).value.toVector)
        .withResolutionCacheDir(crossTarget.value / "resolution-cache")
        .withUpdateOptions(updateOptions.value)
        .withLog(s.log)
    }

  def interSort(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Settings[Scope],
      deps: BuildDependencies
  ): Seq[(ProjectRef, String)] = ClasspathImpl.interSort(projectRef, conf, data, deps)

  def interSortConfigurations(
      projectRef: ProjectRef,
      conf: Configuration,
      data: Settings[Scope],
      deps: BuildDependencies
  ): Seq[(ProjectRef, ConfigRef)] =
    interSort(projectRef, conf, data, deps).map {
      case (projectRef, configName) => (projectRef, ConfigRef(configName))
    }

  def mapped(
      confString: Option[String],
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String,
      defaultMapping: String
  ): String => Seq[String] =
    ClasspathImpl.mapped(confString, masterConfs, depConfs, default, defaultMapping)

  def parseMapping(
      confString: String,
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String => Seq[String]
  ): String => Seq[String] =
    ClasspathImpl.parseMapping(confString, masterConfs, depConfs, default)

  def parseSingleMapping(
      masterConfs: Seq[String],
      depConfs: Seq[String],
      default: String => Seq[String]
  )(confString: String): String => Seq[String] =
    ClasspathImpl.parseSingleMapping(masterConfs, depConfs, default)(confString)

  def union[A, B](maps: Seq[A => Seq[B]]): A => Seq[B] =
    ClasspathImpl.union[A, B](maps)

  def parseList(s: String, allConfs: Seq[String]): Seq[String] =
    ClasspathImpl.parseList(s, allConfs)

  def replaceWildcard(allConfs: Seq[String])(conf: String): Seq[String] =
    ClasspathImpl.replaceWildcard(allConfs)(conf)

  def missingConfiguration(in: String, conf: String) =
    sys.error("Configuration '" + conf + "' not defined in '" + in + "'")
  def allConfigs(conf: Configuration): Seq[Configuration] = ClasspathImpl.allConfigs(conf)

  def getConfigurations(p: ResolvedReference, data: Settings[Scope]): Seq[Configuration] =
    ClasspathImpl.getConfigurations(p, data)
  def confOpt(configurations: Seq[Configuration], conf: String): Option[Configuration] =
    ClasspathImpl.confOpt(configurations, conf)

  def unmanagedLibs(dep: ResolvedReference, conf: String, data: Settings[Scope]): Task[Classpath] =
    ClasspathImpl.unmanagedLibs(dep, conf, data)

  def getClasspath(
      key: TaskKey[Classpath],
      dep: ResolvedReference,
      conf: String,
      data: Settings[Scope]
  ): Task[Classpath] =
    ClasspathImpl.getClasspath(key, dep, conf, data)

  def defaultConfigurationTask(p: ResolvedReference, data: Settings[Scope]): Configuration =
    flatten(defaultConfiguration in p get data) getOrElse Configurations.Default

  def flatten[T](o: Option[Option[T]]): Option[T] = o flatMap idFun

  val sbtIvySnapshots: URLRepository = Resolver.sbtIvyRepo("snapshots")
  val typesafeReleases: URLRepository =
    Resolver.typesafeIvyRepo("releases").withName("typesafe-alt-ivy-releases")
  val sbtPluginReleases: URLRepository = Resolver.sbtPluginRepo("releases")
  val sbtMavenSnapshots: MavenRepository =
    MavenRepository("sbt-maven-snapshot", Resolver.SbtRepositoryRoot + "/" + "maven-snapshots/")

  def modifyForPlugin(plugin: Boolean, dep: ModuleID): ModuleID =
    if (plugin) dep.withConfigurations(Some(Provided.name)) else dep

  def autoLibraryDependency(
      auto: Boolean,
      plugin: Boolean,
      org: String,
      version: String
  ): Seq[ModuleID] =
    if (auto)
      modifyForPlugin(plugin, ModuleID(org, ScalaArtifacts.LibraryID, version)) :: Nil
    else
      Nil

  def addUnmanagedLibrary: Seq[Setting[_]] =
    Seq(unmanagedJars in Compile ++= unmanagedScalaLibrary.value)

  def unmanagedScalaLibrary: Initialize[Task[Seq[File]]] = Def.taskDyn {
    if (autoScalaLibrary.value && scalaHome.value.isDefined)
      Def.task { scalaInstance.value.libraryJars } else
      Def.task { Nil }
  }

  import DependencyFilter._
  def managedJars(config: Configuration, jarTypes: Set[String], up: UpdateReport): Classpath =
    up.filter(configurationFilter(config.name) && artifactFilter(`type` = jarTypes))
      .toSeq
      .map {
        case (_, module, art, file) =>
          Attributed(file)(
            AttributeMap.empty
              .put(artifact.key, art)
              .put(moduleID.key, module)
              .put(configuration.key, config)
          )
      }
      .distinct

  def findUnmanagedJars(
      config: Configuration,
      base: File,
      filter: FileFilter,
      excl: FileFilter
  ): Classpath = {
    (base * (filter -- excl) +++ (base / config.name).descendantsExcept(filter, excl)).classpath
  }
  @deprecated(
    "The method only works for Scala 2, use the overloaded version to support both Scala 2 and Scala 3",
    "1.1.5"
  )
  def autoPlugins(report: UpdateReport, internalPluginClasspath: Seq[File]): Seq[String] =
    autoPlugins(report, internalPluginClasspath, isDotty = false)

  def autoPlugins(
      report: UpdateReport,
      internalPluginClasspath: Seq[File],
      isDotty: Boolean
  ): Seq[String] = {
    import sbt.internal.inc.classpath.ClasspathUtil.compilerPlugins
    val pluginClasspath = report.matching(configurationFilter(CompilerPlugin.name)) ++ internalPluginClasspath
    val plugins = compilerPlugins(pluginClasspath.map(_.toPath), isDotty)
    plugins.map("-Xplugin:" + _.toAbsolutePath.toString).toSeq
  }

  private[this] lazy val internalCompilerPluginClasspath: Initialize[Task[Classpath]] =
    Def.taskDyn {
      val ref = thisProjectRef.value
      val data = settingsData.value
      val deps = buildDependencies.value
      ClasspathImpl.internalDependenciesImplTask(
        ref,
        CompilerPlugin,
        CompilerPlugin,
        data,
        deps,
        TrackLevel.TrackAlways,
        streams.value.log
      )
    }

  lazy val compilerPluginConfig = Seq(
    scalacOptions := {
      val options = scalacOptions.value
      val newPlugins = autoPlugins(
        update.value,
        internalCompilerPluginClasspath.value.files,
        ScalaInstance.isDotty(scalaVersion.value)
      )
      val existing = options.toSet
      if (autoCompilerPlugins.value) options ++ newPlugins.filterNot(existing) else options
    }
  )

  def substituteScalaFiles(scalaOrg: String, report: UpdateReport)(
      scalaJars: String => Seq[File]
  ): UpdateReport =
    report.substitute { (configuration, module, arts) =>
      if (module.organization == scalaOrg) {
        val jarName = module.name + ".jar"
        val replaceWith = scalaJars(module.revision).toVector
          .filter(_.getName == jarName)
          .map(f => (Artifact(f.getName.stripSuffix(".jar")), f))
        if (replaceWith.isEmpty) arts else replaceWith
      } else
        arts
    }

  // try/catch for supporting earlier launchers
  def bootIvyHome(app: xsbti.AppConfiguration): Option[File] =
    try {
      Option(app.provider.scalaProvider.launcher.ivyHome)
    } catch {
      case _: NoSuchMethodError => None
    }

  def bootChecksums(app: xsbti.AppConfiguration): Vector[String] =
    try {
      app.provider.scalaProvider.launcher.checksums.toVector
    } catch {
      case _: NoSuchMethodError => IvySbt.DefaultChecksums
    }

  def isOverrideRepositories(app: xsbti.AppConfiguration): Boolean =
    try app.provider.scalaProvider.launcher.isOverrideRepositories
    catch { case _: NoSuchMethodError => false }

  /** Loads the `appRepositories` configured for this launcher, if supported. */
  def appRepositories(app: xsbti.AppConfiguration): Option[Vector[Resolver]] =
    try {
      Some(app.provider.scalaProvider.launcher.appRepositories.toVector map bootRepository)
    } catch {
      case _: NoSuchMethodError => None
    }

  def bootRepositories(app: xsbti.AppConfiguration): Option[Vector[Resolver]] =
    try {
      Some(app.provider.scalaProvider.launcher.ivyRepositories.toVector map bootRepository)
    } catch {
      case _: NoSuchMethodError => None
    }

  // This is a place holder in case someone doesn't want to use Coursier
  private[sbt] def dummyCoursierDirectory(app: xsbti.AppConfiguration): File = {
    val base = app.baseDirectory.getCanonicalFile
    base / "target" / "coursier-temp"
  }

  private[this] def mavenCompatible(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.mavenCompatible
    } catch { case _: NoSuchMethodError => false }

  private[this] def skipConsistencyCheck(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.skipConsistencyCheck
    } catch { case _: NoSuchMethodError => false }

  private[this] def descriptorOptional(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.descriptorOptional
    } catch { case _: NoSuchMethodError => false }

  // for forward-compatibility with launcher.jar prior to 1.3.11
  private[this] def mavenRepoAllowInsecureProtocol(mavenRepo: xsbti.MavenRepository): Boolean =
    try {
      mavenRepo.allowInsecureProtocol
    } catch { case _: NoSuchMethodError => false }

  // for forward-compatibility with launcher.jar prior to 1.3.11
  private[this] def allowInsecureProtocol(ivyRepo: xsbti.IvyRepository): Boolean =
    try {
      ivyRepo.allowInsecureProtocol
    } catch { case _: NoSuchMethodError => false }

  @com.github.ghik.silencer.silent
  private[this] def bootRepository(repo: xsbti.Repository): Resolver = {
    import xsbti.Predefined
    repo match {
      case m: xsbti.MavenRepository =>
        MavenRepository(m.id, m.url.toString)
          .withAllowInsecureProtocol(mavenRepoAllowInsecureProtocol(m))
      case i: xsbti.IvyRepository =>
        val patterns = Patterns(
          Vector(i.ivyPattern),
          Vector(i.artifactPattern),
          mavenCompatible(i),
          descriptorOptional(i),
          skipConsistencyCheck(i)
        )
        i.url.getProtocol match {
          case "file" =>
            // This hackery is to deal suitably with UNC paths on Windows. Once we can assume Java7, Paths should save us from this.
            val file = IO.toFile(i.url)
            Resolver.file(i.id, file)(patterns)
          case _ =>
            Resolver
              .url(i.id, i.url)(patterns)
              .withAllowInsecureProtocol(allowInsecureProtocol(i))
        }
      case p: xsbti.PredefinedRepository =>
        p.id match {
          case Predefined.Local                => Resolver.defaultLocal
          case Predefined.MavenLocal           => Resolver.mavenLocal
          case Predefined.MavenCentral         => Resolver.DefaultMavenRepository
          case Predefined.ScalaToolsReleases   => Resolver.ScalaToolsReleases
          case Predefined.ScalaToolsSnapshots  => Resolver.ScalaToolsSnapshots
          case Predefined.SonatypeOSSReleases  => Resolver.sonatypeRepo("releases")
          case Predefined.SonatypeOSSSnapshots => Resolver.sonatypeRepo("snapshots")
          case unknown =>
            sys.error(
              "Unknown predefined resolver '" + unknown + "'.  This resolver may only be supported in newer sbt versions."
            )
        }
    }
  }

  def shellPromptFromState: State => String = shellPromptFromState(Terminal.console)
  def shellPromptFromState(terminal: Terminal): State => String = { s: State =>
    val extracted = Project.extract(s)
    (name in extracted.currentRef).get(extracted.structure.data) match {
      case Some(name) =>
        s"sbt:$name" + Def.withColor(s"> ", Option(scala.Console.CYAN), terminal.isColorEnabled)
      case _ => "> "
    }
  }
}
